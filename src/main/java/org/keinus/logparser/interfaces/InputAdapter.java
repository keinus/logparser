package org.keinus.logparser.interfaces;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

import org.keinus.logparser.schema.Message;

public abstract class InputAdapter implements Closeable {
	private String type = "";
	private String name;

	protected InputAdapter(Map<String, String> obj) throws IOException {
		if (obj == null) {
			throw new IOException("Property not found.");
		}
		this.type = obj.get("messagetype");
		this.name = getClass().getSimpleName() + ":" + obj.toString();
	}

	public abstract Message run();

	public String getType() {
		return this.type;
	}

	@Override
	public String toString() {
		return this.name;
	}
}
