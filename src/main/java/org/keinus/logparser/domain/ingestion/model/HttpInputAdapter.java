package org.keinus.logparser.domain.ingestion.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.keinus.logparser.domain.configuration.model.InputAdapterConfig;
import org.keinus.logparser.domain.model.LogEvent;

/**
 * HTTP 요청을 수신하여 전체 요청 내용을 단일 메시지로 처리하는 입력 어댑터입니다.
 * <p>
 * 이 클래스는 지정된 포트에서 {@link ServerSocket}을 열고 HTTP 클라이언트의 연결을 기다립니다.
 * 연결이 수립되면, HTTP 요청의 헤더와 본문을 포함한 전체 내용을 읽어 하나의
 * {@link Message} 객체로 생성합니다.
 * <p>
 * 이 어댑터는 주로 HTTP POST/PUT 요청을 통해 로그나 이벤트를 수신하는
 * 웹훅(Webhook) 형태의 엔드포인트로 사용될 수 있습니다.
 * {@code run()} 메서드는 블로킹 방식으로 동작하며, 새로운 요청이 들어올 때까지 대기합니다.
 *
 * @see org.keinus.logparser.core.interfaces.InputAdapter
 * @see java.net.ServerSocket
 */
@Slf4j
public class HttpInputAdapter extends InputAdapter {
	private static final int MAX_CONTENT_LENGTH = 10 * 1024 * 1024; // 10MB

	private ServerSocket serverSocket;

	public HttpInputAdapter(InputAdapterConfig config) throws IOException {
		super(config);
		try {
			if (config.getPort() == null) {
				throw new IllegalArgumentException("Port is required for HTTP Input Adapter");
			}
			int port = config.getPort();
			serverSocket = new ServerSocket(port);

			log.info("HTTP Input Adapter start at port {}", port);
		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}

	private Object[] read(Socket socket) throws IOException {
		char[] buffer = new char[1024];
		StringBuilder sb = new StringBuilder();
		Map<String, String> headers = new HashMap<>();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
			String line;
			int readed;
			int rc;

			// Read request line
			line = br.readLine();
			sb.append(line);
			sb.append(System.getProperty("line.separator"));

			while ((line = br.readLine()) != null) {
				sb.append(line);
				sb.append(System.getProperty("line.separator"));
				if (line.equals(""))
					break;
				if (line.contains(":")) {
					String[] split = line.split(":", 2);
					if (split.length >= 2) {
						headers.put(split[0].toUpperCase().trim(), split[1].toUpperCase().trim());
					}
				}
			}
			// Content-Length 검증 및 파싱
			int contentLength = 0;
			String contentLengthStr = headers.get("CONTENT-LENGTH");
			if (contentLengthStr != null) {
				try {
					contentLength = Integer.parseInt(contentLengthStr);
					// 최대 10MB 제한
					if (contentLength < 0 || contentLength > MAX_CONTENT_LENGTH) {
						throw new SecurityException("Content-Length 값이 허용 범위를 벗어남: " + contentLength);
					}
				} catch (NumberFormatException e) {
					log.error("Invalid Content-Length header: {}", contentLengthStr);
					throw new IllegalArgumentException("Invalid Content-Length format", e);
				}
			}

			if (contentLength > 0) {
				readed = 0;
				while ((rc = br.read(buffer, 0, 1024)) != -1) {
					readed += rc;
					sb.append(new String(buffer, 0, rc));
					if (readed >= contentLength)
						break;
				}

				// 실제 읽은 데이터와 Content-Length 일치 검증
				if (readed != contentLength) {
					log.warn("Content-Length mismatch: expected {}, actual {}", contentLength, readed);
				}
			}

		}
		return new Object[] { headers, sb.toString() };
	}

	@Override
	public LogEvent run() {
		if (serverSocket == null)
			return null;
		String msg = null;
		try (Socket socket = serverSocket.accept()) {
			var content = read(socket);
			msg = (String) content[1];
		} catch (IOException e) {
			log.error("Failed to read HTTP request: {}", e.getMessage(), e);
			return null;
		}

		String host = null;
		try {
			host = InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			log.warn("Failed to get local host address: {}", e.getMessage());
			host = "Unknown";
		}

		return createLogEvent(msg, host);
	}

	@Override
	public void close() throws IOException {
		try {
			if (serverSocket != null)
				serverSocket.close();
			serverSocket = null;
		} catch (IOException e) {
			log.error("Error: {}", e.getMessage());
		}
	}
}
