package org.keinus.logparser.output;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.keinus.logparser.interfaces.OutputAdapter;

public class BenchmarkAdapter extends OutputAdapter {
	private static final Logger LOGGER = LoggerFactory.getLogger( BenchmarkAdapter.class );
	AtomicInteger ai = new AtomicInteger();
	long startElapse = 0L;
	
	public BenchmarkAdapter(Map<String, String> obj) throws IOException {
		super(obj);
		LOGGER.info("Console Output Adapter created");
		ai.set(0);
	}

	public void send(Map<String, Object> json, String jsonString) {
		try {
			int count = ai.incrementAndGet();
			if(count == 1) {
				startElapse = System.currentTimeMillis();
			}
			if(count % 1000 == 0) {
				long endElapse = System.currentTimeMillis();
				double elapsedSeconds = (endElapse - startElapse) / 1000.0;
				int processedPerSecond = (int) (count / elapsedSeconds); // Calculate count per second
				LOGGER.info("{} processed per second: {}", processedPerSecond, count);
				startElapse = endElapse; // Reset start time for next measurement
				ai.set(0);
			}
		} catch (Exception e) {
			LOGGER.error(e.getMessage());
		}
	}

	@Override
	public void close() throws IOException {
		LOGGER.info("Console Output Adapter closed"); 
	}

	@Override
	public void flush() {
		// nothing
	}

}
