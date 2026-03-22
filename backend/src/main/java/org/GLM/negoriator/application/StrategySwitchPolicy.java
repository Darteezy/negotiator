package org.GLM.negoriator.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

import org.GLM.negoriator.domain.NegotiationOffer;
import org.GLM.negoriator.domain.NegotiationParty;
import org.GLM.negoriator.domain.NegotiationStrategyChangeTrigger;
import org.GLM.negoriator.domain.NegotiationSession;
import org.GLM.negoriator.negotiation.NegotiationEngine.Decision;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationIssue;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationResponse;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationState;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationStrategy;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;

class StrategySwitchPolicy {

	StrategyContext describeCurrentStrategy(NegotiationSession session) {
		return new StrategyContext(session.getStrategy(), rationaleFor(session.getStrategy()));
	}

	StrategyCheckpoint evaluate(
		NegotiationSession session,
		NegotiationResponse response,
		OfferVector supplierOffer
	) {
		if (response.decision() != Decision.COUNTER || response.nextState() != NegotiationState.COUNTERED) {
			return StrategyCheckpoint.none();
		}

		NegotiationStrategy currentStrategy = session.getStrategy();
		int roundsRemainingAfterCounter = Math.max(0, session.getMaxRounds() - session.getCurrentRound());

		if (roundsRemainingAfterCounter <= 1 && currentStrategy != NegotiationStrategy.CONCEDER) {
			return StrategyCheckpoint.switchTo(
				NegotiationStrategy.CONCEDER,
				NegotiationStrategyChangeTrigger.DEADLINE_PRESSURE,
				"Switched to Conceder because the session is close to its round limit and the buyer should prioritize closing probability over exploration.");
		}

		if (currentStrategy == NegotiationStrategy.MESO && supplierStalled(session, supplierOffer)) {
			return StrategyCheckpoint.switchTo(
				NegotiationStrategy.TIT_FOR_TAT,
				NegotiationStrategyChangeTrigger.STALLED_NEGOTIATION,
				"Switched from MESO to Tit-for-Tat because the supplier repeated nearly the same position and the buyer should respond more directly to observed movement.");
		}

		if (currentStrategy == NegotiationStrategy.CONCEDER && supplierMadeMaterialProgress(session, supplierOffer)) {
			return StrategyCheckpoint.switchTo(
				NegotiationStrategy.TIT_FOR_TAT,
				NegotiationStrategyChangeTrigger.RECIPROCAL_PROGRESS,
				"Switched to Tit-for-Tat because the supplier made a material concession and the buyer can now reward reciprocal movement more precisely.");
		}

		return StrategyCheckpoint.none();
	}

	String humanIssueExplanation(NegotiationIssue issue) {
		if (issue == null) {
			return "The buyer held all terms steady because no single issue dominated the remaining gap.";
		}

		return switch (issue) {
			case PRICE -> "The buyer moved price because it remains the strongest unresolved source of buyer utility loss.";
			case PAYMENT_DAYS -> "The buyer moved payment days because cash timing is the largest remaining negotiation gap.";
			case DELIVERY_DAYS -> "The buyer moved delivery because lead time is still the most important unresolved operational gap.";
			case CONTRACT_MONTHS -> "The buyer moved contract length because commitment length remains the clearest trade-off lever.";
		};
	}

	private boolean supplierStalled(NegotiationSession session, OfferVector supplierOffer) {
		List<OfferVector> supplierHistory = supplierHistory(session);
		if (supplierHistory.size() < 2) {
			return false;
		}

		OfferVector previousOffer = supplierHistory.get(supplierHistory.size() - 2);
		return distance(previousOffer, supplierOffer).compareTo(new BigDecimal("0.05")) <= 0;
	}

	private boolean supplierMadeMaterialProgress(NegotiationSession session, OfferVector supplierOffer) {
		List<OfferVector> supplierHistory = supplierHistory(session);
		if (supplierHistory.size() < 2) {
			return false;
		}

		OfferVector previousOffer = supplierHistory.get(supplierHistory.size() - 2);
		return distance(previousOffer, supplierOffer).compareTo(new BigDecimal("0.15")) > 0;
	}

	private List<OfferVector> supplierHistory(NegotiationSession session) {
		return session.getOffers().stream()
			.filter(offer -> offer.getParty() == NegotiationParty.SUPPLIER)
			.sorted(Comparator.comparing(NegotiationOffer::getRoundNumber)
				.thenComparing(NegotiationOffer::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
			.map(NegotiationOffer::toOfferVector)
			.toList();
	}

	private BigDecimal distance(OfferVector previousOffer, OfferVector currentOffer) {
		BigDecimal priceDistance = previousOffer.price().subtract(currentOffer.price()).abs()
			.divide(new BigDecimal("40"), 4, RoundingMode.HALF_UP);
		BigDecimal paymentDistance = BigDecimal.valueOf(Math.abs(previousOffer.paymentDays() - currentOffer.paymentDays()))
			.divide(new BigDecimal("60"), 4, RoundingMode.HALF_UP);
		BigDecimal deliveryDistance = BigDecimal.valueOf(Math.abs(previousOffer.deliveryDays() - currentOffer.deliveryDays()))
			.divide(new BigDecimal("11"), 4, RoundingMode.HALF_UP);
		BigDecimal contractDistance = BigDecimal.valueOf(Math.abs(previousOffer.contractMonths() - currentOffer.contractMonths()))
			.divide(new BigDecimal("21"), 4, RoundingMode.HALF_UP);

		return priceDistance.add(paymentDistance).add(deliveryDistance).add(contractDistance);
	}

	private String rationaleFor(NegotiationStrategy strategy) {
		return switch (strategy) {
			case MESO -> "MESO is active to explore the supplier's preferences through multiple equivalent buyer options before giving up too much value.";
			case BOULWARE -> "Boulware is active to keep the buyer strict for longer and delay concessions until stronger supplier movement appears.";
			case CONCEDER -> "Conceder is active to improve close probability by softening faster as time pressure increases.";
			case TIT_FOR_TAT -> "Tit-for-Tat is active to respond more directly to the supplier's observed concessions and repeated positions.";
			case BASELINE -> "Baseline is active as the default linear concession policy when no stronger switching signal is present.";
		};
	}

	record StrategyCheckpoint(
		NegotiationStrategy nextStrategy,
		NegotiationStrategyChangeTrigger trigger,
		String rationale,
		boolean switched
	) {
		static StrategyCheckpoint none() {
			return new StrategyCheckpoint(null, null, null, false);
		}

		static StrategyCheckpoint switchTo(
			NegotiationStrategy nextStrategy,
			NegotiationStrategyChangeTrigger trigger,
			String rationale
		) {
			return new StrategyCheckpoint(nextStrategy, trigger, rationale, true);
		}
	}

	record StrategyContext(NegotiationStrategy strategy, String rationale) {
	}
}