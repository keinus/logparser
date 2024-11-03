package org.keinus.logparser.interfaces;

import java.io.Closeable;
import java.util.Map;

public interface OutputAdapter extends Closeable {
	public void init(Map<String, String> obj);
	public void send(Map<String, Object> json, String jsonString);
	public void flush();
}
