package org.keinus.logparser.output;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.keinus.logparser.core.interfaces.OutputAdapter;

import lombok.extern.slf4j.Slf4j;


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
	private Socket socket;
	private String host = null;
	private int port = 0;
	private int retry = 3;
    
	public TcpOutputAdapter(Map<String, String> obj) throws IOException {
		super(obj);

		port = Integer.parseInt(obj.get("port"));
		host = obj.get("host");
		socket = new Socket();
		log.info("TCP Output Adapter connected at ip, port {}, {}", host, port);
	}

	public void send(Map<String, Object> json, String jsonString) {
		synchronized( this ) {
			ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(jsonString);

			int count = 0;
			while(true) {
				if(count >= retry) return;
				try {
					socket.connect(new InetSocketAddress(host, port));
					socket.setReuseAddress(true);
					break;
				} catch (IOException e) {
					log.error(e.getMessage());
					count++;
				}
			}
			
			try (DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
				dos.write(byteBuffer.array());
			} catch (IOException e) {
				log.error(e.getMessage());
			}
			try {
				socket.close();
			} catch (IOException e) {
				log.error(e.getMessage());
			}
		}
	}

	@Override
	public void close() throws IOException {
		socket.close();
		socket = null;
	}
}
