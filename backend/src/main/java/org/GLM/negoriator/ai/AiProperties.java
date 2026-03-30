package org.GLM.negoriator.ai;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "ai")
public class AiProperties {

	private String provider = "ollama";
	private String baseUrl = "http://localhost:11434";
	private String chatModel = "qwen3.5:9b";
	private String apiKey = "";

	@PostConstruct
	void validate() {
		AiGatewayService.AiProvider resolvedProvider = AiGatewayService.AiProvider.from(provider);

		if (!StringUtils.hasText(baseUrl)) {
			throw new IllegalStateException("AI_BASE_URL must be configured.");
		}

		if (!StringUtils.hasText(chatModel)) {
			throw new IllegalStateException("AI_CHAT_MODEL must be configured.");
		}

		if (resolvedProvider == AiGatewayService.AiProvider.OPENAI_COMPATIBLE
			&& !StringUtils.hasText(apiKey)) {
			throw new IllegalStateException("AI_API_KEY must be configured for AI_PROVIDER=openai-compatible.");
		}
	}

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getChatModel() {
		return chatModel;
	}

	public void setChatModel(String chatModel) {
		this.chatModel = chatModel;
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}
}
