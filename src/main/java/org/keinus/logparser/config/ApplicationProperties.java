package org.keinus.logparser.config;

import java.util.List;
import java.util.Map;

import org.keinus.logparser.util.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "logparser")
@PropertySource(value = "file:./config/config.yaml", factory = YamlPropertySourceFactory.class)
@Data
public class ApplicationProperties {
    private List<Map<String, String>> input;
	private List<Map<String, String>> output;
	private List<Map<String, String>> parser;
	private List<TransformConfig> transform;
	private int parserThreads;
}

