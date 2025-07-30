package org.keinus.logparser.dispatch;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import org.keinus.logparser.interfaces.InputAdapter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InputFactory {
	private InputFactory() {
		throw new IllegalStateException("Utility class");
	}

	public static InputAdapter getInputAdapter(Map<String, String> param) {
		String type = param.get("type");
		try {
			Class<?> cls = Class.forName("org.keinus.logparser.input." + type);
			return (InputAdapter) cls.getDeclaredConstructor(Map.class).newInstance(param);
		}
		catch (InstantiationException | IllegalAccessException | IllegalArgumentException 
			| InvocationTargetException | NoSuchMethodException 
			| SecurityException | ClassNotFoundException e) {
			log.error("Invalid Input Adapter. {}", e.getMessage());
			throw new IllegalStateException("Invalid Input Adapter.");
		}
	}
}
