package org.keinus.logparser.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public class CustomExecutorService extends ThreadPoolExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomExecutorService.class);
     private final Map<String, Thread> threads = new ConcurrentHashMap<>();

    public CustomExecutorService(String threadName) {
        super(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(), new CustomThreadFactory(threadName));
        super.setThreadFactory(new CustomThreadFactory(threadName));
    }

    public CustomExecutorService(String threadName, int nThreads) {
        super(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), new CustomThreadFactory(threadName));
        super.setThreadFactory(new CustomThreadFactory(threadName));
    }

    public void stopThread(String threadName) {
        Thread thread = threads.get(threadName);
        if (thread != null) {
            thread.interrupt();
        } else {
            LOGGER.error("No thread found with the name {}", threadName);
        }
    }

    public void waitForAllThreadsToFinish() throws InterruptedException {
        for (Thread thread : threads.values()) {
            thread.join();
        }
    }

    public List<String> getActiveThreads() {
        return threads.entrySet().stream()
                .filter(entry -> entry.getValue().isAlive())
                .map(Map.Entry::getKey)
                .toList();
    }

    @Override
    public void close() {
        if (!this.isShutdown()) {
            this.shutdown();
            try {
                if (!this.awaitTermination(30, TimeUnit.SECONDS)) {
                    this.shutdownNow();
                    if (!this.awaitTermination(30, TimeUnit.SECONDS))
                        LOGGER.warn("Executor service did not terminate after repeated attempts.");
                }
            } catch (InterruptedException ie) {
                this.shutdownNow();
                Thread.currentThread().interrupt(); // Interrupt status restore
            }
        }
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);
        threads.put(t.getName(), t);
        LOGGER.info("Thread {} is starting task: {}", t.getName(), r);
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        Thread current = Thread.currentThread();
        threads.remove(current.getName());
        LOGGER.info("Task completed by thread: {}, {}", current.getName(), t.getMessage());
    }
    
    @Override
    public void terminated() {
        super.terminated();
        LOGGER.info("ThreadPool has been terminated");
    }
    
}