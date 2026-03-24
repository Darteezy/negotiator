package org.GLM.negoriator.controller;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.GLM.negoriator.ai.AiGatewayService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class AIController {

    private static final String OFFER_PARSE_PROMPT = "You extract the supplier's final intended negotiation terms from a natural-language message. "
        + "Return JSON only with keys price, paymentDays, deliveryDays, contractMonths, selectedCounterOfferIndex, agreesToBuyerTerms, supplierConstraints. "
        + "Use referenceTerms as the default supplier baseline when no buyer option is selected. "
        + "counterOffers contains the latest buyer options, where counterOffers[0] is option 1. "
        + "Infer intent from meaning, not exact keywords. Suppliers may agree with varied wording such as saying a proposal works, choosing an option indirectly, referring to the faster-delivery option, the longer-payment option, the original buyer option, the same terms, or saying they are ready to proceed. "
        + "If the supplier is clearly choosing one buyer option, set selectedCounterOfferIndex to the 1-based option number and use that option as the semantic base even if some fields are omitted. "
        + "If the supplier is clearly agreeing to the buyer's terms and there is only one buyer option, set agreesToBuyerTerms to true. "
        + "If the supplier states a hard limit, include it inside supplierConstraints. Examples: lowest price 110 means supplierConstraints.priceFloor=110; maximum payment 45 days means supplierConstraints.paymentDaysCeiling=45; fastest delivery 20 days means supplierConstraints.deliveryDaysFloor=20; minimum contract 12 months means supplierConstraints.contractMonthsFloor=12. "
        + "If the supplier is making a fresh counteroffer instead of accepting, leave selectedCounterOfferIndex null and agreesToBuyerTerms false. "
        + "Apply relative adjustments such as 5 euro higher or 10 days faster. Do not include markdown fences.";
    private static final Pattern OPTION_PATTERN = Pattern.compile("\\b(?:option|offer)\\s*(\\d+|one|two|three|first|second|third)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORIGINAL_OPTION_PATTERN = Pattern.compile("\\b(original|first)\\s+(?:option|offer)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACCEPT_PATTERN = Pattern.compile("\\b(accept|accepted|agree|agreed|works for us|deal|ok|okay|confirmed)\\b", Pattern.CASE_INSENSITIVE);

    private final AiGatewayService aiGatewayService;
    private final ObjectMapper objectMapper;

    public AIController(
        AiGatewayService aiGatewayService,
        ObjectMapper objectMapper
    ) {
        this.aiGatewayService = aiGatewayService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(path = "/ai/parse-offer", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ParseOfferResponse parseOffer(
        @RequestBody ParseOfferRequest request
    ) {
        if (request == null || request.supplierMessage() == null || request.supplierMessage().isBlank()) {
            throw new IllegalArgumentException("supplierMessage is required.");
        }

        try {
            String content = aiGatewayService.complete(
                OFFER_PARSE_PROMPT,
                objectMapper.writeValueAsString(request));

            JsonNode jsonNode = extractJson(content);
            OfferSnapshot baseOffer = resolveBaseOffer(request, jsonNode);
            return mergeWithBase(baseOffer, new ParseOfferResponse(
                readDecimal(jsonNode, "price"),
                readInteger(jsonNode, "paymentDays"),
                readInteger(jsonNode, "deliveryDays"),
                readInteger(jsonNode, "contractMonths"),
                readSupplierConstraints(jsonNode)));
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "AI parsing returned invalid JSON for the supplier message.",
                exception);
        } catch (Exception exception) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Supplier message parsing requires a configured and reachable AI model.",
                exception);
        }
    }

    private JsonNode extractJson(String content) throws JsonProcessingException {
        if (content == null) {
            throw new IllegalArgumentException("AI parser returned no content.");
        }

        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');

        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("AI parser did not return JSON.");
        }

        return objectMapper.readTree(content.substring(start, end + 1));
    }

    private Double readDecimal(JsonNode jsonNode, String fieldName) {
        JsonNode value = jsonNode.get(fieldName);
        return value == null || value.isNull() ? null : value.asDouble();
    }

    private Integer readInteger(JsonNode jsonNode, String fieldName) {
        JsonNode value = jsonNode.get(fieldName);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private ParseOfferResponse mergeWithBase(OfferSnapshot baseOffer, ParseOfferResponse parsed) {
        return new ParseOfferResponse(
            parsed.price() == null ? baseOffer.price() : parsed.price(),
            parsed.paymentDays() == null ? baseOffer.paymentDays() : parsed.paymentDays(),
            parsed.deliveryDays() == null ? baseOffer.deliveryDays() : parsed.deliveryDays(),
            parsed.contractMonths() == null ? baseOffer.contractMonths() : parsed.contractMonths(),
            parsed.supplierConstraints());
    }

    private SupplierConstraintsResponse readSupplierConstraints(JsonNode jsonNode) {
        JsonNode constraints = jsonNode.get("supplierConstraints");
        if (constraints == null || constraints.isNull()) {
            return null;
        }

        SupplierConstraintsResponse response = new SupplierConstraintsResponse(
            readDecimal(constraints, "priceFloor"),
            readInteger(constraints, "paymentDaysCeiling"),
            readInteger(constraints, "deliveryDaysFloor"),
            readInteger(constraints, "contractMonthsFloor"));

        return response.isEmpty() ? null : response;
    }

    private OfferSnapshot resolveBaseOffer(ParseOfferRequest request, JsonNode aiResponse) {
        int aiSelectedOptionIndex = selectedOptionIndex(aiResponse);
        if (aiSelectedOptionIndex >= 0) {
            return selectedCounterOffer(request, aiSelectedOptionIndex);
        }

        if (agreesToBuyerTerms(aiResponse) && counterOfferCount(request) == 1) {
            return selectedCounterOffer(request, 0);
        }

        return resolveHeuristicBaseOffer(request);
    }

    private OfferSnapshot resolveHeuristicBaseOffer(ParseOfferRequest request) {
        JsonNode referenceTerms = objectMapper.valueToTree(request.referenceTerms());
        JsonNode counterOffers = objectMapper.valueToTree(request.counterOffers());
        int optionIndex = selectedOptionIndex(request.supplierMessage());

        if (optionIndex >= 0 && counterOffers.isArray() && optionIndex < counterOffers.size()) {
            return offerSnapshot(counterOffers.get(optionIndex), referenceTerms);
        }

        if (request.supplierMessage() != null
            && ACCEPT_PATTERN.matcher(request.supplierMessage()).find()
            && counterOffers.isArray()
            && counterOffers.size() == 1) {
            return offerSnapshot(counterOffers.get(0), referenceTerms);
        }

        return offerSnapshot(referenceTerms, referenceTerms);
    }

    private OfferSnapshot selectedCounterOffer(ParseOfferRequest request, int optionIndex) {
        JsonNode referenceTerms = objectMapper.valueToTree(request.referenceTerms());
        JsonNode counterOffers = objectMapper.valueToTree(request.counterOffers());

        if (optionIndex >= 0 && counterOffers.isArray() && optionIndex < counterOffers.size()) {
            return offerSnapshot(counterOffers.get(optionIndex), referenceTerms);
        }

        return offerSnapshot(referenceTerms, referenceTerms);
    }

    private int selectedOptionIndex(String supplierMessage) {
        if (supplierMessage == null) {
            return -1;
        }

        if (ORIGINAL_OPTION_PATTERN.matcher(supplierMessage).find()) {
            return 0;
        }

        Matcher matcher = OPTION_PATTERN.matcher(supplierMessage);
        if (!matcher.find()) {
            return -1;
        }

        return optionNumber(matcher.group(1));
    }

    private int selectedOptionIndex(JsonNode aiResponse) {
        Integer selectedCounterOfferIndex = readInteger(aiResponse, "selectedCounterOfferIndex");
        if (selectedCounterOfferIndex == null) {
            return -1;
        }

        return Math.max(-1, selectedCounterOfferIndex - 1);
    }

    private boolean agreesToBuyerTerms(JsonNode aiResponse) {
        JsonNode value = aiResponse.get("agreesToBuyerTerms");
        return value != null && !value.isNull() && value.asBoolean(false);
    }

    private int counterOfferCount(ParseOfferRequest request) {
        JsonNode counterOffers = objectMapper.valueToTree(request.counterOffers());
        return counterOffers.isArray() ? counterOffers.size() : 0;
    }

    private int optionNumber(String token) {
        if (token == null) {
            return -1;
        }

        String normalized = token.toLowerCase();
        return switch (normalized) {
            case "1", "one", "first" -> 0;
            case "2", "two", "second" -> 1;
            case "3", "three", "third" -> 2;
            default -> -1;
        };
    }

    private OfferSnapshot offerSnapshot(JsonNode candidate, JsonNode fallback) {
        JsonNode node = candidate == null || candidate.isMissingNode() || candidate.isNull() ? fallback : candidate;
        return new OfferSnapshot(
            numeric(node, fallback, "price"),
            wholeNumber(node, fallback, "paymentDays"),
            wholeNumber(node, fallback, "deliveryDays"),
            wholeNumber(node, fallback, "contractMonths"));
    }

    private Double numeric(JsonNode candidate, JsonNode fallback, String fieldName) {
        JsonNode value = candidate == null ? null : candidate.get(fieldName);
        if (value != null && value.isNumber()) {
            return value.asDouble();
        }

        JsonNode fallbackValue = fallback == null ? null : fallback.get(fieldName);
        return fallbackValue != null && fallbackValue.isNumber() ? fallbackValue.asDouble() : null;
    }

    private Integer wholeNumber(JsonNode candidate, JsonNode fallback, String fieldName) {
        JsonNode value = candidate == null ? null : candidate.get(fieldName);
        if (value != null && value.isNumber()) {
            return value.asInt();
        }

        JsonNode fallbackValue = fallback == null ? null : fallback.get(fieldName);
        return fallbackValue != null && fallbackValue.isNumber() ? fallbackValue.asInt() : null;
    }

    public record ParseOfferRequest(
            String supplierMessage,
            Object referenceTerms,
            Object counterOffers
    ) {
    }

    public record ParseOfferResponse(
            Double price,
            Integer paymentDays,
            Integer deliveryDays,
			Integer contractMonths,
			SupplierConstraintsResponse supplierConstraints
    ) {
    }

    public record SupplierConstraintsResponse(
        Double priceFloor,
        Integer paymentDaysCeiling,
        Integer deliveryDaysFloor,
        Integer contractMonthsFloor
    ) {
        boolean isEmpty() {
            return priceFloor == null
                && paymentDaysCeiling == null
                && deliveryDaysFloor == null
                && contractMonthsFloor == null;
        }
    }

    private record OfferSnapshot(
        Double price,
        Integer paymentDays,
        Integer deliveryDays,
        Integer contractMonths
    ) {
    }
}
