package com.anish.ib.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({RedditProperties.class, GroqProperties.class, GeminiProperties.class})
public class AppConfig {
}
