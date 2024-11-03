package org.keinus.logparser.input;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.keinus.logparser.interfaces.InputAdapter;


public class HttpInputAdapter implements InputAdapter {
	private static final Logger LOGGER = LoggerFactory.getLogger( HttpInputAdapter.class );

	private String type = null;
	private String host = null;
	private ServerSocket serverSocket;

	@Override
    public void init(Map<String, String> obj) {
		try {
			if (obj == null) {
				LOGGER.error("HttpInputAdapter: Property not found.");
				throw new IOException("Property not found.");
			}
			this.type = obj.get("type");
			int port = Integer.parseInt(obj.get("port"));
			serverSocket = new ServerSocket(port);

            LOGGER.info("HTTP Input Adapter start at port {}", port);
        } catch (IOException e) {
            e.printStackTrace();
            LOGGER.error(e.getMessage());
        }
	}

	private Object[] read(Socket socket) throws IOException {
		char[] buffer = new char[1024];
		StringBuilder sb = new StringBuilder();
		Map<String, String> headers = new HashMap<>();
		try(BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
			String line;
			int readed;
			int rc;

			// Read request line
			line = br.readLine();
			sb.append(line);
			sb.append(System.getProperty("line.separator"));

			while((line=br.readLine()) != null) {
				sb.append(line);
				sb.append(System.getProperty("line.separator"));
				if(line.equals(""))
					break;
				if(line.contains(":")) {
					String[] split = line.split(":");
					headers.put(split[0].toUpperCase().trim(), split[1].toUpperCase().trim());
				}
			}
			int contentLength = Integer.parseInt(headers.get("CONTENT-LENGTH"));
			if(contentLength > 0) {
				readed = 0;
				while((rc = br.read(buffer, 0, 1024)) != -1) {
					readed += rc;
					sb.append(new String(buffer, 0, rc));
					if(readed >= contentLength)
						break;
				}
			}

		}
		return new Object[] {headers, sb.toString()};
	}
    
	@Override
	public String run() {
		if(serverSocket == null) return null;
		String msg = null;
		try {
			var content = read(serverSocket.accept());
			msg = (String) content[1];
		} catch (IOException e) {
            LOGGER.error(e.getMessage());
			return null;
		}
		try {
			host = InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			host = "Unknown";
		}

		return msg;
	}

	@Override
	public void close() throws IOException {
		try {
			if(serverSocket != null)
				serverSocket.close();
			serverSocket = null;
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public String getHost() {
		return this.host;
	}

	@Override
	public String getType() {
		return this.type;
	}
}
