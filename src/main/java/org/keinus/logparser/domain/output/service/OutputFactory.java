package org.keinus.logparser.domain.output.service;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.HashMap;
import org.keinus.logparser.domain.configuration.model.OutputAdapterConfig;
import org.keinus.logparser.domain.output.model.OutputAdapter;

import lombok.extern.slf4j.Slf4j;


/**
 * 설정에 따라 적절한 {@link OutputAdapter} 구현체를 동적으로 생성하는 팩토리 클래스입니다.
 * <p>
 * 이 클래스는 'type' 문자열(예: "ConsoleOutputAdapter", "KafkaOutputAdapter")을 기반으로
 * 리플렉션을 사용하여 해당 클래스의 인스턴스를 생성합니다. 이를 통해 코드를 변경하지 않고도
 * 설정 파일 수정을 통해 새로운 출력 어댑터를 추가하거나 변경할 수 있습니다.
 * <p>
 * 모든 {@code OutputAdapter} 구현체는 {@code org.keinus.logparser.output} 패키지 내에 위치해야 하며,
 * {@code Map<String, String>}을 인자로 받는 생성자를 가져야 합니다.
 *
 * @see org.keinus.logparser.core.interfaces.OutputAdapter
 * @see org.keinus.logparser.components.OutputAdaptorComponent
 */
@Slf4j
public class OutputFactory {
	private OutputFactory() {
		throw new IllegalStateException("Utility class");
	}

	public static OutputAdapter getOutputAdapter(OutputAdapterConfig config) {
		String type = config.getType();
		try {
			// OutputAdapterConfig를 Map으로 변환
			Map<String, String> param = convertConfigToMap(config);

			Class<?> cls = Class.forName("org.keinus.logparser.domain.delivery.model." + type);
			return (OutputAdapter) cls.getDeclaredConstructor(Map.class).newInstance(param);
		}
		catch (InstantiationException | IllegalAccessException | IllegalArgumentException
			| InvocationTargetException | NoSuchMethodException
			| SecurityException | ClassNotFoundException e) {
			log.error("Invalid Output Adapter. {}", e.getMessage());
			throw new IllegalStateException("Invalid Output Adapter.");
		}
	}

	private static Map<String, String> convertConfigToMap(OutputAdapterConfig config) {
		Map<String, String> param = new HashMap<>();

		if (config.getId() != null) param.put("id", String.valueOf(config.getId()));
		param.put("type", config.getType());
		if (config.getMessagetype() != null) param.put("messagetype", config.getMessagetype());
		if (config.getAddOriginText() != null) param.put("add_origin_text", String.valueOf(config.getAddOriginText()));

		// Network 관련
		if (config.getPort() != null) param.put("port", String.valueOf(config.getPort()));
		if (config.getHost() != null) param.put("host", config.getHost());

		// HTTP 관련
		if (config.getUrl() != null) param.put("url", config.getUrl());
		if (config.getMethod() != null) param.put("method", config.getMethod());

		// Kafka 관련
		if (config.getTopicid() != null) param.put("topicid", config.getTopicid());
		if (config.getBootstrapservers() != null) param.put("bootstrapservers", config.getBootstrapservers());
		if (config.getKey() != null) param.put("key", config.getKey());

		// OpenSearch 관련
		if (config.getIndex() != null) param.put("index", config.getIndex());
		if (config.getOsUsername() != null) param.put("username", config.getOsUsername());
		if (config.getOsPassword() != null) param.put("password", config.getOsPassword());
		if (config.getAction() != null) param.put("action", config.getAction());

		// RabbitMQ 관련
		if (config.getRoutingkey() != null) param.put("routingkey", config.getRoutingkey());
		if (config.getExchange() != null) param.put("exchange", config.getExchange());
		if (config.getRmqUsername() != null) param.put("username", config.getRmqUsername());
		if (config.getRmqPassword() != null) param.put("password", config.getRmqPassword());
		if (config.getRmqPort() != null) param.put("port", String.valueOf(config.getRmqPort()));

		// 성능 관련
		if (config.getBatchSize() != null) param.put("batchSize", String.valueOf(config.getBatchSize()));
		if (config.getFlushIntervalMs() != null) param.put("flushIntervalMs", String.valueOf(config.getFlushIntervalMs()));
		if (config.getRetryCount() != null) param.put("retryCount", String.valueOf(config.getRetryCount()));
		if (config.getRetryDelayMs() != null) param.put("retryDelayMs", String.valueOf(config.getRetryDelayMs()));

		// 공통 설정
		if (config.getEnabled() != null) param.put("enabled", String.valueOf(config.getEnabled()));
		if (config.getTimeoutMs() != null) param.put("timeoutMs", String.valueOf(config.getTimeoutMs()));

		return param;
	}
}
