package org.keinus.logparser.input;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.keinus.logparser.interfaces.InputAdapter;


public class UdpInputAdapter implements InputAdapter {
	private static final Logger LOGGER = LoggerFactory.getLogger( UdpInputAdapter.class );
	private DatagramSocket serverSocket;
    private String type = null;
	private String host = null;

	
    @Override
	public void init(Map<String, String> obj) {
		try {
            if (obj == null) {
            	LOGGER.info("Property not found.");
            	
                throw new IOException("Property not found.");
            }
            int port = Integer.parseInt(obj.get("port"));
            serverSocket = new DatagramSocket(port);
            
            this.type = obj.get("type");
            
            LOGGER.info("UDP Input Adapter start at port {}", port);
        } catch (IOException e) {
            e.printStackTrace();
            LOGGER.error(e.getMessage());
        }
	}

	@Override
	public String run() {
        try {
        	byte[] buffer = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
            serverSocket.receive(receivePacket);
            
            String payload = new String(receivePacket.getData(), 0, receivePacket.getLength());
            
            this.host = receivePacket.getAddress().toString();
        	return payload;
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
