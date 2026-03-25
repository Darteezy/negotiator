package org.GLM.negoriator.application;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SupplierMessageIntentParser {

	private static final Pattern OPTION_SELECTION_PATTERN = Pattern.compile(
		"\\b(option|offer)\\s*(\\d+|one|two|three|first|second|third)\\b",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern OPTION_REFERENCE_PATTERN = Pattern.compile(
		"\\b(the\\s+)?(\\d+|one|two|three|first|second|third)\\s+(option|offer|one|structure|package|version)\\b",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern BUYER_SELECTION_PATTERN = Pattern.compile(
		"\\b(original offer|same terms|your offer|your terms|your last offer|your latest offer|that option|that offer|that structure|that package|that version)\\b",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern DESCRIPTIVE_BUYER_SELECTION_PATTERN = Pattern.compile(
		"\\b(lower|lowest|cheaper|cheapest|higher|highest|longer|longest|shorter|shortest|faster|fastest|quicker|quickest|earlier)" +
			"(?:-|\\s)+(price|payment|delivery|contract)" +
			"(?:-|\\s)+(option|offer|structure|package|version)\\b",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern DECLINE_PATTERN = Pattern.compile(
		"\\b(reject|decline|cannot accept|can't accept|will not accept|won't accept|cannot proceed|can't proceed|won't proceed|not acceptable|not workable|no deal)\\b",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern DIRECT_ACCEPTANCE_PATTERN = Pattern.compile(
		"\\b(accept|accepted|agree|agreed|confirm|confirmed|we can proceed|we are ready to proceed|we're ready to proceed|let's proceed|we have a deal)\\b",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern REFERENCED_OFFER_ACCEPTANCE_PATTERN = Pattern.compile(
		"\\b(go with|go ahead with|choose|pick|take|proceed with|move forward with|works for us|acceptable|fine by us|ready to proceed|good to proceed)\\b",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern COUNTER_PROPOSAL_PATTERN = Pattern.compile(
		"\\b(counter|i propose|i offer|final offer|if you accept|we can do|instead we can|provided that)\\b",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern STRUCTURED_TERMS_PATTERN = Pattern.compile(
		"\\bprice\\b.*\\d|\\bpayment\\b.*\\d|\\bdelivery\\b.*\\d|\\bcontract\\b.*\\d",
		Pattern.CASE_INSENSITIVE);

	SupplierMessageIntent parse(String supplierMessage) {
		if (supplierMessage == null || supplierMessage.isBlank()) {
			return new SupplierMessageIntent(SupplierIntentType.ACCEPT_ACTIVE_OFFER, null, false, true);
		}

		Integer selectedBuyerOfferIndex = selectedBuyerOfferIndex(supplierMessage);
		boolean containsCounterProposalSignal = containsCounterProposalSignal(supplierMessage);
		boolean referencesBuyerOffer = !containsCounterProposalSignal
			&& (selectedBuyerOfferIndex != null
				|| BUYER_SELECTION_PATTERN.matcher(supplierMessage).find()
				|| DESCRIPTIVE_BUYER_SELECTION_PATTERN.matcher(supplierMessage).find());
		boolean containsAcceptanceSignal = containsAcceptanceSignal(supplierMessage, referencesBuyerOffer);
		boolean declineSignal = !containsCounterProposalSignal && DECLINE_PATTERN.matcher(supplierMessage).find();

		SupplierIntentType type;
		if (containsCounterProposalSignal) {
			type = SupplierIntentType.PROPOSE_NEW_TERMS;
		} else if (declineSignal) {
			type = SupplierIntentType.REJECT_OR_DECLINE;
		} else if (containsAcceptanceSignal) {
			type = SupplierIntentType.ACCEPT_ACTIVE_OFFER;
		} else if (referencesBuyerOffer) {
			type = SupplierIntentType.SELECT_COUNTER_OPTION;
		} else {
			type = SupplierIntentType.UNCLEAR;
		}

		return new SupplierMessageIntent(type, selectedBuyerOfferIndex, referencesBuyerOffer, containsAcceptanceSignal);
	}

	private boolean containsCounterProposalSignal(String supplierMessage) {
		return COUNTER_PROPOSAL_PATTERN.matcher(supplierMessage).find()
			|| STRUCTURED_TERMS_PATTERN.matcher(supplierMessage).find();
	}

	private boolean containsAcceptanceSignal(String supplierMessage, boolean referencesBuyerOffer) {
		if (DIRECT_ACCEPTANCE_PATTERN.matcher(supplierMessage).find()) {
			return true;
		}

		return referencesBuyerOffer && REFERENCED_OFFER_ACCEPTANCE_PATTERN.matcher(supplierMessage).find();
	}

	private Integer selectedBuyerOfferIndex(String supplierMessage) {
		Integer fromOptionPattern = extractSelectedIndex(OPTION_SELECTION_PATTERN.matcher(supplierMessage), 2);
		if (fromOptionPattern != null) {
			return fromOptionPattern;
		}

		return extractSelectedIndex(OPTION_REFERENCE_PATTERN.matcher(supplierMessage), 2);
	}

	private Integer extractSelectedIndex(Matcher matcher, int groupIndex) {
		if (!matcher.find()) {
			return null;
		}

		String value = matcher.group(groupIndex).toLowerCase(Locale.ROOT);
		return switch (value) {
			case "1", "one", "first" -> 1;
			case "2", "two", "second" -> 2;
			case "3", "three", "third" -> 3;
			default -> null;
		};
	}

	record SupplierMessageIntent(
		SupplierIntentType type,
		Integer selectedBuyerOfferIndex,
		boolean referencesBuyerOffer,
		boolean containsAcceptanceSignal
	) {
		boolean acceptsBuyerOffer() {
			return type == SupplierIntentType.ACCEPT_ACTIVE_OFFER;
		}

		boolean selectsCounterOption() {
			return type == SupplierIntentType.SELECT_COUNTER_OPTION;
		}

		boolean proposesNewTerms() {
			return type == SupplierIntentType.PROPOSE_NEW_TERMS;
		}

		boolean rejectsOrDeclines() {
			return type == SupplierIntentType.REJECT_OR_DECLINE;
		}
	}

	enum SupplierIntentType {
		ACCEPT_ACTIVE_OFFER,
		SELECT_COUNTER_OPTION,
		PROPOSE_NEW_TERMS,
		REJECT_OR_DECLINE,
		UNCLEAR
	}
}
