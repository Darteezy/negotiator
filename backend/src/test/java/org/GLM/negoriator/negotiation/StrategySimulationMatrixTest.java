package org.GLM.negoriator.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.GLM.negoriator.application.NegotiationDefaults;
import org.GLM.negoriator.application.StrategyMetadata;
import org.GLM.negoriator.negotiation.NegotiationEngine.BuyerProfile;
import org.GLM.negoriator.negotiation.NegotiationEngine.Decision;
import org.GLM.negoriator.negotiation.NegotiationEngine.IssueWeights;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationBounds;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationContext;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationRequest;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationResponse;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationState;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationStrategy;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferEvaluation;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;
import org.GLM.negoriator.negotiation.NegotiationEngine.SupplierArchetype;
import org.GLM.negoriator.negotiation.NegotiationEngine.SupplierModel;
import org.junit.jupiter.api.Test;

class StrategySimulationMatrixTest {

	private static final int SCALE = 4;
	private static final List<NegotiationStrategy> STRATEGIES = List.of(
		NegotiationStrategy.BASELINE,
		NegotiationStrategy.MESO,
		NegotiationStrategy.BOULWARE,
		NegotiationStrategy.CONCEDER,
		NegotiationStrategy.TIT_FOR_TAT);

	private final NegotiationEngineImpl negotiationEngine = new NegotiationEngineImpl();
	private final BuyerUtilityCalculator utilityCalculator = new BuyerUtilityCalculator();

	@Test
	void printsStrategyMatrixAcrossDeterministicSupplierPersonas() {
		List<SimulationScenario> scenarios = List.of(
			marginHardlinerScenario(),
			operationsFocusedScenario(),
			cashflowFocusedScenario(),
			lateCloserScenario(),
			nearSettlementScenario(),
			deadlineSettlementScenario());
		Map<String, Map<NegotiationStrategy, ScenarioOutcome>> results = new LinkedHashMap<>();

		for (SimulationScenario scenario : scenarios) {
			Map<NegotiationStrategy, ScenarioOutcome> scenarioResults = new EnumMap<>(NegotiationStrategy.class);
			for (NegotiationStrategy strategy : STRATEGIES) {
				scenarioResults.put(strategy, simulateScenario(scenario, strategy));
			}
			results.put(scenario.name(), scenarioResults);
		}

		System.out.println(buildReport(scenarios, results));

		assertEquals(scenarios.size() * STRATEGIES.size(), totalOutcomeCount(results));
		assertTrue(hasStrategySpread(results));
		assertTrue(totalAcceptedOutcomes(results) >= 8, "Expected the matrix to produce both rejection and acceptance data.");
		assertTrue(acceptedScenarioCount(results, NegotiationStrategy.BASELINE) >= 2);
		assertTrue(acceptedScenarioCount(results, NegotiationStrategy.BOULWARE) >= 2);
		assertTrue(acceptedScenarioCount(results, NegotiationStrategy.CONCEDER) >= 2);
		assertTrue(averageAcceptedUtility(results, NegotiationStrategy.BOULWARE)
			.compareTo(averageAcceptedUtility(results, NegotiationStrategy.BASELINE)) > 0);
		assertTrue(averageAcceptedUtility(results, NegotiationStrategy.BASELINE)
			.compareTo(averageAcceptedUtility(results, NegotiationStrategy.CONCEDER)) > 0);
		assertTrue(averageAcceptedRound(results, NegotiationStrategy.CONCEDER)
			.compareTo(averageAcceptedRound(results, NegotiationStrategy.BASELINE)) < 0);
		assertTrue(averageAcceptedRound(results, NegotiationStrategy.BASELINE)
			.compareTo(averageAcceptedRound(results, NegotiationStrategy.BOULWARE)) < 0);
	}

