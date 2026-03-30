package org.GLM.negoriator.ai;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
public class AiGatewayService {

	private final String baseUrl;
	private final AiProvider provider;
	private final ChatModel chatModel;
	private final String model;

	public AiGatewayService(
		RestClient.Builder restClientBuilder,
		ObjectMapper objectMapper,
		@Value("${AI_PROVIDER:ollama}") String provider,
		@Value("${AI_BASE_URL:http://localhost:11434}") String baseUrl,
		@Value("${AI_CHAT_MODEL:qwen2.5:7b-instruct}") String model,
		@Value("${AI_API_KEY:}") String apiKey
	) {
		this.provider = AiProvider.from(provider);
		this.baseUrl = normalizeBaseUrl(this.provider, baseUrl);
		this.model = model;
		this.chatModel = createChatModel(this.provider, this.baseUrl, model, apiKey);
	}

	public String complete(String systemPrompt, String userPrompt) {
		return call(prompt(systemPrompt, userPrompt));
	}

	public String completeJson(String systemPrompt, String userPrompt) {
		return switch (provider) {
			case OLLAMA -> call(prompt(systemPrompt, userPrompt,
				OllamaChatOptions.builder().format("json").build()));
			case OPENAI -> call(prompt(systemPrompt, userPrompt,
				OpenAiChatOptions.builder().model(model).build()));
		};
	}

	private ChatModel createChatModel(AiProvider provider, String baseUrl, String model, String apiKey) {
		return switch (provider) {
			case OLLAMA -> buildOllamaChatModel(baseUrl, model);
			case OPENAI -> buildOpenAiChatModel(baseUrl, model, apiKey);
		};
	}

	private ChatModel buildOllamaChatModel(String baseUrl, String model) {
		return OllamaChatModel.builder()
			.ollamaApi(OllamaApi.builder().baseUrl(baseUrl).build())
			.defaultOptions(OllamaChatOptions.builder().model(model).build())
			.build();
	}

	private ChatModel buildOpenAiChatModel(String baseUrl, String model, String apiKey) {
		String resolvedApiKey = StringUtils.hasText(apiKey) ? apiKey : "spring-ai-placeholder";
		return OpenAiChatModel.builder()
			.openAiApi(OpenAiApi.builder().baseUrl(baseUrl).apiKey(resolvedApiKey).build())
			.defaultOptions(OpenAiChatOptions.builder().model(model).build())
			.build();
	}

	private Prompt prompt(String systemPrompt, String userPrompt) {
		return new Prompt(List.of(
			new SystemMessage(systemPrompt),
			new UserMessage(userPrompt)));
	}

	private Prompt prompt(String systemPrompt, String userPrompt, Object options) {
		return new Prompt(
			List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt)),
			(org.springframework.ai.chat.prompt.ChatOptions) options);
	}

	private String call(Prompt prompt) {
		ChatResponse response = chatModel.call(prompt);
		if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
			throw new IllegalArgumentException("AI provider returned an empty response.");
		}

		String content = response.getResult().getOutput().getText();
		if (!StringUtils.hasText(content)) {
			throw new IllegalArgumentException("AI provider returned an empty response.");
		}

		return content;
	}

	private String normalizeBaseUrl(AiProvider provider, String value) {
		String normalized = trimTrailingSlash(value);
		if (provider == AiProvider.OPENAI && StringUtils.hasText(normalized) && normalized.endsWith("/v1")) {
			return normalized.substring(0, normalized.length() - 3);
		}
		return normalized;
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
}