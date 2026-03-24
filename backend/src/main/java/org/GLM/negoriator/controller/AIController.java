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

    private static final String OFFER_PARSE_PROMPT = "You extract final negotiation terms from supplier messages. Return JSON only with keys price, paymentDays, deliveryDays, contractMonths. Use referenceTerms as defaults. If the supplier says option N, use counterOffers[N-1] as the base. Apply relative adjustments such as 5 euro higher. Do not include markdown fences.";
    private static final Pattern OPTION_PATTERN = Pattern.compile("\\b(?:option|offer)\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE);
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

        OfferSnapshot baseOffer = resolveBaseOffer(request);

        try {
            String content = aiGatewayService.complete(
                OFFER_PARSE_PROMPT,
                objectMapper.writeValueAsString(request));

            JsonNode jsonNode = extractJson(content);
            return mergeWithBase(baseOffer, new ParseOfferResponse(
                readDecimal(jsonNode, "price"),
                readInteger(jsonNode, "paymentDays"),
                readInteger(jsonNode, "deliveryDays"),
                readInteger(jsonNode, "contractMonths")));
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
            parsed.contractMonths() == null ? baseOffer.contractMonths() : parsed.contractMonths());
    }

    private OfferSnapshot resolveBaseOffer(ParseOfferRequest request) {
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

    private int selectedOptionIndex(String supplierMessage) {
        if (supplierMessage == null) {
            return -1;
        }

        Matcher matcher = OPTION_PATTERN.matcher(supplierMessage);
        if (!matcher.find()) {
            return -1;
        }

        return Math.max(-1, Integer.parseInt(matcher.group(1)) - 1);
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
            Integer contractMonths
    ) {
    }

    private record OfferSnapshot(
        Double price,
        Integer paymentDays,
        Integer deliveryDays,
        Integer contractMonths
    ) {
    }
}
