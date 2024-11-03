package org.keinus.logparser.parser;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.keinus.logparser.interfaces.IParser;


public class HttpParser implements IParser {
	@Override
	public void init(Object param) {
	}

	@Override
	public Map<String, Object> parse(String message) {
		Map<String, Object> headers = new HashMap<>();
		Map<String, Object> retval = new HashMap<>();
		StringBuilder sb = new StringBuilder();
		
		try(BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8 ))))) {
			String line;
			// Read request line
			line = br.readLine();

			while((line=br.readLine()) != null) {
				if(line.equals(""))
					break;
				if(line.contains(":")) {
					String[] split = line.split(":");
					headers.put(split[0].toUpperCase().trim(), split[1].toUpperCase().trim());
				}
			}

			while((line=br.readLine()) != null) {
				sb.append(line);
				sb.append(System.getProperty("line.separator"));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		retval.put("headers", headers);
		retval.put("body", sb.toString());
		return retval;
	}
}
