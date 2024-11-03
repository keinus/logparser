package org.keinus.logparser.input;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.keinus.logparser.interfaces.InputAdapter;


public class TcpInputAdapter implements InputAdapter {
	private static final Logger LOGGER = LoggerFactory.getLogger( TcpInputAdapter.class );
	private ServerSocket serverSocket;
    private String type = null;
	private String host = null;
	private int port = 0;
    
	@Override
	public void init(Map<String, String> obj) {
		try {
            if (obj == null) {
            	LOGGER.info("Property not found.");
            	
                throw new IOException("Property not found.");
            }
            port = Integer.parseInt(obj.get("port"));
            serverSocket = new ServerSocket(port);
			serverSocket.setReuseAddress(true);

            this.type = obj.get("type");
            LOGGER.info("TCP Input Adapter start at port {}", port);
        } catch (IOException e) {
            e.printStackTrace();
            LOGGER.error(e.getMessage());
        }
	}

	@Override
	public String run() {
        try {
            Socket socket = serverSocket.accept();
            if (socket.isConnected()) {
            	BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            	String payload = br.readLine();
                this.host = socket.getInetAddress().toString();
            	return payload;
            }
        } catch(SocketException e) {
			try {
				serverSocket.close();
				serverSocket = new ServerSocket(port);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		} catch (IOException e) {
            e.printStackTrace();
        } 
        return null;
    }
	
	@Override
	public void close() throws IOException {
		if(serverSocket != null)
			serverSocket.close();
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
