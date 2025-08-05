package org.keinus.logparser.output;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.keinus.logparser.interfaces.OutputAdapter;

public class ConsoleOutputAdapter extends OutputAdapter {
	private static final Logger LOGGER = LoggerFactory.getLogger( ConsoleOutputAdapter.class );
		
	public ConsoleOutputAdapter(Map<String, String> obj) throws IOException {
		super(obj);
		LOGGER.info("Console Output Adapter created");
	}

	public void send(Map<String, Object> json, String jsonString) {
		try {
			synchronized( this ) {
				LOGGER.info(jsonString);
			}
		} catch (Exception e) {
			LOGGER.error(e.getMessage());
		}
	}

	@Override
	public void close() throws IOException {
		// 불필요
	}
}