	private ScenarioOutcome simulateScenario(SimulationScenario scenario, NegotiationStrategy strategy) {
		List<OfferVector> history = new ArrayList<>();
		OfferVector supplierOffer = scenario.initialSupplierOffer();

		for (int round = 1; round <= scenario.maxRounds(); round++) {
			NegotiationRequest request = new NegotiationRequest(
				supplierOffer,
				new NegotiationContext(
					round,
					scenario.maxRounds(),
					strategy,
					round == 1 ? NegotiationState.PENDING : NegotiationState.COUNTERED,
					scenario.riskOfWalkaway(),
					List.copyOf(history)),
				scenario.buyerProfile(),
				scenario.supplierModel(),
				scenario.bounds(),
				null);
			NegotiationResponse response = negotiationEngine.negotiate(request);
			BigDecimal buyerUtility = utilityCalculator.calculate(supplierOffer, scenario.buyerProfile(), scenario.bounds());
			BigDecimal supplierUtility = scenario.persona().utility(supplierOffer, scenario.bounds());

			if (response.decision() == Decision.ACCEPT) {
				return new ScenarioOutcome(
					Decision.ACCEPT,
					round,
					supplierOffer,
					buyerUtility,
					supplierUtility,
					response.evaluation());
			}

			if (response.decision() == Decision.REJECT) {
				return new ScenarioOutcome(
					Decision.REJECT,
					round,
					supplierOffer,
					buyerUtility,
					supplierUtility,
					response.evaluation());
			}

			OfferVector selectedBuyerOffer = scenario.persona().selectPreferredCounterOffer(response.counterOffers(), scenario.bounds());
			history.add(supplierOffer);
			history.add(selectedBuyerOffer);
			supplierOffer = scenario.persona().nextOffer(selectedBuyerOffer, supplierOffer, round, scenario.maxRounds(), scenario.bounds());
		}

		BigDecimal buyerUtility = utilityCalculator.calculate(supplierOffer, scenario.buyerProfile(), scenario.bounds());
		BigDecimal supplierUtility = scenario.persona().utility(supplierOffer, scenario.bounds());
		return new ScenarioOutcome(
			Decision.REJECT,
			scenario.maxRounds(),
			supplierOffer,
			buyerUtility,
			supplierUtility,
			new OfferEvaluation(
				buyerUtility,
				supplierUtility,
				BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP),
				BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP),
				BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP)));
	}

	private boolean hasStrategySpread(Map<String, Map<NegotiationStrategy, ScenarioOutcome>> results) {
		long scenariosWithSpread = results.values().stream()
			.filter(outcomes -> outcomes.values().stream()
				.map(outcome -> outcome.decision() + ":" + outcome.round() + ":" + outcome.buyerUtility())
				.distinct()
				.count() > 1)
			.count();
		return scenariosWithSpread >= 3;
	}

	private int totalOutcomeCount(Map<String, Map<NegotiationStrategy, ScenarioOutcome>> results) {
		return results.values().stream()
			.mapToInt(Map::size)
			.sum();
	}

	private BigDecimal averageAcceptedUtility(
		Map<String, Map<NegotiationStrategy, ScenarioOutcome>> results,
		NegotiationStrategy strategy
	) {
		List<BigDecimal> utilities = results.values().stream()
			.map(result -> result.get(strategy))
			.filter(outcome -> outcome.decision() == Decision.ACCEPT)
			.map(ScenarioOutcome::buyerUtility)
			.toList();

		return utilities.isEmpty() ? null : average(utilities);
	}

	private BigDecimal averageAcceptedRound(
		Map<String, Map<NegotiationStrategy, ScenarioOutcome>> results,
		NegotiationStrategy strategy
	) {
		List<BigDecimal> rounds = results.values().stream()
			.map(result -> result.get(strategy))
			.filter(outcome -> outcome.decision() == Decision.ACCEPT)
			.map(outcome -> BigDecimal.valueOf(outcome.round()))
			.toList();

		return rounds.isEmpty() ? null : average(rounds);
	}

	private long totalAcceptedOutcomes(Map<String, Map<NegotiationStrategy, ScenarioOutcome>> results) {
		return results.values().stream()
			.flatMap(strategyResults -> strategyResults.values().stream())
			.filter(outcome -> outcome.decision() == Decision.ACCEPT)
			.count();
	}

	private long acceptedScenarioCount(
		Map<String, Map<NegotiationStrategy, ScenarioOutcome>> results,
		NegotiationStrategy strategy
	) {
		return results.values().stream()
			.map(strategyResults -> strategyResults.get(strategy))
			.filter(outcome -> outcome.decision() == Decision.ACCEPT)
			.count();
	}

	private BigDecimal average(List<BigDecimal> values) {
		BigDecimal total = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		return total.divide(BigDecimal.valueOf(values.size()), SCALE, RoundingMode.HALF_UP);
	}

	private String buildReport(
		List<SimulationScenario> scenarios,
		Map<String, Map<NegotiationStrategy, ScenarioOutcome>> results
	) {
		StringBuilder report = new StringBuilder();
		report.append("Strategy simulation matrix").append(System.lineSeparator());
		report.append(String.format(
			Locale.ROOT,
			"%-22s %-12s %-8s %-6s %-9s %-9s %-12s %s%n",
			"Scenario",
			"Strategy",
			"Decision",
			"Round",
			"Buyer U",
			"Supp U",
			"Price",
			"Final Offer"));

		for (SimulationScenario scenario : scenarios) {
			Map<NegotiationStrategy, ScenarioOutcome> scenarioResults = results.get(scenario.name());
			for (NegotiationStrategy strategy : STRATEGIES) {
				ScenarioOutcome outcome = scenarioResults.get(strategy);
				report.append(String.format(
					Locale.ROOT,
					"%-22s %-12s %-8s %-6d %-9s %-9s %-12s %s%n",
					scenario.name(),
					StrategyMetadata.describe(strategy).label(),
					outcome.decision().name(),
					outcome.round(),
					outcome.buyerUtility().toPlainString(),
					outcome.supplierUtility().toPlainString(),
					outcome.finalOffer().price().setScale(2, RoundingMode.HALF_UP).toPlainString(),
					formatOffer(outcome.finalOffer())));
			}
		}

		report.append(System.lineSeparator()).append("Accepted averages by strategy").append(System.lineSeparator());
		report.append(String.format(
			Locale.ROOT,
			"%-12s %-14s %-12s %s%n",
			"Strategy",
			"Avg Buyer U",
			"Avg Round",
			"Accepted Scenarios"));

		for (NegotiationStrategy strategy : STRATEGIES) {
			List<String> acceptedScenarios = scenarios.stream()
				.filter(scenario -> results.get(scenario.name()).get(strategy).decision() == Decision.ACCEPT)
				.map(SimulationScenario::name)
				.toList();
			BigDecimal averageUtility = averageAcceptedUtility(results, strategy);
			BigDecimal averageRound = averageAcceptedRound(results, strategy);
			report.append(String.format(
				Locale.ROOT,
				"%-12s %-14s %-12s %s%n",
				StrategyMetadata.describe(strategy).label(),
				averageUtility == null ? "N/A" : averageUtility.toPlainString(),
				averageRound == null ? "N/A" : averageRound.toPlainString(),
				acceptedScenarios.isEmpty() ? "None" : String.join(", ", acceptedScenarios)));
		}

		return report.toString();
	}

	private String formatOffer(OfferVector offer) {
		return "P=" + offer.price().setScale(2, RoundingMode.HALF_UP).toPlainString()
			+ ", Pay=" + offer.paymentDays()
			+ ", Del=" + offer.deliveryDays()
			+ ", Ctr=" + offer.contractMonths();
	}

	private SimulationScenario marginHardlinerScenario() {
		NegotiationBounds bounds = NegotiationDefaults.bounds();
		BuyerProfile buyerProfile = NegotiationDefaults.buyerProfile();
		SupplierPersona persona = new SupplierPersona(
			"Margin hardliner",
			new BigDecimal("0.60"),
			new BigDecimal("0.10"),
			new BigDecimal("0.20"),
			new BigDecimal("0.10"),
			new BigDecimal("0.78"),
			new BigDecimal("0.68"),
			2,
			new BigDecimal("0.18"),
			new BigDecimal("0.45"),
			3,
			8,
			3,
			9,
			2,
			6);
		return new SimulationScenario(
			"Margin hardliner",
			new OfferVector(new BigDecimal("120.00"), 30, 30, 24),
			buyerProfile,
			bounds,
			persona,
			supplierModel(persona),
			NegotiationDefaults.maxRounds(),
			NegotiationDefaults.riskOfWalkaway());
	}

	private SimulationScenario operationsFocusedScenario() {
		NegotiationBounds bounds = NegotiationDefaults.bounds();
		BuyerProfile buyerProfile = new BuyerProfile(
			NegotiationDefaults.buyerProfile().idealOffer(),
			NegotiationDefaults.buyerProfile().reservationOffer(),
			new IssueWeights(
				new BigDecimal("0.25"),
				new BigDecimal("0.15"),
				new BigDecimal("0.45"),
				new BigDecimal("0.15")),
			BigDecimal.ZERO);
		SupplierPersona persona = new SupplierPersona(
			"Operations flexible",
			new BigDecimal("0.20"),
			new BigDecimal("0.10"),
			new BigDecimal("0.55"),
			new BigDecimal("0.15"),
			new BigDecimal("0.66"),
			new BigDecimal("0.54"),
			0,
			new BigDecimal("0.24"),
			new BigDecimal("0.40"),
			5,
			10,
			6,
			10,
			3,
			6);
		return new SimulationScenario(
			"Operations focused",
			new OfferVector(new BigDecimal("118.00"), 35, 30, 20),
			buyerProfile,
			bounds,
			persona,
			supplierModel(persona),
			NegotiationDefaults.maxRounds(),
			NegotiationDefaults.riskOfWalkaway());
	}

	private SimulationScenario cashflowFocusedScenario() {
		NegotiationBounds bounds = NegotiationDefaults.bounds();
		BuyerProfile buyerProfile = new BuyerProfile(
			NegotiationDefaults.buyerProfile().idealOffer(),
			NegotiationDefaults.buyerProfile().reservationOffer(),
			new IssueWeights(
				new BigDecimal("0.25"),
				new BigDecimal("0.45"),
				new BigDecimal("0.20"),
				new BigDecimal("0.10")),
			BigDecimal.ZERO);
		SupplierPersona persona = new SupplierPersona(
			"Cashflow anchored",
			new BigDecimal("0.20"),
			new BigDecimal("0.55"),
			new BigDecimal("0.15"),
			new BigDecimal("0.10"),
			new BigDecimal("0.72"),
			new BigDecimal("0.58"),
			1,
			new BigDecimal("0.15"),
			new BigDecimal("0.30"),
			2,
			5,
			3,
			7,
			2,
			4);
		return new SimulationScenario(
			"Cashflow focused",
			new OfferVector(new BigDecimal("117.00"), 30, 24, 18),
			buyerProfile,
			bounds,
			persona,
			supplierModel(persona),
			NegotiationDefaults.maxRounds(),
			NegotiationDefaults.riskOfWalkaway());
	}

	private SimulationScenario lateCloserScenario() {
		NegotiationBounds bounds = NegotiationDefaults.bounds();
		BuyerProfile buyerProfile = new BuyerProfile(
			NegotiationDefaults.buyerProfile().idealOffer(),
			NegotiationDefaults.buyerProfile().reservationOffer(),
			new IssueWeights(
				new BigDecimal("0.45"),
				new BigDecimal("0.15"),
				new BigDecimal("0.25"),
				new BigDecimal("0.15")),
			new BigDecimal("0.0500"));
		SupplierPersona persona = new SupplierPersona(
			"Late closer",
			new BigDecimal("0.35"),
			new BigDecimal("0.20"),
			new BigDecimal("0.25"),
			new BigDecimal("0.20"),
			new BigDecimal("0.74"),
			new BigDecimal("0.48"),
			3,
			new BigDecimal("0.10"),
			new BigDecimal("0.55"),
			2,
			8,
			2,
			10,
			1,
			8);
		return new SimulationScenario(
			"Late closer",
			new OfferVector(new BigDecimal("120.00"), 30, 28, 22),
			buyerProfile,
			bounds,
			persona,
			supplierModel(persona),
			NegotiationDefaults.maxRounds(),
			NegotiationDefaults.riskOfWalkaway());
	}

	private SimulationScenario nearSettlementScenario() {
		NegotiationBounds bounds = NegotiationDefaults.bounds();
		BuyerProfile buyerProfile = NegotiationDefaults.buyerProfile();
		SupplierPersona persona = new SupplierPersona(
			"Near settlement",
			new BigDecimal("0.35"),
			new BigDecimal("0.20"),
			new BigDecimal("0.25"),
			new BigDecimal("0.20"),
			new BigDecimal("0.48"),
			new BigDecimal("0.34"),
			0,
			new BigDecimal("0.30"),
			new BigDecimal("0.60"),
			6,
			12,
			5,
			10,
			3,
			8);
		return new SimulationScenario(
			"Near settlement",
			new OfferVector(new BigDecimal("102.00"), 50, 12, 12),
			buyerProfile,
			bounds,
			persona,
			supplierModel(persona),
			NegotiationDefaults.maxRounds(),
			NegotiationDefaults.riskOfWalkaway());
	}

	private SimulationScenario deadlineSettlementScenario() {
		NegotiationBounds bounds = NegotiationDefaults.bounds();
		BuyerProfile buyerProfile = new BuyerProfile(
			NegotiationDefaults.buyerProfile().idealOffer(),
			NegotiationDefaults.buyerProfile().reservationOffer(),
			new IssueWeights(
				new BigDecimal("0.50"),
				new BigDecimal("0.15"),
				new BigDecimal("0.20"),
				new BigDecimal("0.15")),
			new BigDecimal("0.0200"));
		SupplierPersona persona = new SupplierPersona(
			"Deadline settlement",
			new BigDecimal("0.30"),
			new BigDecimal("0.20"),
			new BigDecimal("0.25"),
			new BigDecimal("0.25"),
			new BigDecimal("0.45"),
			new BigDecimal("0.30"),
			0,
			new BigDecimal("0.35"),
			new BigDecimal("0.70"),
			7,
			14,
			6,
			12,
			4,
			10);
		return new SimulationScenario(
			"Deadline settlement",
			new OfferVector(new BigDecimal("104.00"), 55, 10, 9),
			buyerProfile,
			bounds,
			persona,
			supplierModel(persona),
			NegotiationDefaults.maxRounds(),
			NegotiationDefaults.riskOfWalkaway());
	}

	private SupplierModel supplierModel(SupplierPersona persona) {
		BigDecimal totalWeight = persona.priceWeight()
			.add(persona.paymentWeight())
			.add(persona.deliveryWeight())
			.add(persona.contractWeight());
		return new SupplierModel(
			Map.of(
				SupplierArchetype.MARGIN_FOCUSED, persona.priceWeight().divide(totalWeight, SCALE, RoundingMode.HALF_UP),
				SupplierArchetype.CASHFLOW_FOCUSED, persona.paymentWeight().divide(totalWeight, SCALE, RoundingMode.HALF_UP),
				SupplierArchetype.OPERATIONS_FOCUSED, persona.deliveryWeight().divide(totalWeight, SCALE, RoundingMode.HALF_UP),
				SupplierArchetype.STABILITY_FOCUSED, persona.contractWeight().divide(totalWeight, SCALE, RoundingMode.HALF_UP)),
			persona.acceptanceThreshold());
	}

	private record SimulationScenario(
		String name,
		OfferVector initialSupplierOffer,
		BuyerProfile buyerProfile,
		NegotiationBounds bounds,
		SupplierPersona persona,
		SupplierModel supplierModel,
		int maxRounds,
		BigDecimal riskOfWalkaway
	) {
	}

	private record ScenarioOutcome(
		Decision decision,
		int round,
		OfferVector finalOffer,
		BigDecimal buyerUtility,
		BigDecimal supplierUtility,
		OfferEvaluation evaluation
	) {
	}

	private record SupplierPersona(
		String name,
		BigDecimal priceWeight,
		BigDecimal paymentWeight,
		BigDecimal deliveryWeight,
		BigDecimal contractWeight,
		BigDecimal acceptanceThreshold,
		BigDecimal finalRoundThreshold,
		int stallRounds,
		BigDecimal earlyPriceStepRatio,
		BigDecimal latePriceStepRatio,
		int earlyPaymentStep,
		int latePaymentStep,
		int earlyDeliveryStep,
		int lateDeliveryStep,
		int earlyContractStep,
		int lateContractStep
	) {

		OfferVector selectPreferredCounterOffer(List<OfferVector> counterOffers, NegotiationBounds bounds) {
			return counterOffers.stream()
				.max((left, right) -> utility(left, bounds).compareTo(utility(right, bounds)))
				.orElseThrow();
		}

		OfferVector nextOffer(
			OfferVector buyerOffer,
			OfferVector currentSupplierOffer,
			int round,
			int maxRounds,
			NegotiationBounds bounds
		) {
			if (utility(buyerOffer, bounds).compareTo(acceptanceThreshold(round, maxRounds)) >= 0) {
				return buyerOffer;
			}

			if (round <= stallRounds) {
				return currentSupplierOffer;
			}

			boolean lateStage = round >= maxRounds - 2;
			BigDecimal priceRatio = lateStage ? latePriceStepRatio : earlyPriceStepRatio;
			int paymentStep = lateStage ? latePaymentStep : earlyPaymentStep;
			int deliveryStep = lateStage ? lateDeliveryStep : earlyDeliveryStep;
			int contractStep = lateStage ? lateContractStep : earlyContractStep;

			return new OfferVector(
				movePrice(currentSupplierOffer.price(), buyerOffer.price(), priceRatio),
				moveInteger(currentSupplierOffer.paymentDays(), buyerOffer.paymentDays(), paymentStep),
				moveInteger(currentSupplierOffer.deliveryDays(), buyerOffer.deliveryDays(), deliveryStep),
				moveInteger(currentSupplierOffer.contractMonths(), buyerOffer.contractMonths(), contractStep));
		}

		BigDecimal utility(OfferVector offer, NegotiationBounds bounds) {
			BigDecimal totalWeight = priceWeight.add(paymentWeight).add(deliveryWeight).add(contractWeight);
			BigDecimal priceUtility = Normalization.normalizePositiveDecimal(
				offer.price(),
				bounds.minPrice(),
				bounds.maxPrice());
			BigDecimal paymentUtility = Normalization.normalizeNegativeInt(
				offer.paymentDays(),
				bounds.minPaymentDays(),
				bounds.maxPaymentDays());
			BigDecimal deliveryUtility = Normalization.normalizePositiveInt(
				offer.deliveryDays(),
				bounds.minDeliveryDays(),
				bounds.maxDeliveryDays());
			BigDecimal contractUtility = Normalization.normalizePositiveInt(
				offer.contractMonths(),
				bounds.minContractMonths(),
				bounds.maxContractMonths());

			return priceUtility.multiply(priceWeight)
				.add(paymentUtility.multiply(paymentWeight))
				.add(deliveryUtility.multiply(deliveryWeight))
				.add(contractUtility.multiply(contractWeight))
				.divide(totalWeight, SCALE, RoundingMode.HALF_UP);
		}

		private BigDecimal acceptanceThreshold(int round, int maxRounds) {
			if (round >= maxRounds - 1) {
				return finalRoundThreshold;
			}
			return acceptanceThreshold;
		}

		private BigDecimal movePrice(BigDecimal current, BigDecimal target, BigDecimal ratio) {
			if (current.compareTo(target) <= 0) {
				return current;
			}

			BigDecimal delta = current.subtract(target);
			BigDecimal step = delta.multiply(ratio).setScale(2, RoundingMode.CEILING);
			if (step.compareTo(new BigDecimal("0.01")) < 0) {
				step = new BigDecimal("0.01");
			}

			return current.subtract(step).max(target).setScale(2, RoundingMode.HALF_UP);
		}

		private int moveInteger(int current, int target, int step) {
			if (current == target) {
				return current;
			}

			if (current < target) {
				return Math.min(current + step, target);
			}

			return Math.max(current - step, target);
		}
	}
}
