package org.keinus.logparser.domain.ingestion.model;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.keinus.logparser.domain.ingestion.model.InputAdapter;
import org.keinus.logparser.domain.model.LogEvent;

/**
 * UDP 데이터그램 패킷을 통해 메시지를 수신하는 입력 어댑터입니다.
 * <p>
 * 이 클래스는 지정된 포트에서 {@link DatagramSocket}을 열고 UDP 패킷을 기다립니다.
 * 패킷을 수신하면, 그 내용을 문자열로 변환하여 {@link Message} 객체를 생성합니다.
 * 각 데이터그램 패킷은 하나의 메시지로 처리됩니다.
 * <p>
 * {@code run()} 메서드는 블로킹 방식으로 동작하며, 새로운 UDP 패킷이 도착할 때까지 대기합니다.
 * Syslog와 같은 비연결성 프로토콜을 통해 로그를 수신하는 데 주로 사용됩니다.
 *
 * @see org.keinus.logparser.core.interfaces.InputAdapter
 * @see java.net.DatagramSocket
 * @see java.net.DatagramPacket
 */
@Slf4j
public class UdpInputAdapter extends InputAdapter {
	private static final int MAX_PACKET_SIZE = 1600; 
	private DatagramSocket serverSocket = null;

	public UdpInputAdapter(Map<String, String> obj) throws IOException {
		super(obj);
		
		int port = Integer.parseInt(obj.get("port"));
		try {
			serverSocket = new DatagramSocket(port);
		} catch (SocketException e) {
			log.error("Socket Initialize Error: {}", e.getMessage());
			return;
		}

		log.info("UDP Input Adapter start at port {}", port);
	}

	@Override
	public LogEvent run() {
		if(serverSocket == null)
			return null;

		try {
			byte[] buffer = new byte[MAX_PACKET_SIZE];
			DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
			serverSocket.setSoTimeout(5000);
			serverSocket.receive(receivePacket);
			int actualLength = receivePacket.getLength();
			if (actualLength > MAX_PACKET_SIZE) {
				throw new SecurityException("패킷 크기가 제한을 초과했습니다");
			}

			String payload = new String(receivePacket.getData(), 0, receivePacket.getLength());

			String host = receivePacket.getAddress().toString();
			return createLogEvent(payload, host);
		} catch (IOException e) {
			log.error(e.getMessage());
		}
		return null;
	}

	@Override
	public void close() throws IOException {
		if (serverSocket != null)
			serverSocket.close();
	}

}
