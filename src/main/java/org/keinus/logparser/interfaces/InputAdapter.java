package org.keinus.logparser.interfaces;

import java.io.Closeable;
import java.util.Map;

public interface InputAdapter extends Closeable {
	public void init(Map<String, String> obj);
	public String run();
	public String getHost();
	public String getType();
}
