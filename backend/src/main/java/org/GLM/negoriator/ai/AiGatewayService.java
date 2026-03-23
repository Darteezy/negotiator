package org.GLM.negoriator.ai;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Service
public class AiGatewayService {

	private final String apiKey;
	private final String baseUrl;
	private final String model;
	private final AiProvider provider;
	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	public AiGatewayService(
		RestClient.Builder restClientBuilder,
		ObjectMapper objectMapper,
		@Value("${AI_PROVIDER:ollama}") String provider,
		@Value("${AI_BASE_URL:http://localhost:11434}") String baseUrl,
		@Value("${AI_CHAT_MODEL:qwen2.5:7b-instruct}") String model,
		@Value("${AI_CONNECT_TIMEOUT_MS:5000}") int connectTimeoutMs,
		@Value("${AI_READ_TIMEOUT_MS:45000}") int readTimeoutMs,
		@Value("${AI_API_KEY:}") String apiKey
	) {
		this.provider = AiProvider.from(provider);
		this.baseUrl = trimTrailingSlash(baseUrl);
		this.model = model;
		this.apiKey = apiKey;
		this.objectMapper = objectMapper;
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
		requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
		this.restClient = restClientBuilder
			.baseUrl(this.baseUrl)
			.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
			.requestFactory(requestFactory)
			.build();
	}

	public String complete(String systemPrompt, String userPrompt) {
		return switch (provider) {
			case OLLAMA -> completeWithOllama(systemPrompt, userPrompt);
			case OPENAI -> completeWithOpenAi(systemPrompt, userPrompt);
		};
	}

	private String completeWithOllama(String systemPrompt, String userPrompt) {
		String responseBody = restClient.post()
			.uri("/api/chat")
			.body(new OllamaChatRequest(
				model,
				List.of(
					new ChatMessage("system", systemPrompt),
					new ChatMessage("user", userPrompt)
				),
				false))
			.retrieve()
			.body(String.class);

		if (!StringUtils.hasText(responseBody)) {
			throw new IllegalArgumentException("AI provider returned an empty response.");
		}

		OllamaChatResponse response;
		try {
			response = objectMapper.readValue(responseBody, OllamaChatResponse.class);
		} catch (Exception e) {
			throw new IllegalArgumentException("Failed to parse Ollama response: " + responseBody, e);
		}

		if (response == null || response.message() == null || !StringUtils.hasText(response.message().content())) {
			throw new IllegalArgumentException("AI provider returned an empty response.");
		}

		return response.message().content();
	}

	private String completeWithOpenAi(String systemPrompt, String userPrompt) {
		OpenAiChatResponse response = restClient.post()
			.uri("/chat/completions")
			.headers(headers -> {
				if (StringUtils.hasText(apiKey)) {
					headers.setBearerAuth(apiKey);
				}
			})
			.body(new OpenAiChatRequest(
				model,
				List.of(
					new ChatMessage("system", systemPrompt),
					new ChatMessage("user", userPrompt)
				)))
			.retrieve()
			.body(OpenAiChatResponse.class);

		if (response == null || response.choices() == null || response.choices().isEmpty()) {
			throw new IllegalArgumentException("AI provider returned an empty response.");
		}

		ChatMessage message = response.choices().getFirst().message();
		if (message == null || !StringUtils.hasText(message.content())) {
			throw new IllegalArgumentException("AI provider returned an empty response.");
		}

		return message.content();
	}

	private String trimTrailingSlash(String value) {
		if (!StringUtils.hasText(value)) {
			return value;
		}

		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}

	enum AiProvider {
		OLLAMA,
		OPENAI;

		static AiProvider from(String value) {
			return switch (value == null ? "" : value.trim().toLowerCase()) {
				case "openai" -> OPENAI;
				case "ollama", "" -> OLLAMA;
				default -> throw new IllegalArgumentException("Unsupported AI_PROVIDER: " + value);
			};
		}
	}

	record ChatMessage(String role, String content) {
	}

	record OllamaChatRequest(String model, List<ChatMessage> messages, boolean stream) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record OllamaChatResponse(ChatMessage message) {
	}

	record OpenAiChatRequest(String model, List<ChatMessage> messages) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record OpenAiChatResponse(List<OpenAiChoice> choices) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record OpenAiChoice(@JsonProperty("message") ChatMessage message) {
	}
}