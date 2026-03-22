import { formatMoney, sentenceCase } from "@/lib/format";

export function buildChatTranscript(
  session,
  supplierMessages = [],
  pendingSupplierMessage = "",
) {
  return buildConversationModel(
    session,
    supplierMessages,
    pendingSupplierMessage,
  ).chatEvents;
}

export function buildDebugTimeline(
  session,
  supplierMessages = [],
  pendingSupplierMessage = "",
) {
  return buildConversationModel(
    session,
    supplierMessages,
    pendingSupplierMessage,
  ).debugEvents;
}

function buildConversationModel(
  session,
  supplierMessages = [],
  pendingSupplierMessage = "",
) {
  const conversation = session?.conversation ?? [];
  const rounds = session?.rounds ?? [];
  const strategyHistory = session?.strategyHistory ?? [];
  const chatEvents = [];
  const debugEvents = [];
  const roundLinks = new Map();
  let nextMessageNumber = 1;

  const appendChatEvent = (event) => {
    const messageId = `M${nextMessageNumber}`;
    nextMessageNumber += 1;

    const chatEvent = {
      ...event,
      messageId,
      anchorId: `message-${messageId.toLowerCase()}`,
    };

    chatEvents.push(chatEvent);
    return chatEvent;
  };

  const messageLink = (chatEvent) => {
    if (!chatEvent) {
      return null;
    }

    return {
      messageId: chatEvent.messageId,
      href: `#${chatEvent.anchorId}`,
    };
  };

  if (session) {
    appendChatEvent({
      actor: "buyer",
      title: "Buyer",
      at: conversation[0]?.at ?? null,
      message: buildOpeningBuyerLetter(session),
      isOpening: true,
      eventType: "BUYER_OPENING",
    });
  }

  if (rounds.length > 0) {
    rounds.forEach((round, index) => {
      const supplierChatEvent = appendChatEvent({
        actor: "supplier",
        title: "You",
        at: round.supplierOffer?.at ?? null,
        message:
          supplierMessages[index] ??
          round.supplierOffer?.message ??
          formatFallbackSupplierTerms(round.supplierOffer?.terms),
        eventType: "SUPPLIER_OFFER",
      });

      let buyerChatEvent = null;

      if (round.buyerReply) {
        buyerChatEvent = appendChatEvent({
          actor: "buyer",
          title: "Buyer",
          at: round.buyerReply.decidedAt ?? round.supplierOffer?.at ?? null,
          message: buildBuyerLetter({
            decision: round.buyerReply.decision,
            resultingStatus: round.buyerReply.resultingStatus,
            explanation: round.buyerReply.explanation,
            terms: round.buyerReply.counterOffer ?? round.buyerReply.terms,
            counterOffers: round.buyerReply.counterOffers,
          }),
          eventType: "BUYER_REPLY",
        });
      }

      roundLinks.set(round.roundNumber, {
        supplier: messageLink(supplierChatEvent),
        buyer: messageLink(buyerChatEvent),
      });

      debugEvents.push({
        actor: "supplier",
        title: "Input parsed",
        at: round.supplierOffer?.at ?? null,
        links: [messageLink(supplierChatEvent)].filter(Boolean),
        summary:
          "Supplier message parsed into structured terms for evaluation.",
        terms: round.supplierOffer?.terms ?? null,
        counterOffers: [],
        debug: {
          reasonLabel: "Supplier input",
          narrative:
            "The frontend normalized the supplier message and sent the extracted terms to the backend engine.",
        },
      });

      if (round.buyerReply) {
        debugEvents.push({
          actor: "buyer",
          title: "Decision computed",
          at: round.buyerReply.decidedAt ?? round.supplierOffer?.at ?? null,
          links: [
            messageLink(supplierChatEvent),
            messageLink(buyerChatEvent),
          ].filter(Boolean),
          summary:
            "Buyer utility, target gap, and strategy logic produced the visible reply.",
          terms:
            round.buyerReply.counterOffer ?? round.buyerReply.terms ?? null,
          counterOffers: round.buyerReply.counterOffers ?? [],
          debug: {
            ...round.buyerReply,
            reasonLabel: round.buyerReply.reasonCode
              ? sentenceCase(round.buyerReply.reasonCode)
              : "Buyer decision",
            narrative:
              round.buyerReply.strategyRationale ??
              round.buyerReply.explanation ??
              "The buyer engine evaluated the supplier offer and prepared this reply.",
            strategy: round.buyerReply.strategyUsed,
          },
        });
      }
    });

    strategyHistory
      .filter((change) => change.trigger !== "INITIAL_SELECTION")
      .forEach((change) => {
        const roundReference = roundLinks.get(change.roundNumber);

        debugEvents.push({
          actor: "system",
          title: "Strategy switch",
          at: change.at ?? null,
          links: [roundReference?.supplier, roundReference?.buyer].filter(
            Boolean,
          ),
          summary: change.rationale,
          terms: null,
          counterOffers: [],
          debug: {
            strategy: change.nextStrategy,
            switchTrigger: change.trigger,
            reasonLabel: `Trigger: ${sentenceCase(change.trigger)}`,
            narrative: change.rationale,
          },
        });
      });
  } else {
    let supplierMessageIndex = 0;
    const fallbackLinks = [];

    for (const event of conversation) {
      if (event.actor === "supplier") {
        const chatEvent = appendChatEvent({
          actor: "supplier",
          title: "You",
          at: event.at,
          message: supplierMessages[supplierMessageIndex++] ?? event.message,
          eventType: event.eventType,
        });

        fallbackLinks.push(messageLink(chatEvent));
        debugEvents.push({
          actor: "supplier",
          title: "Input parsed",
          at: event.at,
          links: [messageLink(chatEvent)],
          summary:
            "Supplier message parsed into structured terms for evaluation.",
          terms: event.terms,
          counterOffers: [],
          debug: {
            reasonLabel: "Supplier input",
            narrative:
              "The frontend normalized the supplier message and sent the extracted terms to the backend engine.",
          },
        });
        continue;
      }

      if (event.actor === "buyer") {
        const chatEvent = appendChatEvent({
          actor: "buyer",
          title: "Buyer",
          at: event.at,
          message: buildBuyerLetter(event),
          eventType: event.eventType,
        });

        fallbackLinks.push(messageLink(chatEvent));
        debugEvents.push({
          actor: "buyer",
          title: "Decision computed",
          at: event.at,
          links: fallbackLinks.slice(-2),
          summary:
            "Buyer utility, target gap, and strategy logic produced the visible reply.",
          terms: event.terms,
          counterOffers: event.counterOffers ?? [],
          debug: {
            reasonLabel: event.debug?.reasonCode
              ? sentenceCase(event.debug.reasonCode)
              : "Buyer decision",
            narrative:
              event.debug?.strategyRationale ??
              event.message ??
              "The buyer engine evaluated the supplier offer and prepared this reply.",
            ...event.debug,
          },
        });
        continue;
      }

      debugEvents.push({
        actor: "system",
        title: event.title,
        at: event.at,
        links: fallbackLinks.slice(-2),
        summary: event.message,
        terms: null,
        counterOffers: [],
        debug: {
          reasonLabel: event.debug?.switchTrigger
            ? `Trigger: ${sentenceCase(event.debug.switchTrigger)}`
            : "System event",
          narrative:
            event.debug?.strategyRationale ??
            event.message ??
            "The system recorded an internal negotiation checkpoint.",
          ...event.debug,
        },
      });
    }
  }

  if (pendingSupplierMessage.trim()) {
    const pendingChatEvent = appendChatEvent({
      actor: "supplier",
      title: "You",
      at: null,
      message: pendingSupplierMessage.trim(),
      eventType: "SUPPLIER_PENDING",
      pending: true,
    });

    debugEvents.push({
      actor: "supplier",
      title: "Awaiting backend",
      at: null,
      links: [messageLink(pendingChatEvent)],
      summary:
        "Supplier message sent. Waiting for backend evaluation and reply.",
      terms: null,
      counterOffers: [],
      debug: {
        reasonLabel: "Pending submission",
        narrative:
          "The supplier message has been sent from the chat UI and is waiting for the backend response.",
      },
    });
  }

  return { chatEvents, debugEvents };
}

