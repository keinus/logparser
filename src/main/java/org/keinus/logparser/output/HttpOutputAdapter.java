package org.keinus.logparser.output;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.keinus.logparser.interfaces.OutputAdapter;


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
					e.printStackTrace();
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
		if(socket != null)
			socket.close();
		
	}

	@Override
	public void flush() {
		// nothing
	}
}
