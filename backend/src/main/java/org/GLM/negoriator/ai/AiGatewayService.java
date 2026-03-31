package org.GLM.negoriator.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AiGatewayService {

	private final AiProvider provider;
	private final ChatClient chatClient;
	private final String model;

	@Autowired
	public AiGatewayService(
		ChatClient aiChatClient,
		AiProperties properties
	) {
		this(AiProvider.from(properties.getProvider()), properties.getChatModel(), aiChatClient);
	}

	protected AiGatewayService(String provider, String model, ChatClient chatClient) {
		this(AiProvider.from(provider), model, chatClient);
	}

	private AiGatewayService(AiProvider provider, String model, ChatClient chatClient) {
		this.provider = provider;
		this.model = model;
		this.chatClient = chatClient;
	}

	public String complete(String systemPrompt, String userPrompt) {
		String content = chatClient.prompt()
			.system(systemPrompt)
			.user(userPrompt)
			.call()
			.content();

		if (!StringUtils.hasText(content)) {
			throw new IllegalArgumentException("AI provider returned an empty response.");
		}

		return content;
	}

	public <T> T completeStructured(
		String systemPrompt,
		String userPrompt,
		StructuredOutputConverter<T> outputConverter
	) {
		String effectiveUserPrompt = userPrompt + "\n\n" + outputConverter.getFormat();
		ChatClient.ChatClientRequestSpec request = chatClient.prompt()
			.system(systemPrompt)
			.user(effectiveUserPrompt);

		if (outputConverter instanceof BeanOutputConverter<?> beanOutputConverter) {
			request = request.options(structuredOptions(beanOutputConverter.getJsonSchema()));
		}

		return request.call().entity(outputConverter);
	}

	private org.springframework.ai.chat.prompt.ChatOptions structuredOptions(String jsonSchema) {
		return switch (provider) {
			case OLLAMA -> OllamaChatOptions.builder().model(model).outputSchema(jsonSchema).build();
			case OPENAI_COMPATIBLE -> OpenAiChatOptions.builder().model(model).outputSchema(jsonSchema).build();
		};
	}

	enum AiProvider {
		OLLAMA,
		OPENAI_COMPATIBLE;

		static AiProvider from(String value) {
			return switch (value == null ? "" : value.trim().toLowerCase()) {
				case "openai-compatible" -> OPENAI_COMPATIBLE;
				case "ollama", "" -> OLLAMA;
				default -> throw new IllegalArgumentException("Unsupported AI_PROVIDER: " + value);
			};
		}
	}
}
