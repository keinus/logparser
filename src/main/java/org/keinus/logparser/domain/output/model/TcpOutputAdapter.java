package org.keinus.logparser.domain.output.model;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import org.keinus.logparser.infrastructure.util.ThreadUtil;

import lombok.extern.slf4j.Slf4j;
import org.keinus.logparser.domain.model.LogEvent;

/**
 * 처리된 메시지를 지정된 TCP 서버로 전송하는 출력 어댑터입니다.
 * <p>
 * 이 클래스는 {@link OutputAdapter}를 구현하며, {@link #send(Map, String)}가 호출될 때마다
 * 원격 서버에 새로운 {@link Socket} 연결을 시도하고, 성공하면 메시지(JSON 문자열)를 전송한 후
 * 연결을 닫습니다.
 * <p>
 * <b>주의:</b> 각 메시지 전송 시마다 새로운 TCP 연결을 생성하고 닫는 것은 매우 비효율적입니다.
 * 대량의 메시지를 처리해야 하는 운영 환경에서는 연결을 재사용하거나,
 * 메시지 브로커(예: Kafka, RabbitMQ)를 사용하는 다른 어댑터를 고려해야 합니다.
 *
 * @see org.keinus.logparser.core.interfaces.OutputAdapter
 * @see java.net.Socket
 */
@Slf4j
public class TcpOutputAdapter extends OutputAdapter {
	private static final int DEFAULT_RETRY_COUNT = 3;
	private static final int DEFAULT_RETRY_DELAY_MS = 1000;

	private final String host;
	private final int port;
	private final int retryCount;
	private final int retryDelayMs;
	private final AtomicBoolean closed = new AtomicBoolean(false);
	private final ReentrantLock lock = new ReentrantLock();

	public TcpOutputAdapter(Map<String, String> obj) throws IOException {
		super(obj);

		port = Integer.parseInt(obj.get("port"));
		host = obj.get("host");
		retryCount = parsePositiveInt(obj.get("retryCount"), DEFAULT_RETRY_COUNT);
		retryDelayMs = parsePositiveInt(obj.get("retryDelayMs"), DEFAULT_RETRY_DELAY_MS);
		log.info("TCP Output Adapter initialized for {}:{}", host, port);
	}

	@Override
	public void send(LogEvent logEvent) {
		String jsonString = serializeEvent(logEvent);
		byte[] payload = jsonString.getBytes(StandardCharsets.UTF_8);
		lock.lock();
		try {
			sendWithRetry(payload);
		} finally {
			lock.unlock();
		}
	}

	private void sendWithRetry(byte[] payload) {
		IOException lastException = null;

		for (int attempt = 1; attempt <= retryCount; attempt++) {
			try (Socket socket = new Socket()) {
				socket.setKeepAlive(false);
				socket.setTcpNoDelay(true);
				socket.setSoTimeout(getTimeoutMs());
				socket.connect(new InetSocketAddress(host, port), getTimeoutMs());

				DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
				dos.write(payload);
				dos.flush();
				return;
			} catch (IOException e) {
				lastException = e;
				log.error("Connection failed (attempt {}/{}): {}", attempt, retryCount, e.getMessage());
				if (attempt < retryCount) {
					ThreadUtil.sleep(retryDelayMs);
				}
			}
		}

		throw deliveryFailure("Failed to send message", lastException);
	}

	private int parsePositiveInt(String value, int defaultValue) {
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

	@Override
	public void close() throws IOException {
		// 멱등성 보장: 이미 닫혔으면 즉시 리턴
		if (!closed.compareAndSet(false, true)) {
			log.debug("TCP Output Adapter already closed, skipping");
			return;
		}
	}
}
