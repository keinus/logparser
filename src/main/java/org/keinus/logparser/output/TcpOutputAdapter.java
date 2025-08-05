package org.keinus.logparser.output;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.keinus.logparser.interfaces.OutputAdapter;


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
