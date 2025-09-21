package org.keinus.logparser.output;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.keinus.logparser.core.interfaces.OutputAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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
public class HttpOutputAdapter extends OutputAdapter {
	private static final Logger LOGGER = LoggerFactory.getLogger( HttpOutputAdapter.class );
	private Socket socket;
    String path = null;
    String host;
    int port;
	int retry = 3;
    
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
		
		LOGGER.info("TCP Output Adapter connected at ip, port {}, {}.", host, port);
	}

	public void send(Map<String, Object> json, String jsonString) {
		synchronized( this ) {
			int count = 0;
			while(true) {
				if(count >= retry) return;
				try {
					socket = new Socket(host, 9200);
					socket.setReuseAddress(true);
					break;
				} catch (IOException e) {
					LOGGER.error(e.getMessage());
					if(count > 1) return;
					count++;
				}
			}

			StringBuilder sb = new StringBuilder();
			sb.append("POST " + path + " HTTP/1.0\r\n");
			sb.append("Content-Length: " + jsonString.length() + "\r\n");
			sb.append("Content-Type: application/json\r\n");
			sb.append("\r\n");
			sb.append(jsonString);
			ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(sb.toString());
			try (DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
				dos.write(byteBuffer.array());
			} catch (IOException e) {
				LOGGER.error(e.getMessage());				
			}
			try {
				socket.close();
			} catch (IOException e) {
				LOGGER.error(e.getMessage());				
			}
		}
	}

	@Override
	public void close() throws IOException {
		if(socket != null)
			socket.close();
		
	}
}
