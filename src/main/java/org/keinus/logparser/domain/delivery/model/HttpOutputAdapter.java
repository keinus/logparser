package org.keinus.logparser.domain.delivery.model;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.keinus.logparser.domain.delivery.model.OutputAdapter;

import lombok.extern.slf4j.Slf4j;


/**
 * 처리된 메시지를 지정된 URL로 HTTP POST 요청을 보내 전송하는 출력 어댑터입니다.
 * <p>
 * 이 클래스는 {@link OutputAdapter}를 구현하며, Socket을 통해 직접 HTTP 요청을 구성하여 전송합니다.
 * {@code Content-Type}은 {@code application/json}으로 고정됩니다.
 * <p>
 * <b>제한사항:</b>
 * <ul>
 * <li>HTTPS는 지원하지 않음 (SSL/TLS 미구현)</li>
 * <li>Keep-Alive 연결을 통한 성능 최적화</li>
 * <li>HTTP 응답 상태 코드 검증</li>
 * </ul>
 *
 * @see org.keinus.logparser.core.interfaces.OutputAdapter
 * @see java.net.Socket
 */
@Slf4j
public class HttpOutputAdapter extends OutputAdapter {
	private Socket socket;
	private DataOutputStream outputStream;
	private BufferedReader inputReader;
    private final String path;
    private final String host;
    private final int port;
    private final boolean isHttps;
	private static final int RETRY_COUNT = 3;
	private static final boolean KEEP_ALIVE = true;
	private long lastUsed = 0;
	private long connectionCreatedAt = 0;
	private static final long CONNECTION_TIMEOUT = 30000; // 30초 유휴 타임아웃
	private static final long MAX_CONNECTION_AGE = 300000; // 5분 최대 연결 수명
	private final AtomicBoolean closed = new AtomicBoolean(false);

	public HttpOutputAdapter(Map<String, String> obj) throws IOException {
		super(obj);

		String url = obj.get("url");
		if (url == null) {
			throw new IOException("URL is required for HttpOutputAdapter");
		}

		UrlParts urlParts = parseUrl(url);
		this.host = urlParts.host;
		this.port = urlParts.port;
		this.path = urlParts.path;
		this.isHttps = urlParts.isHttps;

		if (isHttps) {
			log.warn("HTTPS URLs are not supported, using plain HTTP connection to {}:{}", host, port);
		}

		log.info("HTTP Output Adapter configured for {}:{}{}", host, port, path);
	}

	private static class UrlParts {
		final String host;
		final int port;
		final String path;
		final boolean isHttps;

		UrlParts(String host, int port, String path, boolean isHttps) {
			this.host = host;
			this.port = port;
			this.path = path;
			this.isHttps = isHttps;
		}
	}

	private static UrlParts parseUrl(String url) throws IOException {
		if (!url.startsWith("http://") && !url.startsWith("https://")) {
			throw new IOException("URL must start with http:// or https://");
		}

		boolean isHttps = url.startsWith("https://");
		int protocolEnd = url.indexOf("://");
		if (protocolEnd == -1) {
			throw new IOException("Invalid URL format");
		}

		int hostStart = protocolEnd + 3;
		int portIndex = url.indexOf(":", hostStart);
		int pathIndex = url.indexOf("/", hostStart);

		String path;
		if (pathIndex == -1) {
			pathIndex = url.length();
			path = "/";
		} else {
			path = url.substring(pathIndex);
		}

		String host;
		int port;
		if (portIndex == -1 || portIndex > pathIndex) {
			port = isHttps ? 443 : 80;
			host = url.substring(hostStart, pathIndex);
		} else {
			host = url.substring(hostStart, portIndex);
			try {
				String portStr = url.substring(portIndex + 1, pathIndex);
				port = Integer.parseInt(portStr);
				if (port <= 0 || port > 65535) {
					throw new IOException("Port must be between 1 and 65535");
				}
			} catch (NumberFormatException e) {
				throw new IOException("Invalid port number in URL: " + e.getMessage());
			}
		}

		if (host.isEmpty()) {
			throw new IOException("Host cannot be empty");
		}

		return new UrlParts(host, port, path, isHttps);
	}

	private void ensureConnection() throws IOException {
		long currentTime = System.currentTimeMillis();

		// 연결이 없거나 타임아웃되었거나 연결 수명이 다한 경우 새 연결 생성
		boolean needsReconnect = socket == null ||
								 socket.isClosed() ||
								 !socket.isConnected() ||
								 (currentTime - lastUsed > CONNECTION_TIMEOUT) ||
								 (currentTime - connectionCreatedAt > MAX_CONNECTION_AGE);

		if (needsReconnect) {
			if (socket != null) {
				log.debug("Reconnecting - idle: {}ms, age: {}ms",
						currentTime - lastUsed,
						currentTime - connectionCreatedAt);
			}
			closeConnection();
			createConnection();
		}
		lastUsed = currentTime;
	}

	private void createConnection() throws IOException {
		int count = 0;
		while(count < RETRY_COUNT) {
			try {
				socket = new Socket();
				socket.setReuseAddress(true);
				socket.setKeepAlive(KEEP_ALIVE);
				socket.setSoTimeout(5000); // Read timeout
				socket.setTcpNoDelay(true);

				// 연결 타임아웃 설정
				socket.connect(new java.net.InetSocketAddress(host, port), 10000);

				// Stream 초기화 (socket 재사용을 위해 필드로 보관)
				outputStream = new DataOutputStream(socket.getOutputStream());
				inputReader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

				connectionCreatedAt = System.currentTimeMillis();
				log.debug("New connection established to {}:{}", host, port);
				break;
			} catch (IOException e) {
				log.error("Connection attempt {} failed: {}", count + 1, e.getMessage());
				count++;
				if (count < RETRY_COUNT) {
					try {
						Thread.sleep(1000L * count); // 지수적 백오프
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						throw new IOException("Connection interrupted", ie);
					}
				} else {
					throw new IOException("Failed to connect after " + RETRY_COUNT + " attempts", e);
				}
			}
		}
	}

