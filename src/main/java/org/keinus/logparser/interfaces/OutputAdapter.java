package org.keinus.logparser.interfaces;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

public abstract class OutputAdapter implements Closeable {
	public static final String ALL_MESSAGE_STRING = "ALL_MESSAGE_TYPE";

	protected OutputAdapter(Map<String, String> obj) throws IOException {
		if (obj == null) {
			throw new IOException("Property not found.");
		}
		this.type = obj.getOrDefault("messagetype", ALL_MESSAGE_STRING);
		this.addOriginText = Boolean.parseBoolean(obj.get("add_origin_text"));
	}

	private String type = "";
	private boolean addOriginText = false;

	public String getType() {
		return this.type;
	}

	public boolean getAddOriginText() {
		return this.addOriginText;
	}

	public abstract void send(Map<String, Object> json, String jsonString);
	public abstract void flush();

}
