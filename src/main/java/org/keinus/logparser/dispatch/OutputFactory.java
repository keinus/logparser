package org.keinus.logparser.dispatch;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import org.keinus.logparser.interfaces.OutputAdapter;


public class OutputFactory {
	private OutputFactory() {
		throw new IllegalStateException("Utility class");
	}

	public static OutputAdapter getOutputAdapter(Map<String, String> param) {
		String type = param.get("type");
		try {
			Class<?> cls = Class.forName("org.keinus.logparser.output." + type);
			return (OutputAdapter) cls.getDeclaredConstructor().newInstance();
		}
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
			e.printStackTrace();
		}
		return null;
	}
}