	private void closeConnection() {
		// Stream들을 먼저 닫기
		if (inputReader != null) {
			try {
				inputReader.close();
			} catch (IOException e) {
				log.debug("Error closing input reader: {}", e.getMessage());
			}
			inputReader = null;
		}

		if (outputStream != null) {
			try {
				outputStream.close();
			} catch (IOException e) {
				log.debug("Error closing output stream: {}", e.getMessage());
			}
			outputStream = null;
		}

		// Socket 닫기
		if (socket != null && !socket.isClosed()) {
			try {
				socket.close();
			} catch (IOException e) {
				log.error("Error closing socket: {}", e.getMessage());
			}
		}
		socket = null;
	}

	public void send(Map<String, Object> json, String jsonString) {
		if (jsonString == null || jsonString.isEmpty()) {
			log.warn("Empty or null JSON string, skipping send");
			return;
		}

		synchronized(this) {
			try {
				ensureConnection();

				byte[] jsonBytes = jsonString.getBytes(StandardCharsets.UTF_8);
				StringBuilder sb = new StringBuilder();
				sb.append("POST ").append(path).append(" HTTP/1.1\r\n");
				sb.append("Host: ").append(host).append(":").append(port).append("\r\n");
				sb.append("Content-Length: ").append(jsonBytes.length).append("\r\n");
				sb.append("Content-Type: application/json\r\n");
				sb.append("User-Agent: LogParser/1.0\r\n");
				if (KEEP_ALIVE) {
					sb.append("Connection: keep-alive\r\n");
				} else {
					sb.append("Connection: close\r\n");
				}
				sb.append("\r\n");

				// 헤더와 바디를 전송 (재사용 가능한 outputStream 사용)
				outputStream.write(sb.toString().getBytes(StandardCharsets.UTF_8));
				outputStream.write(jsonBytes);
				outputStream.flush();

				// HTTP 응답 읽기
				readHttpResponse();

				// Keep-Alive가 아닌 경우에만 연결 닫기
				if (!KEEP_ALIVE) {
					closeConnection();
				}

			} catch (IOException e) {
				log.error("Send failed: {}", e.getMessage());
				closeConnection();
			} catch (Exception e) {
				log.error("Unexpected error during send: {}", e.getMessage());
				closeConnection();
			}
		}
	}

	private void readHttpResponse() throws IOException {
		// 재사용 가능한 inputReader 사용 (socket 재사용을 위해 닫지 않음)
		try {
			String statusLine = inputReader.readLine();
			if (statusLine == null) {
				throw new IOException("No HTTP response received");
			}

			log.debug("HTTP response: {}", statusLine);

			// 상태 코드 정확히 파싱
			try {
				String[] parts = statusLine.split(" ", 3);
				if (parts.length >= 2) {
					int statusCode = Integer.parseInt(parts[1]);
					if (statusCode < 200 || statusCode >= 300) {
						log.error("HTTP request failed with status code {}: {}", statusCode, statusLine);
					} else {
						log.debug("HTTP request succeeded with status code {}", statusCode);
					}
				} else {
					log.warn("Invalid HTTP status line format: {}", statusLine);
				}
			} catch (NumberFormatException e) {
				log.warn("Failed to parse HTTP status code from: {}", statusLine);
			}

			// 헤더 읽기 (Content-Length 확인용)
			String line;
			int contentLength = 0;
			boolean isChunked = false;
			while ((line = inputReader.readLine()) != null && !line.isEmpty()) {
				String lowerLine = line.toLowerCase();
				if (lowerLine.startsWith("content-length:")) {
					try {
						contentLength = Integer.parseInt(line.substring(15).trim());
					} catch (NumberFormatException e) {
						log.warn("Invalid Content-Length header: {}", line);
					}
				} else if (lowerLine.startsWith("transfer-encoding:") && lowerLine.contains("chunked")) {
					isChunked = true;
				}
			}

			// 응답 바디 읽기 (버퍼 비우기)
			if (contentLength > 0) {
				char[] buffer = new char[Math.min(contentLength, 8192)];
				int remaining = contentLength;
				while (remaining > 0) {
					int toRead = Math.min(remaining, buffer.length);
					int read = inputReader.read(buffer, 0, toRead);
					if (read == -1) break;
					remaining -= read;
				}
			} else if (isChunked) {
				// Chunked encoding인 경우 간단히 처리
				while ((line = inputReader.readLine()) != null) {
					if (line.equals("0")) break; // 마지막 청크
				}
			}
		} catch (IOException e) {
			// 읽기 오류 발생 시 연결 종료 필요
			throw e;
		}
	}

	@Override
	public void close() throws IOException {
		// 멱등성 보장: 이미 닫혔으면 즉시 리턴
		if (!closed.compareAndSet(false, true)) {
			log.debug("HTTP Output Adapter already closed, skipping");
			return;
		}

		closeConnection();
		log.info("HTTP Output Adapter closed");
	}
}