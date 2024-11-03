package org.keinus.logparser.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomThreadFactory implements ThreadFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger( CustomThreadFactory.class );
	private final AtomicInteger threadNum = new AtomicInteger( 0 );
	private final String factoryName;
	private List<Thread> threads = new ArrayList<>();
	
	public CustomThreadFactory( String factoryName ) {
		this.factoryName = factoryName;
	}
	
	@Override
	public Thread newThread(Runnable r) {
		Thread thread = new Thread( r, String.format( "%s-%d-%s", 
													this.factoryName, 
													this.threadNum.getAndIncrement(), 
													r.getClass().getSimpleName() ) );
		thread.setDaemon( false );
		thread.setPriority( Thread.NORM_PRIORITY );
		threads.add(thread);
		LOGGER.info("Thread created: {}", thread.getName());
		return thread;
	}
	
	public String getThreadsByString() {
		StringBuilder buffer = new StringBuilder();
		for(Thread thread : threads) {
			String name = thread.getName();
			String stat = thread.getState().toString();
			int priority = thread.getPriority();
			String text = String.format("Name: %s,Stat: %s, Priority: %d", name, stat, priority);
			buffer.append(text);
			buffer.append(System.lineSeparator());
		}
		return buffer.toString();
	}
}
