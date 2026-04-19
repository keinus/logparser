package org.keinus.logparser.domain.output.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import com.rabbitmq.client.ConnectionFactory;

import lombok.extern.slf4j.Slf4j;
import org.keinus.logparser.domain.model.LogEvent;

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
	private final ReentrantLock lock = new ReentrantLock();
	private Channel channel = null;
	private Connection connection = null;
	private final AtomicBoolean closed = new AtomicBoolean(false);

	public RabbitMQAdapter(Map<String, String> obj) throws IOException {
		this(obj, null, null);
	}

	RabbitMQAdapter(Map<String, String> obj, Connection connection, Channel channel) throws IOException {
		super(obj);
		routingkey = obj.get("routingkey");
		exchange = obj.get("exchange");

		if (connection != null || channel != null) {
			this.connection = connection;
			this.channel = channel;
			log.info("RabbitMQ adapter initialized with injected connection/channel for exchange: {}", exchange);
			return;
		}

		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost(obj.get("host"));
		factory.setUsername(obj.get("username"));
		factory.setPassword(obj.get("password"));
		factory.setPort(Integer.parseInt(obj.get("port")));
		factory.setConnectionTimeout(10000); // 10초 연결 타임아웃
		factory.setHandshakeTimeout(10000); // 10초 핸드셰이크 타임아웃

		try {
			this.connection = factory.newConnection();
			this.channel = this.connection.createChannel();
			this.channel.confirmSelect();
			this.channel.exchangeDeclare(exchange, BuiltinExchangeType.TOPIC);
			log.info("RabbitMQ adapter initialized for exchange: {}", exchange);
		} catch (IOException | TimeoutException e) {
			log.error("Failed to initialize RabbitMQ connection: {}", e.getMessage());
			// Clean up partially initialized resources
			closeResources();
			throw new IOException("Failed to initialize RabbitMQ adapter", e);
		}
	}

	private void closeResources() {
		lock.lock();
		try {
			if (channel != null) {
				try {
					// 먼저 graceful close 시도 (타임아웃 5초)
					channel.close(5000, "Adapter closing");
					log.info("RabbitMQ channel closed gracefully");
				} catch (IOException | TimeoutException e) {
					log.warn("Graceful channel close failed, forcing abort: {}", e.getMessage());
					try {
						// Graceful close 실패 시 즉시 종료
						channel.abort();
					} catch (Exception abortEx) {
						log.error("Failed to abort channel: {}", abortEx.getMessage());
					}
				} finally {
					channel = null;
				}
			}

			if (connection != null) {
				try {
					// 먼저 graceful close 시도 (타임아웃 5초)
					connection.close(5000);
					log.info("RabbitMQ connection closed gracefully");
				} catch (IOException e) {
					log.warn("Graceful connection close failed, forcing abort: {}", e.getMessage());
					try {
						// Graceful close 실패 시 즉시 종료
						connection.abort();
					} catch (Exception abortEx) {
						log.error("Failed to abort connection: {}", abortEx.getMessage());
					}
				} finally {
					connection = null;
				}
			}
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void close() throws IOException {
		// 멱등성 보장: 이미 닫혔으면 즉시 리턴
		if (!closed.compareAndSet(false, true)) {
			log.debug("RabbitMQ Adapter already closed, skipping");
			return;
		}

		closeResources();
	}

	@Override
	public void send(LogEvent logEvent) {
		String jsonString = serializeEvent(logEvent);
		lock.lock();
		try {
			if (channel == null) {
				throw deliveryFailure("RabbitMQ channel is not initialized");
			}

			try {
				channel.basicPublish(exchange, routingkey, null, jsonString.getBytes(StandardCharsets.UTF_8));
				channel.waitForConfirmsOrDie(getTimeoutMs());
			} catch (IOException | InterruptedException | TimeoutException e) {
				if (e instanceof InterruptedException) {
					Thread.currentThread().interrupt();
				}
				throw deliveryFailure("Failed to publish message to RabbitMQ", e);
			}
		} finally {
			lock.unlock();
		}
	}
}
