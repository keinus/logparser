package org.keinus.logparser.input;


import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.deser.std.StringDeserializer;

import org.keinus.logparser.interfaces.InputAdapter;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;


/**
 * 카프카의 경우, 큐에서 
 */
public class KafkaInputAdapter implements InputAdapter {
	private static final Logger LOGGER = LoggerFactory.getLogger( KafkaInputAdapter.class );

	private static final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
	private KafkaConsumer<String, String> consumer = null;
	private String type = null;
	private String host = null;
	
	@Override
	public void init(Map<String, String> obj) {
		try {
			if (obj == null) {
            	LOGGER.info("Property not found.");
                throw new IOException("Property not found.");
            }
			// topicid : csas
		    // bootstrapservers : "192.168.254.8:9092"

			

			String server = obj.get("bootstrapservers");
			Properties consumerProperties = new Properties();
			consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, server);
			consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
			consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
			consumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());

			consumer = new KafkaConsumer<>(consumerProperties);
			
			String topic = obj.get("topicid");

			this.type = obj.get("type");
			this.host = server;

            LOGGER.info("Kafka Input Adapter connected at {} by {}", server, topic);
        } catch (IOException e) {
            e.printStackTrace();
            LOGGER.error(e.getMessage());
        }
	}

	@Override
	public String run() {
		if(messageQueue.isEmpty()) {
			ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
			if(!records.isEmpty()) {
				for (ConsumerRecord<String, String> item : records) {
					try {
						messageQueue.put(item.value());
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}
			}
		} 

		if(!messageQueue.isEmpty()) {
			try {
				return messageQueue.take();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	
		return null;
    }

	@Override
	public void close() throws IOException {
		consumer.close();
		
	}

	@Override
	public String getHost() {
		return host;
	}

	@Override
	public String getType() {
		return type;
	}
}
