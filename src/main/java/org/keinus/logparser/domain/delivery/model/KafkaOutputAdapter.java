package org.keinus.logparser.domain.delivery.model;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

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
	
	public KafkaOutputAdapter(Map<String, String> obj) throws IOException {
		super(obj);

		topic = obj.get("topicid");
		String server = obj.get("bootstrapservers");
		
		Properties props = new Properties();
		props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, server);
		props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		producer = new KafkaProducer<>(props);
		
		LOGGER.info("Kafka Output Adapter connected to {}, {}", server, topic);
	}

	public void send(Map<String, Object> json, String jsonString) {
		try {
			synchronized( this ) {
				producer.send(new ProducerRecord<>(topic, jsonString));
			}
		} catch (Exception e) {
			LOGGER.error(e.getMessage());
		}
	}

	@Override
	public void close() throws IOException {
		producer.close();
	}
}
