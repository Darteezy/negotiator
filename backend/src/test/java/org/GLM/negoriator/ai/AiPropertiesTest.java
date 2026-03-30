package org.GLM.negoriator.ai;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AiPropertiesTest {

	@Test
	void providerParserAcceptsOpenAiCompatible() {
		assertEquals(
			AiGatewayService.AiProvider.OPENAI_COMPATIBLE,
			AiGatewayService.AiProvider.from("openai-compatible"));
	}

	@Test
	void providerParserRejectsLegacyOpenAiAlias() {
		IllegalArgumentException exception = assertThrows(
			IllegalArgumentException.class,
			() -> AiGatewayService.AiProvider.from("openai"));

		assertEquals("Unsupported AI_PROVIDER: openai", exception.getMessage());
	}

	@Test
	void defaultsMatchDocumentedOllamaSetup() {
		AiProperties properties = new AiProperties();

		assertEquals("ollama", properties.getProvider());
		assertEquals("http://localhost:11434", properties.getBaseUrl());
		assertEquals("qwen3.5:9b", properties.getChatModel());
	}

	@Test
	void validationRequiresApiKeyForOpenAiCompatible() {
		AiProperties properties = new AiProperties();
		properties.setProvider("openai-compatible");
		properties.setApiKey("");

		IllegalStateException exception = assertThrows(IllegalStateException.class, properties::validate);

		assertEquals(
			"AI_API_KEY must be configured for AI_PROVIDER=openai-compatible.",
			exception.getMessage());
	}

	@Test
	void validationAllowsOpenAiCompatibleWhenRequiredFieldsArePresent() {
		AiProperties properties = new AiProperties();
		properties.setProvider("openai-compatible");
		properties.setBaseUrl("https://api.openai.com");
		properties.setApiKey("test-key");
		properties.setChatModel("gpt-4.1-mini");

		assertDoesNotThrow(properties::validate);
	}

	@Test
	void validationAllowsOllamaWithoutApiKey() {
		AiProperties properties = new AiProperties();

		assertDoesNotThrow(properties::validate);
	}

	@Test
	void validationRequiresChatModel() {
		AiProperties properties = new AiProperties();
		properties.setChatModel("   ");

		IllegalStateException exception = assertThrows(IllegalStateException.class, properties::validate);

		assertEquals("AI_CHAT_MODEL must be configured.", exception.getMessage());
	}

	@Test
	void validationRequiresBaseUrl() {
		AiProperties properties = new AiProperties();
		properties.setBaseUrl("  ");

		IllegalStateException exception = assertThrows(IllegalStateException.class, properties::validate);

		assertEquals("AI_BASE_URL must be configured.", exception.getMessage());
	}
}
