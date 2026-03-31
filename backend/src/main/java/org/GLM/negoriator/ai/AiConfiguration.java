package org.GLM.negoriator.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
class AiConfiguration {

	@Bean
	ChatModel aiChatModel(AiProperties properties) {
		AiGatewayService.AiProvider provider = AiGatewayService.AiProvider.from(properties.getProvider());
		return switch (provider) {
			case OLLAMA -> OllamaChatModel.builder()
				.ollamaApi(OllamaApi.builder().baseUrl(trimTrailingSlash(properties.getBaseUrl())).build())
				.defaultOptions(OllamaChatOptions.builder().model(properties.getChatModel()).build())
				.build();
			case OPENAI_COMPATIBLE -> OpenAiChatModel.builder()
				.openAiApi(OpenAiApi.builder()
					.baseUrl(normalizeOpenAiBaseUrl(properties.getBaseUrl()))
					.apiKey(defaultString(properties.getApiKey()))
					.build())
				.defaultOptions(OpenAiChatOptions.builder().model(properties.getChatModel()).build())
				.build();
		};
	}

	@Bean
	ChatClient aiChatClient(ChatModel aiChatModel) {
		return ChatClient.builder(aiChatModel).build();
	}

	private String normalizeOpenAiBaseUrl(String value) {
		String normalized = trimTrailingSlash(value);
		if (StringUtils.hasText(normalized) && normalized.endsWith("/v1")) {
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

	private String defaultString(String value) {
		return value == null ? "" : value;
	}
}
