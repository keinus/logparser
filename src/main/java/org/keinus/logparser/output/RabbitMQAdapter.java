package org.keinus.logparser.output;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.keinus.logparser.core.interfaces.OutputAdapter;

import com.rabbitmq.client.ConnectionFactory;

import lombok.extern.slf4j.Slf4j;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;

/**
 * 처리된 메시지를 RabbitMQ 익스체인지(exchange)로 전송하는 출력 어댑터입니다.
 * <p>
 * 이 클래스는 {@link OutputAdapter}를 구현하며, RabbitMQ Java 클라이언트를 사용하여
 * 지정된 익스체인지에 메시지를 발행(publish)합니다.
 * <p>
 * 주요 설정:
 * <ul>
 *     <li>{@code host}, {@code port}, {@code username}, {@code password}: RabbitMQ 서버 접속 정보</li>
 *     <li>{@code exchange}: 메시지를 발행할 익스체인지의 이름</li>
 *     <li>{@code routingkey}: 메시지 발행 시 사용할 라우팅 키</li>
 * </ul>
 * 익스체인지 타입은 'TOPIC'으로 고정되어 있습니다.
 *
 * @see org.keinus.logparser.core.interfaces.OutputAdapter
 * @see com.rabbitmq.client.Channel
 */
@Slf4j
public class RabbitMQAdapter extends OutputAdapter {
	private String routingkey = null;
	private String exchange = null;
	Channel channel = null;
	Connection connection = null;

	public RabbitMQAdapter(Map<String, String> obj) throws IOException {
		super(obj);
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost(obj.get("host"));
		factory.setUsername(obj.get("username"));
		factory.setPassword(obj.get("password"));
		factory.setPort(Integer.parseInt(obj.get("port")));
		routingkey = obj.get("routingkey");
		exchange = obj.get("exchange");

		try {
			connection = factory.newConnection();
			channel = connection.createChannel();
			channel.exchangeDeclare(exchange, BuiltinExchangeType.TOPIC);
		} catch (IOException | TimeoutException e) {
			log.error("Failed to initialize RabbitMQ connection: {}", e.getMessage());
			// Clean up partially initialized resources
			closeResources();
			throw new IOException("Failed to initialize RabbitMQ adapter", e);
		}
	}

	private void closeResources() {
		if (channel != null) {
			try {
				channel.close();
			} catch (IOException | TimeoutException e) {
				log.warn("Failed to close channel: {}", e.getMessage());
			}
		}
		if (connection != null) {
			try {
				connection.close();
			} catch (IOException e) {
				log.warn("Failed to close connection: {}", e.getMessage());
			}
		}
	}

	@Override
	public void close() throws IOException {
		try {
			channel.close();
			connection.close();
		} catch (IOException | TimeoutException e) {
			log.error(e.getMessage());
		}

	}

	@Override
	public void send(Map<String, Object> json, String jsonString) {
		try {
			channel.basicPublish(exchange, routingkey, null, jsonString.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}
}
