package org.keinus.logparser.output;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.keinus.logparser.core.interfaces.OutputAdapter;

import lombok.extern.slf4j.Slf4j;


/**
 * 처리된 메시지를 지정된 URL로 HTTP POST 요청을 보내 전송하는 출력 어댑터입니다.
 * <p>
 * 이 클래스는 {@link OutputAdapter}를 구현하며, 각 메시지에 대해 새로운 {@link Socket} 연결을 생성하고
 * 직접 HTTP 요청 문자열을 구성하여 전송합니다.
 * {@code Content-Type}은 {@code application/json}으로 고정됩니다.
 * <p>
 * <b>주의:</b> 이 클래스는 각 요청마다 새로운 소켓을 열고 닫으며, HTTP 요청을 수동으로 구성합니다.
 * 대량의 메시지를 처리할 경우, 연결 재사용(Keep-Alive)을 지원하는
 * {@link org.apache.http.client.HttpClient}와 같은 라이브러리를 사용하는
 * {@link OpenSearchOutputAdapter}에 비해 성능이 저하될 수 있습니다.
 *
 * @see org.keinus.logparser.core.interfaces.OutputAdapter
 * @see java.net.Socket
 */
@Slf4j
public class HttpOutputAdapter extends OutputAdapter {
	private Socket socket;
    String path = null;
    String host;
    int port;
	int retry = 3;
	private boolean keepAlive = true;
	private long lastUsed = 0;
	private static final long CONNECTION_TIMEOUT = 30000; // 30초
    
	public HttpOutputAdapter(Map<String, String> obj) throws IOException {
		super(obj);
		
		String url = obj.get("url");
		
		int start = url.indexOf("://");
		start += 3;
		int portIndex = url.indexOf(":", start);
		int pathIndex = url.indexOf("/", start);
		if(portIndex == 0) {
			this.port = 80;
		} else {
			try {
				this.port = Integer.parseInt(url.substring(portIndex+1, pathIndex));
			} catch(Exception e) {
				this.port = 80;
			}
		}
			
		this.host = url.substring(start, portIndex);
		
		path = url.substring(pathIndex);
		
		log.info("TCP Output Adapter connected at ip, port {}, {}.", host, port);
	}

	private void ensureConnection() throws IOException {
		long currentTime = System.currentTimeMillis();

		// 연결이 없거나 타임아웃된 경우 새 연결 생성
		if (socket == null || socket.isClosed() || !socket.isConnected() ||
			(currentTime - lastUsed > CONNECTION_TIMEOUT)) {
			closeConnection();
			createConnection();
		}
		lastUsed = currentTime;
	}

	private void createConnection() throws IOException {
		int count = 0;
		while(count < retry) {
			try {
				socket = new Socket(host, port);
				socket.setReuseAddress(true);
				socket.setKeepAlive(keepAlive);
				socket.setSoTimeout(5000); // 5초 읽기 타임아웃
				log.debug("New connection established to {}:{}", host, port);
				break;
			} catch (IOException e) {
				log.error("Connection attempt {} failed: {}", count + 1, e.getMessage());
				count++;
				if(count >= retry) {
					throw e;
				}
			}
		}
	}

	private void closeConnection() {
		if (socket != null && !socket.isClosed()) {
			try {
				socket.close();
			} catch (IOException e) {
				log.error("Error closing connection: {}", e.getMessage());
			}
		}
		socket = null;
	}

	public void send(Map<String, Object> json, String jsonString) {
		synchronized( this ) {
			try {
				ensureConnection();

				StringBuilder sb = new StringBuilder();
				sb.append("POST " + path + " HTTP/1.1\r\n"); // HTTP/1.1로 변경
				sb.append("Host: " + host + ":" + port + "\r\n");
				sb.append("Content-Length: " + jsonString.length() + "\r\n");
				sb.append("Content-Type: application/json\r\n");
				if (keepAlive) {
					sb.append("Connection: keep-alive\r\n");
				} else {
					sb.append("Connection: close\r\n");
				}
				sb.append("\r\n");
				sb.append(jsonString);

				ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(sb.toString());
				try (DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
					dos.write(byteBuffer.array());
					dos.flush();
				}

				// Keep-Alive가 아닌 경우에만 연결 닫기
				if (!keepAlive) {
					closeConnection();
				}

			} catch (IOException e) {
				log.error("Send failed: {}", e.getMessage());
				closeConnection(); // 오류 시 연결 재설정
			}
		}
	}

	@Override
	public void close() throws IOException {
		closeConnection();
		log.info("HTTP Output Adapter closed");
	}
}
