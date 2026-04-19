package org.keinus.logparser.domain.output.model;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.keinus.logparser.domain.model.LogEvent;
import org.keinus.logparser.domain.output.model.OutputAdapter;


/**
 * 처리된 메시지를 Apache Kafka 토픽으로 전송하는 출력 어댑터입니다.
 * <p>
 * 이 클래스는 {@link OutputAdapter}를 구현하며, 내부적으로 {@link KafkaProducer}를 사용하여
 * 메시지를 비동기적으로 Kafka 브로커에 전송합니다.
 * <p>
 * 주요 설정:
 * <ul>
 *     <li>{@code bootstrapservers}: 접속할 Kafka 브로커의 주소 목록</li>
 *     <li>{@code topicid}: 메시지를 전송할 대상 토픽의 이름</li>
 * </ul>
 * 메시지의 키는 null이며, 값은 JSON 형식의 문자열입니다.
 *
 * @see org.keinus.logparser.core.interfaces.OutputAdapter
 * @see org.apache.kafka.clients.producer.KafkaProducer
 */
public class KafkaOutputAdapter extends OutputAdapter {
	private static final int DEFAULT_RETRY_COUNT = 0;
	private static final int DEFAULT_RETRY_DELAY_MS = 250;
	private static final int DEFAULT_TIMEOUT_MS = 30_000;
	private static final long DEFAULT_BUFFER_MEMORY_BYTES = 8L * 1024L * 1024L;
	private static final Logger LOGGER = LoggerFactory.getLogger( KafkaOutputAdapter.class );
	Producer<String, String> producer = null;
	String topic = "";
	String key = null;
	private final AtomicBoolean closed = new AtomicBoolean(false);
	
	public KafkaOutputAdapter(Map<String, String> obj) throws IOException {
		this(obj, null);
	}

	KafkaOutputAdapter(Map<String, String> obj, Producer<String, String> producer) throws IOException {
		super(obj);

		topic = obj.get("topicid");
		key = obj.get("key");
		String server = obj.get("bootstrapservers");

		if (producer != null) {
			this.producer = producer;
			LOGGER.info("Kafka Output Adapter initialized with injected producer for topic={}", topic);
			return;
		}

		Properties props = buildProducerProperties(obj);
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, server);
		this.producer = new KafkaProducer<>(props);

		LOGGER.info("Kafka Output Adapter connected to {} with bounded retry settings (topic={}, timeoutMs={})",
				server, topic);
	}

	@Override
	public void send(LogEvent logEvent) {
		String jsonString = serializeEvent(logEvent);
		ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, jsonString);

		try {
			producer.send(record).get(getTimeoutMs(), TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw deliveryFailure("Kafka send interrupted", e);
		} catch (ExecutionException | TimeoutException e) {
			throw deliveryFailure("Failed to send message to Kafka topic " + topic, e);
		}
	}

	static Properties buildProducerProperties(Map<String, String> obj) {
		int timeoutMs = parsePositiveInt(obj.get("timeoutMs"), DEFAULT_TIMEOUT_MS);
		int retryCount = parseNonNegativeInt(obj.get("retryCount"), DEFAULT_RETRY_COUNT);
		int retryDelayMs = parsePositiveInt(obj.get("retryDelayMs"), DEFAULT_RETRY_DELAY_MS);
		int deliveryTimeoutMs = timeoutMs + (retryCount * retryDelayMs) + 1_000;

		Properties props = new Properties();
		props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		props.put(ProducerConfig.ACKS_CONFIG, "all");
		props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "false");
		props.put(ProducerConfig.RETRIES_CONFIG, String.valueOf(retryCount));
		props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, String.valueOf(retryDelayMs));
		props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, String.valueOf(timeoutMs));
		props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, String.valueOf(deliveryTimeoutMs));
		props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, String.valueOf(timeoutMs));
		props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, String.valueOf(DEFAULT_BUFFER_MEMORY_BYTES));
		props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
		return props;
	}

	private static int parsePositiveInt(String value, int defaultValue) {
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		try {
			int parsed = Integer.parseInt(value);
			return parsed > 0 ? parsed : defaultValue;
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	private static int parseNonNegativeInt(String value, int defaultValue) {
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		try {
			int parsed = Integer.parseInt(value);
			return Math.max(parsed, 0);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	@Override
	public void close() throws IOException {
		// 멱등성 보장: 이미 닫혔으면 즉시 리턴
		if (!closed.compareAndSet(false, true)) {
			LOGGER.debug("Kafka Output Adapter already closed, skipping");
			return;
		}

		if (producer != null) {
			try {
				producer.close(java.time.Duration.ofMillis(getTimeoutMs()));
				LOGGER.info("Kafka producer closed successfully");
			} catch (Exception e) {
				LOGGER.error("Error closing Kafka producer: {}", e.getMessage(), e);
				throw new IOException("Failed to close Kafka producer", e);
			}
		}
	}
}
