package org.keinus.logparser.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomThreadFactory implements ThreadFactory {
	private static final Logger LOGGER = LoggerFactory.getLogger(CustomThreadFactory.class);
	private final AtomicInteger threadNum = new AtomicInteger(0);
	private final String namePrefix;
	
	public CustomThreadFactory(String namePrefix) {
		this.namePrefix = namePrefix;
	}

	@Override
	public Thread newThread(Runnable r) {
		Thread thread = new Thread(r, String.format("%s-%d",
				this.namePrefix,
				this.threadNum.getAndIncrement()
				));
		thread.setDaemon(false);
		thread.setPriority(Thread.NORM_PRIORITY);
		LOGGER.info("Thread created: {}", thread.getName());
		return thread;
	}
}
