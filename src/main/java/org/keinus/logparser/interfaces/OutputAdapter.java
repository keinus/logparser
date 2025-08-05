package org.keinus.logparser.interfaces;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

public abstract class OutputAdapter implements Closeable {
	private String name;

	protected OutputAdapter(Map<String, String> obj) throws IOException {
		if (obj == null) {
			throw new IOException("Property not found.");
		}
		this.type = obj.getOrDefault("messagetype", null);
		this.addOriginText = Boolean.parseBoolean(obj.get("add_origin_text"));
		this.name = getClass().getSimpleName() + ":" + obj.toString();
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

	@Override
	public String toString() {
		return this.name;
	}
}
