package org.GLM.negoriator.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AIController {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public AIController(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @GetMapping("/ai")
    String generation(@RequestParam String userInput) {
        return this.chatClient.prompt()
                .user(userInput)
                .call()
                .content();
    }

    @PostMapping(path = "/ai/parse-offer", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ParseOfferResponse parseOffer(@RequestBody ParseOfferRequest request) {
        if (request == null || request.supplierMessage() == null || request.supplierMessage().isBlank()) {
            throw new IllegalArgumentException("supplierMessage is required.");
        }

        try {
            String content = this.chatClient.prompt()
                    .system("You extract final negotiation terms from supplier messages. Return JSON only with keys price, paymentDays, deliveryDays, contractMonths. Use referenceTerms as defaults. If the supplier says option N, use counterOffers[N-1] as the base. Apply relative adjustments such as 5 euro higher. Do not include markdown fences.")
                    .user(objectMapper.writeValueAsString(request))
                    .call()
                    .content();

            JsonNode jsonNode = extractJson(content);
            return new ParseOfferResponse(
                    readDecimal(jsonNode, "price"),
                    readInteger(jsonNode, "paymentDays"),
                    readInteger(jsonNode, "deliveryDays"),
                    readInteger(jsonNode, "contractMonths"));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("AI parsing failed for supplier message.");
        }
    }

    private JsonNode extractJson(String content) throws JsonProcessingException {
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
}