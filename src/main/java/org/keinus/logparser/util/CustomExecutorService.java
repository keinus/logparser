package org.keinus.logparser.util;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import org.keinus.logparser.config.ApplicationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class CustomExecutorService implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger( CustomExecutorService.class );
    
    private ExecutorService executorService = null;
    private CustomThreadFactory threadFactory = null;

    public CustomExecutorService(Environment environment, ApplicationProperties appProp) {
		threadFactory = new CustomThreadFactory(environment.getProperty("spring.application.name"));
		executorService = Executors.newFixedThreadPool(appProp.getThreads(), threadFactory);
	}

    public boolean execute(Runnable command) {
        if(command == null || executorService.isShutdown())
            return false;
        try {
            executorService.execute(command);
        } catch(RejectedExecutionException e) {
            return false;
        }
        return true;
	}

    @Override
    public void close() throws IOException {
        executorService.shutdownNow();
		int count = 0;
		while(!executorService.isTerminated()) {
            executorService.shutdownNow();
            
            count++;
            if(count > 10) {
                LOGGER.info("attempt to close, but still alive. closing.");
                break;
            }
		}
    }
}
