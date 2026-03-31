package org.GLM.negoriator.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NegotiationDefaultsProperties.class)
class NegotiationDefaultsConfiguration {

	NegotiationDefaultsConfiguration(NegotiationDefaultsProperties properties) {
		NegotiationDefaults.configure(properties);
	}
}
