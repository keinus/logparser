package org.keinus.logparser.output;

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
import org.keinus.logparser.interfaces.OutputAdapter;


public class KafkaOutputAdapter implements OutputAdapter {
	private static final Logger LOGGER = LoggerFactory.getLogger( KafkaOutputAdapter.class );
	Producer<String, String> producer = null;
	String topic = "";
	
	@Override
	public void init(Map<String, String> obj) {
		try {
			if (obj == null) {
            	LOGGER.info("Property not found.");
                throw new IOException("Property not found.");
            }
            topic = obj.get("topicid");
            String server = obj.get("bootstrapservers");
            
            Properties props = new Properties();
			props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
			props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, server);
			props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
			producer = new KafkaProducer<>(props);
            
            LOGGER.info("Kafka Output Adapter connected to {}, {}", server, topic);

        } catch (IOException e) {
            e.printStackTrace();
            LOGGER.error(e.getMessage());
        }
	}

	public void send(Map<String, Object> json, String jsonString) {
		try {
			synchronized( this ) {
				producer.send(new ProducerRecord<>(topic, jsonString));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void close() throws IOException {
		producer.close();
	}

	@Override
	public void flush() {
		// nothing
	}
}
