package org.GLM.negoriator.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.GLM.negoriator.application.NegotiationApplicationService;
import org.GLM.negoriator.application.NegotiationDefaults;
import org.GLM.negoriator.application.StrategyMetadata;
import org.GLM.negoriator.domain.NegotiationDecision;
import org.GLM.negoriator.domain.NegotiationDecisionType;
import org.GLM.negoriator.domain.NegotiationOffer;
import org.GLM.negoriator.domain.NegotiationParty;
import org.GLM.negoriator.domain.NegotiationSession;
import org.GLM.negoriator.domain.NegotiationSessionRepository;
import org.GLM.negoriator.domain.NegotiationSessionStatus;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationStrategy;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:negotiator-price-sensitivity-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class PriceSensitivityTest {

    private static final int SAMPLE_COUNT = 1_000;
    private static final List<NegotiationStrategy> STRATEGIES = List.of(
        NegotiationStrategy.BASELINE,
        NegotiationStrategy.MESO,
        NegotiationStrategy.BOULWARE,
        NegotiationStrategy.CONCEDER,
        NegotiationStrategy.TIT_FOR_TAT);
    private static final List<SupplierTurnStep> SUPPLIER_SCRIPT = List.of(
        scriptedOffer(new OfferVector(new BigDecimal("120.00"), 60, 30, 24)),
        scriptedOffer(new OfferVector(new BigDecimal("120.00"), 60, 21, 24)),
        scriptedOffer(new OfferVector(new BigDecimal("120.00"), 60, 14, 24)),
        scriptedOffer(new OfferVector(new BigDecimal("120.00"), 60, 14, 12)),
        scriptedOffer(new OfferVector(new BigDecimal("120.00"), 60, 7, 12)),
        scriptedOffer(new OfferVector(new BigDecimal("120.00"), 60, 7, 12)),
        new SupplierTurnStep(new OfferVector(new BigDecimal("120.00"), 60, 7, 12), "accept"));

    @Autowired
    private NegotiationApplicationService service;

    @Autowired
    private NegotiationSessionRepository sessionRepository;

    @AfterEach
    void cleanUp() {
        sessionRepository.deleteAll();
    }

    @Test
    void comparesAcceptedOfferAveragesAcrossStrategiesForTheSameSupplierFlow() {
        Map<NegotiationStrategy, StrategySummary> summaries = new EnumMap<>(NegotiationStrategy.class);

        for (NegotiationStrategy strategy : STRATEGIES) {
            summaries.put(strategy, runSamples(strategy));
        }

        System.out.println(buildReport(summaries));

        assertTrue(summaries.values().stream().allMatch(summary -> summary.closedDeals() == SAMPLE_COUNT));
        assertTrue(summaries.get(NegotiationStrategy.BOULWARE).averagePrice()
            .compareTo(summaries.get(NegotiationStrategy.BASELINE).averagePrice()) < 0);
        assertTrue(summaries.get(NegotiationStrategy.BASELINE).averagePrice()
            .compareTo(summaries.get(NegotiationStrategy.MESO).averagePrice()) < 0);
        assertTrue(summaries.get(NegotiationStrategy.MESO).averagePrice()
            .compareTo(summaries.get(NegotiationStrategy.CONCEDER).averagePrice()) < 0);
        assertTrue(summaries.get(NegotiationStrategy.TIT_FOR_TAT).averagePrice()
            .compareTo(summaries.get(NegotiationStrategy.BASELINE).averagePrice()) >= 0);
        assertTrue(summaries.get(NegotiationStrategy.TIT_FOR_TAT).averagePrice()
            .compareTo(summaries.get(NegotiationStrategy.CONCEDER).averagePrice()) < 0);
    }

    private StrategySummary runSamples(NegotiationStrategy strategy) {
        int closedDeals = 0;
        int totalAcceptedRound = 0;
        int totalFinalTermsRound = 0;
        BigDecimal totalPrice = BigDecimal.ZERO;
        long totalPaymentDays = 0;
        long totalDeliveryDays = 0;
        long totalContractMonths = 0;
        Map<String, Integer> finalDeals = new LinkedHashMap<>();

        for (int sample = 0; sample < SAMPLE_COUNT; sample++) {
            ScenarioOutcome outcome = simulateStrategy(strategy);
            assertTrue(outcome.closed(), "Expected supplier script to close for " + strategy);

            closedDeals++;
            totalAcceptedRound += outcome.acceptedRound();
            totalFinalTermsRound += outcome.finalTermsFirstOfferedRound();
            totalPrice = totalPrice.add(outcome.finalDeal().price());
            totalPaymentDays += outcome.finalDeal().paymentDays();
            totalDeliveryDays += outcome.finalDeal().deliveryDays();
            totalContractMonths += outcome.finalDeal().contractMonths();
            finalDeals.merge(formatOffer(outcome.finalDeal()), 1, Integer::sum);
        }

        return new StrategySummary(
            SAMPLE_COUNT,
            closedDeals,
            averageMoney(totalPrice, closedDeals),
            averageWholeNumber(totalPaymentDays, closedDeals),
            averageWholeNumber(totalDeliveryDays, closedDeals),
            averageWholeNumber(totalContractMonths, closedDeals),
            averageWholeNumber(totalAcceptedRound, closedDeals),
            averageWholeNumber(totalFinalTermsRound, closedDeals),
            mostCommonDeal(finalDeals));
    }

    private ScenarioOutcome simulateStrategy(NegotiationStrategy strategy) {
        NegotiationSession session = service.startSession(NegotiationDefaults.startSessionCommand(strategy));

        for (SupplierTurnStep supplierTurn : SUPPLIER_SCRIPT) {
            session = submit(session, supplierTurn.offer(), supplierTurn.message());
            if (session.getStatus() == NegotiationSessionStatus.ACCEPTED) {
                NegotiationDecision decision = latestDecision(session);
                OfferVector finalDeal = acceptedTerms(decision);
                return new ScenarioOutcome(
                    true,
                    finalDeal,
                    decision.getRoundNumber(),
                    firstBuyerRoundOfferingTerms(session, finalDeal));
            }
        }

        return new ScenarioOutcome(false, null, -1, -1);
    }

    private OfferVector acceptedTerms(NegotiationDecision decision) {
        NegotiationOffer acceptedBuyerOffer = decision.getCounterOffer();
        if (acceptedBuyerOffer != null) {
            return acceptedBuyerOffer.toOfferVector();
        }

        return decision.getSupplierOffer().toOfferVector();
    }

    private int firstBuyerRoundOfferingTerms(NegotiationSession session, OfferVector finalDeal) {
        return session.getOffers().stream()
            .filter(offer -> offer.getParty() == NegotiationParty.BUYER)
            .filter(offer -> offer.toOfferVector().matches(finalDeal))
            .map(NegotiationOffer::getRoundNumber)
            .findFirst()
            .orElse(-1);
    }

    private NegotiationSession submit(NegotiationSession session, OfferVector offer, String message) {
        NegotiationSession updatedSession = service.submitSupplierOffer(session.getId(), offer, null, message);
        return service.getSession(updatedSession.getId());
    }

    private NegotiationDecision latestDecision(NegotiationSession session) {
        return session.getDecisions().stream()
            .reduce((first, second) -> second)
            .orElseThrow();
    }

    private String buildReport(Map<NegotiationStrategy, StrategySummary> summaries) {
        StringBuilder report = new StringBuilder();
        report.append("Accepted offer averages over ")
            .append(SAMPLE_COUNT)
            .append(" runs with the same fixed supplier flow")
            .append(System.lineSeparator());
        report.append(String.format(
            Locale.ROOT,
            "%-12s %-11s %-12s %-8s %-8s %-8s %-12s %-17s %s%n",
            "Strategy",
            "Closed",
            "Avg Price",
            "Avg Pay",
            "Avg Del",
            "Avg Ctr",
            "Avg Accept R",
            "Avg Final-Term R",
            "Accepted Deal"));

        for (NegotiationStrategy strategy : STRATEGIES) {
            StrategySummary summary = summaries.get(strategy);
            report.append(String.format(
                Locale.ROOT,
                "%-12s %-11s %-12s %-8s %-8s %-8s %-12s %-17s %s%n",
                StrategyMetadata.describe(strategy).label(),
                summary.closedDeals() + "/" + summary.sampleCount(),
                summary.averagePrice().toPlainString(),
                summary.averagePaymentDays().toPlainString(),
                summary.averageDeliveryDays().toPlainString(),
                summary.averageContractMonths().toPlainString(),
                summary.averageAcceptedRound().toPlainString(),
                summary.averageFinalTermsRound().toPlainString(),
                summary.mostCommonFinalDeal()));
        }

        return report.toString();
    }

    private static SupplierTurnStep scriptedOffer(OfferVector offer) {
        return new SupplierTurnStep(
            offer,
            "Price " + offer.price().setScale(2, RoundingMode.HALF_UP).toPlainString()
                + ", payment " + offer.paymentDays()
                + ", delivery " + offer.deliveryDays()
                + ", contract " + offer.contractMonths());
    }

    private BigDecimal averageMoney(BigDecimal total, int count) {
        return total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal averageWholeNumber(long total, int count) {
        return BigDecimal.valueOf(total)
            .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private String mostCommonDeal(Map<String, Integer> finalDeals) {
        return finalDeals.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("No deal");
    }

    private String formatOffer(OfferVector offer) {
        return "Price " + offer.price().setScale(2, RoundingMode.HALF_UP).toPlainString()
            + ", payment " + offer.paymentDays()
            + ", delivery " + offer.deliveryDays()
            + ", contract " + offer.contractMonths();
    }

    private record StrategySummary(
        int sampleCount,
        int closedDeals,
        BigDecimal averagePrice,
        BigDecimal averagePaymentDays,
        BigDecimal averageDeliveryDays,
        BigDecimal averageContractMonths,
        BigDecimal averageAcceptedRound,
        BigDecimal averageFinalTermsRound,
        String mostCommonFinalDeal
    ) {
    }

    private record ScenarioOutcome(
        boolean closed,
        OfferVector finalDeal,
        int acceptedRound,
        int finalTermsFirstOfferedRound
    ) {
    }

    private record SupplierTurnStep(
        OfferVector offer,
        String message
    ) {
    }

    @TestConfiguration
    static class StubAiConfiguration {

        @Bean
        @Primary
        AiNegotiationMessageService aiNegotiationMessageService() {
            AiGatewayService gateway = new AiGatewayService(
                RestClient.builder(),
                new ObjectMapper(),
                "ollama",
                "http://localhost:11434",
                "test-model",
                "");

            return new AiNegotiationMessageService(gateway) {
                @Override
                public String composeOpeningMessage(NegotiationSession session) {
                    return "Please send your opening offer with price, payment days, delivery days, and contract length.";
                }

                @Override
                public String composeBuyerReply(BuyerReplyMessageRequest request) {
                    return "buyer reply";
                }
            };
        }
    }
}