function buildOpeningBuyerLetter(session) {
  return [
    "Dear Supplier,",
    "Thank you for engaging with our procurement team.",
    "The buyer is ready to review proposals across price, payment timing, delivery timing, and contract length.",
    "Please send your initial commercial proposal when ready.",
  ].join(" ");
}

function buildBuyerLetter(event) {
  const counterOffers = event.counterOffers ?? [];
  const primaryTerms = event.terms ?? counterOffers[0] ?? null;

  if (event.decision === "ACCEPT" || event.resultingStatus === "ACCEPTED") {
    return [
      "Dear Supplier,",
      "Thank you for your confirmation.",
      primaryTerms
        ? `The buyer accepts the agreed terms: ${formatTermsSentence(primaryTerms)}.`
        : "The buyer accepts the agreed terms and closes the negotiation.",
      event.explanation ?? "This negotiation is now closed.",
    ].join(" ");
  }

  if (event.decision === "REJECT" || event.resultingStatus === "REJECTED") {
    return [
      "Dear Supplier,",
      "Thank you for your proposal.",
      event.explanation ??
        "The buyer cannot approve the current terms and requests a revised commercial offer.",
    ].join(" ");
  }

  if (!primaryTerms) {
    return [
      "Dear Supplier,",
      "Thank you for your proposal.",
      "The buyer cannot approve the current terms and requests a revised commercial offer.",
    ].join(" ");
  }

  if (counterOffers.length > 1) {
    return [
      "Dear Supplier,",
      "Thank you for your proposal.",
      "The buyer can continue under the following alternative arrangements:",
      counterOffers
        .map(
          (terms, index) =>
            `Option ${index + 1}: ${formatTermsSentence(terms)}.`,
        )
        .join(" "),
      "Please confirm which arrangement you would like to discuss further.",
    ].join(" ");
  }

  return [
    "Dear Supplier,",
    "Thank you for your proposal.",
    `After review, the buyer can continue under the following terms: ${formatTermsSentence(primaryTerms)}.`,
    "Please let us know whether you can proceed on this basis.",
  ].join(" ");
}

function formatTermsSentence(terms) {
  return [
    `price ${formatMoney(terms.price)}`,
    `payment in ${terms.paymentDays} days`,
    `delivery in ${terms.deliveryDays} days`,
    `contract term ${terms.contractMonths} months`,
  ].join(", ");
}

function formatFallbackSupplierTerms(terms) {
  if (!terms) {
    return "Supplier submitted a proposal.";
  }

  return [
    `Price ${formatMoney(terms.price)}`,
    `payment ${terms.paymentDays} days`,
    `delivery ${terms.deliveryDays} days`,
    `contract ${terms.contractMonths} months`,
  ].join(", ");
}
