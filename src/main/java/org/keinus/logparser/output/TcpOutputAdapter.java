package org.keinus.logparser.output;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.keinus.logparser.interfaces.OutputAdapter;


public class TcpOutputAdapter implements OutputAdapter {
	private static final Logger LOGGER = LoggerFactory.getLogger( TcpOutputAdapter.class );
	private Socket socket;
	private String host = null;
	private int port = 0;
	private int retry = 3;
    
	public void init(Map<String, String> obj) {
		try {
			if (obj == null) {
            	LOGGER.info("Property not found.");
                throw new IOException("Property not found.");
            }
            port = Integer.parseInt(obj.get("port"));
            host = obj.get("host");
            socket = new Socket();
            LOGGER.info("TCP Output Adapter connected at ip, port {}, {}", host, port);
        } catch (IOException e) {
            e.printStackTrace();
            LOGGER.error(e.getMessage());
        } catch (Exception e) {
			e.printStackTrace();
		}
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
					e.printStackTrace();
					LOGGER.error(e.getMessage());
					count++;
				}
			}
			
			try (DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
				dos.write(byteBuffer.array());
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void close() throws IOException {
		socket.close();
		socket = null;
	}

	@Override
	public void flush() {
		// nothing
	}
}
