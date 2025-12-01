package org.keinus.logparser.domain.delivery.model;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.keinus.logparser.domain.delivery.model.OutputAdapter;


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
	private static final Logger LOGGER = LoggerFactory.getLogger( KafkaOutputAdapter.class );
	Producer<String, String> producer = null;
	String topic = "";
	private final AtomicBoolean closed = new AtomicBoolean(false);
	
	public KafkaOutputAdapter(Map<String, String> obj) throws IOException {
		super(obj);

		topic = obj.get("topicid");
		String server = obj.get("bootstrapservers");

		Properties props = new Properties();
		props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, server);
		props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

		// Reliability settings to prevent message loss
		// acks=all: Wait for all in-sync replicas to acknowledge (strongest durability)
		props.put(ProducerConfig.ACKS_CONFIG, "all");

		// Idempotence: Ensures exactly-once delivery semantics within a partition
		props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");

		// Retries: Maximum number of retry attempts (idempotence requires retries > 0)
		props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);

		// max.in.flight.requests.per.connection: Limit to 5 for idempotence
		props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");

		// Request timeout: How long to wait for a request
		props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "30000");

		// Delivery timeout: Total time including retries (should be > request.timeout.ms)
		props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "120000");

		// Compression for better performance (optional but recommended)
		props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");

		producer = new KafkaProducer<>(props);

		LOGGER.info("Kafka Output Adapter connected to {} with reliability settings (acks=all, idempotence=true, topic={})",
				server, topic);
	}

	public void send(Map<String, Object> json, String jsonString) {
		// KafkaProducer는 이미 스레드 안전하므로 synchronized 불필요
		ProducerRecord<String, String> record = new ProducerRecord<>(topic, jsonString);

		producer.send(record, (metadata, exception) -> {
			if (exception != null) {
				LOGGER.error("Failed to send message to Kafka topic {}: {}",
					topic, exception.getMessage(), exception);
			} else {
				LOGGER.debug("Message sent successfully to topic {} partition {} offset {}",
					topic, metadata.partition(), metadata.offset());
			}
		});
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
				producer.close();
				LOGGER.info("Kafka producer closed successfully");
			} catch (Exception e) {
				LOGGER.error("Error closing Kafka producer: {}", e.getMessage(), e);
				throw new IOException("Failed to close Kafka producer", e);
			}
		}
	}
}
