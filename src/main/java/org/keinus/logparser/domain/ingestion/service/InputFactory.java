package org.keinus.logparser.domain.ingestion.service;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.HashMap;
import org.keinus.logparser.domain.configuration.model.InputAdapterConfig;
import org.keinus.logparser.domain.ingestion.model.InputAdapter;

import lombok.extern.slf4j.Slf4j;

/**
 * 설정에 따라 적절한 {@link InputAdapter} 구현체를 동적으로 생성하는 팩토리 클래스입니다.
 * <p>
 * 이 클래스는 'type' 문자열(예: "FileInputAdapter", "TcpInputAdapter")을 기반으로
 * 리플렉션을 사용하여 해당 클래스의 인스턴스를 생성합니다. 이를 통해 코드를 변경하지 않고도
 * 설정 파일 수정을 통해 새로운 입력 어댑터를 추가하거나 변경할 수 있습니다.
 * <p>
 * 모든 {@code InputAdapter} 구현체는 {@code org.keinus.logparser.input} 패키지 내에 위치해야 하며,
 * {@code Map<String, String>}을 인자로 받는 생성자를 가져야 합니다.
 *
 * @see org.keinus.logparser.core.interfaces.InputAdapter
 * @see org.keinus.logparser.components.InputAdaptorComponent
 */
@Slf4j
public class InputFactory {
	private InputFactory() {
		throw new IllegalStateException("Utility class");
	}

	public static InputAdapter getInputAdapter(InputAdapterConfig config) {
		String type = config.getType();
		try {
			// InputAdapterConfig를 Map으로 변환
			Map<String, String> param = convertConfigToMap(config);

			Class<?> cls = Class.forName("org.keinus.logparser.domain.ingestion.model." + type);
			return (InputAdapter) cls.getDeclaredConstructor(Map.class).newInstance(param);
		}
		catch (InstantiationException | IllegalAccessException | IllegalArgumentException
			| InvocationTargetException | NoSuchMethodException
			| SecurityException | ClassNotFoundException e) {
			log.error("Invalid Input Adapter. {}", e.getMessage());
			throw new IllegalStateException("Invalid Input Adapter.");
		}
	}

	private static Map<String, String> convertConfigToMap(InputAdapterConfig config) {
		Map<String, String> param = new HashMap<>();

		param.put("type", config.getType());
		param.put("messagetype", config.getMessagetype());

		if (config.getPort() != null) param.put("port", String.valueOf(config.getPort()));
		if (config.getHost() != null) param.put("host", config.getHost());
		if (config.getPath() != null) param.put("path", config.getPath());
		if (config.getIsFromBeginning() != null) param.put("isFromBeginning", String.valueOf(config.getIsFromBeginning()));
		if (config.getTopicid() != null) param.put("topicid", config.getTopicid());
		if (config.getBootstrapservers() != null) param.put("bootstrapservers", config.getBootstrapservers());
		if (config.getGroupId() != null) param.put("groupId", config.getGroupId());
		if (config.getCodec() != null) param.put("codec", config.getCodec());
		if (config.getPath_pattern() != null) param.put("path_pattern", config.getPath_pattern());
		if (config.getBufferSize() != null) param.put("bufferSize", String.valueOf(config.getBufferSize()));
		if (config.getTimeoutMs() != null) param.put("timeoutMs", String.valueOf(config.getTimeoutMs()));
		if (config.getEnabled() != null) param.put("enabled", String.valueOf(config.getEnabled()));
		if (config.getWorkerThreads() != null) param.put("workerThreads", String.valueOf(config.getWorkerThreads()));
		if (config.getQueueSize() != null) param.put("queueSize", String.valueOf(config.getQueueSize()));

		return param;
	}
}
