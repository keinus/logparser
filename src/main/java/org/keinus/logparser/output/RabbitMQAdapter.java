package org.keinus.logparser.output;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.keinus.logparser.interfaces.OutputAdapter;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;

public class RabbitMQAdapter extends OutputAdapter {
	private String routingkey = null;
	private String exchange = null;
	Channel channel = null;
	Connection connection = null;

	public RabbitMQAdapter(Map<String, String> obj) throws IOException {
		super(obj);
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost(obj.get("host"));
		factory.setUsername(obj.get("username"));
		factory.setPassword(obj.get("password"));
		factory.setPort(Integer.parseInt(obj.get("port")));
		routingkey = obj.get("routingkey");
		exchange = obj.get("exchange");

		try {
			connection = factory.newConnection();
			channel = connection.createChannel();
			channel.exchangeDeclare(exchange, BuiltinExchangeType.TOPIC);
		} catch (IOException | TimeoutException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void close() throws IOException {
		try {
			channel.close();
			connection.close();
		} catch (IOException | TimeoutException e) {
			e.printStackTrace();
		}

	}

	@Override
	public void send(Map<String, Object> json, String jsonString) {
		try {
			channel.basicPublish(exchange, routingkey, null, jsonString.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void flush() {
		// nothing
	}

}
