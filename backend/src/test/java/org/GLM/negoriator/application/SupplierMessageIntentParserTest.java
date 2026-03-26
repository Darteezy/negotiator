package org.GLM.negoriator.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SupplierMessageIntentParserTest {

	private final SupplierMessageIntentParser parser = new SupplierMessageIntentParser();

	@Test
	void parsesExplicitAcceptanceOfNumberedOption() {
		SupplierMessageIntentParser.SupplierMessageIntent intent = parser.parse("We agree with option 1");

		assertEquals(SupplierMessageIntentParser.SupplierIntentType.ACCEPT_ACTIVE_OFFER, intent.type());
		assertEquals(SupplierMessageIntentParser.SupplierIntentSource.DETERMINISTIC, intent.source());
		assertTrue(intent.referencesBuyerOffer());
		assertEquals(1, intent.selectedBuyerOfferIndex());
	}

	@Test
	void parsesSelectionWithoutAcceptanceAsNonClosingReference() {
		SupplierMessageIntentParser.SupplierMessageIntent intent = parser.parse("option 2");

		assertEquals(SupplierMessageIntentParser.SupplierIntentType.SELECT_COUNTER_OPTION, intent.type());
		assertEquals(SupplierMessageIntentParser.SupplierIntentSource.DETERMINISTIC, intent.source());
		assertTrue(intent.referencesBuyerOffer());
		assertEquals(2, intent.selectedBuyerOfferIndex());
	}

	@Test
	void parsesContextualFirstOptionWording() {
		SupplierMessageIntentParser.SupplierMessageIntent intent = parser.parse("the first option works for us");

		assertEquals(SupplierMessageIntentParser.SupplierIntentType.ACCEPT_ACTIVE_OFFER, intent.type());
		assertEquals(SupplierMessageIntentParser.SupplierIntentSource.DETERMINISTIC, intent.source());
		assertTrue(intent.referencesBuyerOffer());
		assertEquals(1, intent.selectedBuyerOfferIndex());
	}

	@Test
	void parsesActionBasedAcceptanceOfBuyerOption() {
		SupplierMessageIntentParser.SupplierMessageIntent intent = parser.parse("Let's go with the first package.");

		assertEquals(SupplierMessageIntentParser.SupplierIntentType.ACCEPT_ACTIVE_OFFER, intent.type());
		assertEquals(SupplierMessageIntentParser.SupplierIntentSource.DETERMINISTIC, intent.source());
		assertTrue(intent.referencesBuyerOffer());
		assertEquals(1, intent.selectedBuyerOfferIndex());
	}

	@Test
	void classifiesDescriptiveBuyerOptionReferenceAsSelection() {
		SupplierMessageIntentParser.SupplierMessageIntent intent = parser.parse("The faster delivery option is closest for us.");

		assertEquals(SupplierMessageIntentParser.SupplierIntentType.SELECT_COUNTER_OPTION, intent.type());
		assertEquals(SupplierMessageIntentParser.SupplierIntentSource.DETERMINISTIC, intent.source());
		assertTrue(intent.referencesBuyerOffer());
		assertEquals(null, intent.selectedBuyerOfferIndex());
	}

	@Test
	void classifiesDescriptiveBuyerOptionAcceptanceAsAcceptance() {
		SupplierMessageIntentParser.SupplierMessageIntent intent = parser.parse("Let's go with the faster delivery option.");

		assertEquals(SupplierMessageIntentParser.SupplierIntentType.ACCEPT_ACTIVE_OFFER, intent.type());
		assertEquals(SupplierMessageIntentParser.SupplierIntentSource.DETERMINISTIC, intent.source());
		assertTrue(intent.referencesBuyerOffer());
		assertEquals(null, intent.selectedBuyerOfferIndex());
	}

	@Test
	void doesNotMisreadCounterProposalAsAcceptance() {
		SupplierMessageIntentParser.SupplierMessageIntent intent = parser.parse("If you accept, we can do price 102 and delivery 18 days.");

		assertEquals(SupplierMessageIntentParser.SupplierIntentType.PROPOSE_NEW_TERMS, intent.type());
		assertEquals(SupplierMessageIntentParser.SupplierIntentSource.DETERMINISTIC, intent.source());
		assertFalse(intent.referencesBuyerOffer());
		assertEquals(null, intent.selectedBuyerOfferIndex());
	}

	@Test
	void classifiesStructuredTermsMessageAsCounterproposal() {
		SupplierMessageIntentParser.SupplierMessageIntent intent = parser.parse("Price 102, payment 45, delivery 18, contract 12.");

		assertEquals(SupplierMessageIntentParser.SupplierIntentType.PROPOSE_NEW_TERMS, intent.type());
		assertEquals(SupplierMessageIntentParser.SupplierIntentSource.DETERMINISTIC, intent.source());
		assertFalse(intent.referencesBuyerOffer());
		assertEquals(null, intent.selectedBuyerOfferIndex());
	}

	@Test
	void classifiesDirectDeclineSeparately() {
		SupplierMessageIntentParser.SupplierMessageIntent intent = parser.parse("We cannot accept these terms and will not proceed.");

		assertEquals(SupplierMessageIntentParser.SupplierIntentType.REJECT_OR_DECLINE, intent.type());
		assertEquals(SupplierMessageIntentParser.SupplierIntentSource.DETERMINISTIC, intent.source());
		assertFalse(intent.referencesBuyerOffer());
		assertEquals(null, intent.selectedBuyerOfferIndex());
	}

	@Test
	void marksUnclearMessagesAsUnclear() {
		SupplierMessageIntentParser.SupplierMessageIntent intent = parser.parse("Please clarify the contract point.");

		assertEquals(SupplierMessageIntentParser.SupplierIntentType.UNCLEAR, intent.type());
		assertEquals(SupplierMessageIntentParser.SupplierIntentSource.DETERMINISTIC, intent.source());
		assertFalse(intent.referencesBuyerOffer());
		assertEquals(null, intent.selectedBuyerOfferIndex());
	}

	@Test
	void doesNotTreatGenericWorksQuestionAsAcceptance() {
		SupplierMessageIntentParser.SupplierMessageIntent intent = parser.parse("What works for you on delivery?");

		assertEquals(SupplierMessageIntentParser.SupplierIntentType.UNCLEAR, intent.type());
		assertEquals(SupplierMessageIntentParser.SupplierIntentSource.DETERMINISTIC, intent.source());
		assertFalse(intent.referencesBuyerOffer());
		assertEquals(null, intent.selectedBuyerOfferIndex());
	}

	@Test
	void blankMessageDefaultsToAcceptanceCompatibility() {
		SupplierMessageIntentParser.SupplierMessageIntent intent = parser.parse("   ");

		assertEquals(SupplierMessageIntentParser.SupplierIntentType.ACCEPT_ACTIVE_OFFER, intent.type());
		assertEquals(SupplierMessageIntentParser.SupplierIntentSource.DETERMINISTIC, intent.source());
		assertFalse(intent.referencesBuyerOffer());
		assertEquals(null, intent.selectedBuyerOfferIndex());
	}
}
