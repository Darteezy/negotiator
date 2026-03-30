package org.GLM.negoriator.controller;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.GLM.negoriator.ai.AiGatewayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
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

    private static final Logger log = LoggerFactory.getLogger(AIController.class);
    private static final Resource OFFER_PARSE_PROMPT_TEMPLATE = new ClassPathResource("prompts/ai/parse-offer-system.st");
    private static final Pattern OPTION_PATTERN = Pattern.compile("\\b(?:option|offer)\\s*(\\d+|one|two|three|first|second|third)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORIGINAL_OPTION_PATTERN = Pattern.compile("\\b(original|first)\\s+(?:option|offer)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACCEPT_PATTERN = Pattern.compile("\\b(accept|accepted|agree|agreed|works for us|deal|ok|okay|confirmed)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRICE_FLOOR_PATTERN = Pattern.compile(
        "\\b(lowest|minimum|min|floor|cannot go below|can't go below|no lower than|not below|bottom price|final price|best price)\\b",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern PAYMENT_CEILING_PATTERN = Pattern.compile(
        "\\b(max(?:imum)?|no more than|not more than|cannot do more than|can't do more than|up to)\\b.*\\bpayment|\\bpayment\\b.*\\b(max(?:imum)?|no more than|not more than|cannot do more than|can't do more than|up to)\\b",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern DELIVERY_FLOOR_PATTERN = Pattern.compile(
        "\\b(fastest|earliest|soonest|min(?:imum)?|at least|no earlier than|cannot deliver before|can't deliver before)\\b.*\\b(delivery|deliver)|\\b(delivery|deliver)\\b.*\\b(fastest|earliest|soonest|min(?:imum)?|at least|no earlier than|cannot deliver before|can't deliver before)\\b",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTRACT_FLOOR_PATTERN = Pattern.compile(
        "\\b(min(?:imum)?|at least|no shorter than|cannot do shorter|can't do shorter)\\b.*\\b(contract|month)|\\b(contract|month)\\b.*\\b(min(?:imum)?|at least|no shorter than|cannot do shorter|can't do shorter)\\b",
        Pattern.CASE_INSENSITIVE);

    private final AiGatewayService aiGatewayService;
    private final BeanOutputConverter<AiParseOfferResult> outputConverter;
    private final ObjectMapper objectMapper;

    public AIController(
        AiGatewayService aiGatewayService,
        ObjectMapper objectMapper
    ) {
        this.aiGatewayService = aiGatewayService;
        this.outputConverter = new BeanOutputConverter<>(AiParseOfferResult.class, objectMapper);
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
            AiParseOfferResult aiResult = aiGatewayService.completeStructured(
                renderPrompt(OFFER_PARSE_PROMPT_TEMPLATE),
                objectMapper.writeValueAsString(request),
                outputConverter);

            OfferSnapshot baseOffer = resolveBaseOffer(request, aiResult);
            return applySupplierConstraints(mergeWithBase(baseOffer, new ParseOfferResponse(
                aiResult.price(),
                aiResult.paymentDays(),
                aiResult.deliveryDays(),
                aiResult.contractMonths(),
                normalizeSupplierConstraints(request.supplierMessage(), aiResult.supplierConstraints()))));
        } catch (Exception exception) {
            log.warn("AI parse-offer request failed: {}", exception.getMessage());
            log.debug("AI parse-offer stack trace", exception);
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Supplier message parsing requires a configured and reachable AI model.",
                exception);
        }
    }

    private String renderPrompt(Resource resource) {
        return new PromptTemplate(resource).render();
    }

    private ParseOfferResponse mergeWithBase(OfferSnapshot baseOffer, ParseOfferResponse parsed) {
        return new ParseOfferResponse(
            parsed.price() == null ? baseOffer.price() : parsed.price(),
            parsed.paymentDays() == null ? baseOffer.paymentDays() : parsed.paymentDays(),
            parsed.deliveryDays() == null ? baseOffer.deliveryDays() : parsed.deliveryDays(),
            parsed.contractMonths() == null ? baseOffer.contractMonths() : parsed.contractMonths(),
            parsed.supplierConstraints());
    }

    private ParseOfferResponse applySupplierConstraints(ParseOfferResponse parsed) {
        SupplierConstraintsResponse constraints = parsed.supplierConstraints();
        if (constraints == null) {
            return parsed;
        }

        Double price = parsed.price();
        Integer paymentDays = parsed.paymentDays();
        Integer deliveryDays = parsed.deliveryDays();
        Integer contractMonths = parsed.contractMonths();

        if (price != null && constraints.priceFloor() != null) {
            price = Math.max(price, constraints.priceFloor());
        }
        if (paymentDays != null && constraints.paymentDaysCeiling() != null) {
            paymentDays = Math.min(paymentDays, constraints.paymentDaysCeiling());
        }
        if (deliveryDays != null && constraints.deliveryDaysFloor() != null) {
            deliveryDays = Math.max(deliveryDays, constraints.deliveryDaysFloor());
        }
        if (contractMonths != null && constraints.contractMonthsFloor() != null) {
            contractMonths = Math.max(contractMonths, constraints.contractMonthsFloor());
        }

        return new ParseOfferResponse(
            price,
            paymentDays,
            deliveryDays,
            contractMonths,
            constraints);
    }

    private SupplierConstraintsResponse normalizeSupplierConstraints(
        String supplierMessage,
        SupplierConstraintsResponse constraints
    ) {
        if (constraints == null || constraints.isEmpty()) {
            return null;
        }

        String message = supplierMessage == null ? "" : supplierMessage;
        SupplierConstraintsResponse filtered = new SupplierConstraintsResponse(
            PRICE_FLOOR_PATTERN.matcher(message).find() ? constraints.priceFloor() : null,
            PAYMENT_CEILING_PATTERN.matcher(message).find() ? constraints.paymentDaysCeiling() : null,
            DELIVERY_FLOOR_PATTERN.matcher(message).find() ? constraints.deliveryDaysFloor() : null,
            CONTRACT_FLOOR_PATTERN.matcher(message).find() ? constraints.contractMonthsFloor() : null);

        return filtered.isEmpty() ? null : filtered;
    }

    private OfferSnapshot resolveBaseOffer(ParseOfferRequest request, AiParseOfferResult aiResponse) {
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

    private int selectedOptionIndex(AiParseOfferResult aiResponse) {
        Integer selectedCounterOfferIndex = aiResponse.selectedCounterOfferIndex();
        if (selectedCounterOfferIndex == null) {
            return -1;
        }

        return Math.max(-1, selectedCounterOfferIndex - 1);
    }

    private boolean agreesToBuyerTerms(AiParseOfferResult aiResponse) {
        return Boolean.TRUE.equals(aiResponse.agreesToBuyerTerms());
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

    private record AiParseOfferResult(
        Double price,
        Integer paymentDays,
        Integer deliveryDays,
        Integer contractMonths,
        Integer selectedCounterOfferIndex,
        Boolean agreesToBuyerTerms,
        SupplierConstraintsResponse supplierConstraints
    ) {
    }
}
