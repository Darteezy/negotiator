package org.GLM.negoriator.application;

import java.util.Arrays;
import java.util.List;

import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationStrategy;

public final class StrategyMetadata {

	private StrategyMetadata() {
	}

	public static StrategyDescriptor describe(NegotiationStrategy strategy) {
		return switch (strategy) {
			case BASELINE -> new StrategyDescriptor(
				strategy.name(),
				"Baseline",
				"Balanced default strategy with steady concessions and predictable counters.",
				"Concede at a steady pace without being especially firm or especially eager.",
				"Keep negotiating when the offer is close enough to repair, and reject only when it is clearly too far outside the buyer range.",
				"Baseline is active as the balanced default strategy with a steady concession pace.",
				"Use a balanced and neutral opening tone. Invite an opening offer without sounding rigid or overly eager.",
				"Use a balanced tone. State the buyer position clearly, keep the message constructive, and avoid sounding either stubborn or overly flexible.");
			case MESO -> new StrategyDescriptor(
				strategy.name(),
				"Meso",
				"Explores supplier preferences by offering several buyer-safe paths instead of only one.",
				"Concede selectively by shaping multiple buyer-equivalent options rather than pushing one narrow path.",
				"When the supplier is close to the buyer boundary, try to keep the discussion alive with clear options before rejecting.",
				"Meso is active to explore the supplier's preferences through several buyer-safe options.",
				"Use an exploratory opening tone that signals openness to structured options while still protecting buyer value.",
				"If countering, present the buyer position as a small set of viable paths and invite the supplier to indicate which direction is closest to workable.");
			case BOULWARE -> new StrategyDescriptor(
				strategy.name(),
				"Boulware",
				"Firm strategy that holds buyer value longer and makes slower concessions early.",
				"Concede slowly and protect the buyer target for as long as practical.",
				"Stay firm near the boundary, but still counter when the offer is close enough to repair instead of rejecting too early.",
				"Boulware is active to keep the buyer firm for longer and delay concessions until the supplier shows stronger movement.",
				"Use a firm but professional opening tone that signals clear expectations without sounding hostile.",
				"Use firmer wording, protect the anchor, and keep flexibility limited unless the supplier has earned movement.");
			case CONCEDER -> new StrategyDescriptor(
				strategy.name(),
				"Conceder",
				"Faster-closing strategy that softens earlier to increase the chance of reaching a deal.",
				"Concede earlier and more visibly when preserving deal momentum matters more than holding every point of value.",
				"Allow more near-boundary offers to continue into countering as long as the buyer can still steer them back inside the workable range.",
				"Conceder is active to improve close probability by softening earlier and keeping momentum in the conversation.",
				"Use a warm and practical opening tone that encourages engagement and a quick start to bargaining.",
				"Use a more flexible tone, acknowledge supplier movement when present, and steer toward closure without sounding weak.");
			case TIT_FOR_TAT -> new StrategyDescriptor(
				strategy.name(),
				"Tit for Tat",
				"Reciprocal strategy that rewards supplier movement and stays cautious when the supplier does not move.",
				"Mirror the supplier's level of cooperation instead of following a purely time-driven concession pace.",
				"Prefer measured counters over abrupt rejection when the supplier has shown real progress toward the buyer range.",
				"Tit for Tat is active to respond directly to supplier movement and reward real concessions with measured reciprocity.",
				"Use a professional opening tone that signals willingness to respond in kind to constructive supplier movement.",
				"Frame buyer movement as reciprocal. If the supplier moved, acknowledge it briefly and answer with proportionate movement. If not, stay cautious and firm.");
		};
	}

	public static List<StrategyDescriptor> all() {
		return Arrays.stream(NegotiationStrategy.values())
			.map(StrategyMetadata::describe)
			.toList();
	}

	public static String rationaleFor(NegotiationStrategy strategy) {
		return describe(strategy).rationale();
	}

	public static String initialSelectionRationale(NegotiationStrategy strategy) {
		return "Session started with " + describe(strategy).label() + " as the configured opening strategy.";
	}

	public static String manualChangeRationale(NegotiationStrategy strategy) {
		return "Session settings updated manually. Upcoming rounds will use " + describe(strategy).label() + ".";
	}

	public record StrategyDescriptor(
		String name,
		String label,
		String summary,
		String concessionStyle,
		String boundaryStyle,
		String rationale,
		String openingPromptGuidance,
		String replyPromptGuidance
	) {
	}
}