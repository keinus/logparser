package org.keinus.logparser.parser;

import java.lang.reflect.Type;
import java.util.Map;

import org.keinus.logparser.core.interfaces.IParser;
import org.keinus.logparser.core.schema.LogEvent;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonParser implements IParser {
	/**
	 * Parser that converts JSON-formatted log messages into a map of key-value pairs.
	 * Uses Gson for deserialization.
	 */
    Gson gson = new Gson();
    Type type = new TypeToken<Map<String, Object>>() {}.getType();

    @Override
	public void init(Object param) {
		// 초기화 없음.
	}

	@Override
	public boolean parse(LogEvent logEvent) {
		try {
			Map<String, Object> parsed = gson.fromJson(logEvent.getOriginalText(), type);
			if (parsed != null && !parsed.isEmpty()) {
				logEvent.setFields(parsed);
				return true;
			}
		} catch(Exception e) {
			log.error("JSON parsing failed: {}", e.getMessage());
			logEvent.markAsError("JSON parsing failed: " + e.getMessage());
		}
		return false;
	}

}
