package org.keinus.logparser.parser;

import java.lang.reflect.Type;
import java.util.Map;

import org.keinus.logparser.interfaces.IParser;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class JsonParser implements IParser {
    Gson gson = new Gson();
    Type type = new TypeToken<Map<String, Object>>() {}.getType();

    @Override
	public void init(Object param) {
		// 초기화 없음.
	}

	@Override
	public Map<String, Object> parse(String message) {
		return gson.fromJson(message, type);
	}
}
