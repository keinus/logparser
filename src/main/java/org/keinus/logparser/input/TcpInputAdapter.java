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
import org.keinus.logparser.schema.Message;


public class TcpInputAdapter extends InputAdapter {
	private static final Logger LOGGER = LoggerFactory.getLogger( TcpInputAdapter.class );
	private ServerSocket serverSocket;
	private int port = 0;
    
	public TcpInputAdapter(Map<String, String> obj) throws IOException {
		super(obj);
		try {
            port = Integer.parseInt(obj.get("port"));
            initServerSocket();
            
            LOGGER.info("TCP Input Adapter start at port {}", port);
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
        }
	}

	private void initServerSocket() throws IOException {
		serverSocket = new ServerSocket(port);
		serverSocket.setReuseAddress(true);
	}

	@Override
	public Message run() {
        try {
            Socket socket = serverSocket.accept();
            if (socket.isConnected()) {
            	BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            	String payload = br.readLine();
                String host = socket.getInetAddress().toString();
				return new Message(payload, host);
            }
        } catch(SocketException e) {
			try {
				initServerSocket();
			} catch (IOException e1) {
				LOGGER.error("TcpInputAdaptor Server Socket Error(Terminate this Adapter): {}", e1.getMessage());
			}
		} catch (IOException e) {
            LOGGER.error(e.getMessage());
        } 
        return null;
    }
	
	@Override
	public void close() throws IOException {
		if(serverSocket != null)
			serverSocket.close();
	}
}
