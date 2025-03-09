package org.keinus.logparser.input;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.keinus.logparser.interfaces.InputAdapter;
import org.keinus.logparser.schema.Message;

public class UdpInputAdapter extends InputAdapter {
	private static final Logger LOGGER = LoggerFactory.getLogger(UdpInputAdapter.class);
	private DatagramSocket serverSocket = null;

	public UdpInputAdapter(Map<String, String> obj) throws IOException {
		super(obj);
		
		int port = Integer.parseInt(obj.get("port"));
		try {
			serverSocket = new DatagramSocket(port);
		} catch (SocketException e) {
			LOGGER.error("Socket Initialize Error: {}", e.getMessage());
			return;
		}

		LOGGER.info("UDP Input Adapter start at port {}", port);
	}

	@Override
	public Message run() {
		if(serverSocket == null)
			return null;

		try {
			byte[] buffer = new byte[1024];
			DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
			serverSocket.receive(receivePacket);

			String payload = new String(receivePacket.getData(), 0, receivePacket.getLength());

			String host = receivePacket.getAddress().toString();
			return new Message(payload, host);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public void close() throws IOException {
		if (serverSocket != null)
			serverSocket.close();
	}

}
