package org.keinus.logparser.infrastructure.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 스레드를 이름으로 관리하고 모니터링하는 {@link ThreadPoolExecutor} 확장 클래스입니다.
 */
public class ThreadManager extends ThreadPoolExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadManager.class);
    
    private final Map<String, Thread> threads = new ConcurrentHashMap<>();
    private final Map<Runnable, String> taskNames = new ConcurrentHashMap<>();

    public ThreadManager(String threadName) {
        super(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, 
              new LinkedBlockingQueue<>(), new CustomThreadFactory(threadName));
    }

    public ThreadManager(String threadName, int nThreads) {
        super(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS, 
              new LinkedBlockingQueue<>(), new CustomThreadFactory(threadName));
    }

    /**
     * 지정된 이름으로 스레드를 실행합니다.
     *
     * @param threadName 스레드 이름
     * @param task 실행할 작업
     * @throws IllegalArgumentException threadName이 null이거나 비어있는 경우
     * @throws IllegalStateException 동일한 이름의 스레드가 이미 실행 중인 경우
     */
    public void executeWithName(String threadName, Runnable task) {
        if (threadName == null || threadName.trim().isEmpty()) {
            throw new IllegalArgumentException("Thread name cannot be null or empty");
        }
        
        // 이미 실행 중인지 확인 (taskNames에 pending 상태도 포함)
        synchronized (taskNames) {
            if (threads.containsKey(threadName) || taskNames.containsValue(threadName)) {
                throw new IllegalStateException(
                    String.format("Thread with name '%s' is already running or pending", threadName));
            }
            taskNames.put(task, threadName);
        }

        try {
            this.execute(task);
        } catch (Exception e) {
            taskNames.remove(task);
            LOGGER.error("Failed to submit task '{}': {}", threadName, e.getMessage());
            throw e;
        }
    }

    /**
     * 지정된 이름의 스레드를 중지하고 완전히 종료될 때까지 대기합니다.
     */
    public void stopThread(String threadName) {
        Thread thread = threads.get(threadName);
        if (thread == null) {
            LOGGER.debug("Thread not found: {}", threadName);
            return;
        }

        if (!thread.isAlive()) {
            LOGGER.debug("Thread already stopped or not active: {}", threadName);
            return;
        }

        LOGGER.info("Interrupting thread: {}", threadName);
        thread.interrupt();

        try {
            LOGGER.debug("Waiting for thread to finish: {}", threadName);

            // 스레드가 1~9초 사이에 종료되므로 최대 10초 대기하며 1초마다 확인
            long timeoutMs = 10000;
            long intervalMs = 1000;
            long deadline = System.currentTimeMillis() + timeoutMs;

            while (thread.isAlive() && System.currentTimeMillis() < deadline) {
                thread.join(intervalMs);
                if (thread.isAlive()) {
                    LOGGER.debug("Thread {} is still running... waiting", threadName);
                }
            }

            if (thread.isAlive()) {
                LOGGER.warn("Thread {} did not finish within {} ms", threadName, timeoutMs);
            } else {
                LOGGER.info("Thread {} has been successfully stopped", threadName);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Interrupted while waiting for thread {} to finish", threadName, e);
        }
    }

    /**
     * 지정된 접두사로 시작하는 모든 스레드를 중지합니다.
     *
     * @param prefix 스레드 이름 접두사
     * @return 중지 요청된 스레드 수
     */
    public int stopThreadsStartingWith(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (String threadName : threads.keySet()) {
            if (threadName.startsWith(prefix)) {
                stopThread(threadName);
                count++;
            }
        }
        LOGGER.info("Stopped {} threads starting with '{}'", count, prefix);
        return count;
    }

    /**
     * 모든 스레드가 종료될 때까지 대기합니다.
     */
    public void waitForAllThreadsToFinish() throws InterruptedException {
        for (Thread thread : new ArrayList<>(threads.values())) {
            if (thread.isAlive()) {
                thread.join();
            }
        }
    }

    /**
     * 현재 실행 중인 스레드의 이름 목록을 반환합니다.
     */
    public List<String> getActiveThreads() {
        List<String> activeThreads = new ArrayList<>();
        threads.forEach((name, thread) -> {
            if (thread.isAlive()) {
                activeThreads.add(name);
            }
        });
        return Collections.unmodifiableList(activeThreads);
    }

    /**
     * 모든 스레드 정보를 반환합니다.
     */
    public List<ThreadInfo> getAllThreadInfo() {
        List<ThreadInfo> infoList = new ArrayList<>();
        threads.forEach((name, thread) -> infoList.add(createThreadInfo(name, thread)));
        return Collections.unmodifiableList(infoList);
    }

    /**
     * 특정 이름의 스레드 정보를 반환합니다.
     */
    public ThreadInfo getThreadInfo(String threadName) {
        Thread thread = threads.get(threadName);
        return thread != null ? createThreadInfo(threadName, thread) : null;
    }

    private ThreadInfo createThreadInfo(String name, Thread thread) {
        return new ThreadInfo(name, thread.threadId(), thread.getState(), 
                              thread.isAlive(), thread.isInterrupted());
    }

    public void shutdownAllThreads() {
        LOGGER.info("Shutting down all threads. Active: {}", threads.size());

        threads.values().forEach(thread -> {
            if (thread.isAlive()) {
                thread.interrupt();
            }
        });

        this.shutdown();
        try {
            if (!this.awaitTermination(30, TimeUnit.SECONDS)) {
                LOGGER.warn("ThreadPool did not terminate gracefully, forcing shutdown");
                this.shutdownNow();
                if (!this.awaitTermination(10, TimeUnit.SECONDS)) {
                    LOGGER.error("ThreadPool did not terminate after forced shutdown");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        LOGGER.info("All threads have been shut down");
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);
        
        String threadName = taskNames.get(r);
        if (threadName != null) {
            t.setName(threadName);
            threads.put(threadName, t);
        } else {
            threads.put(t.getName(), t);
        }
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        
        String threadName = Thread.currentThread().getName();
        threads.remove(threadName);
        taskNames.remove(r);

        if (t != null) {
            LOGGER.error("Task failed in thread '{}': {}", threadName, t.getMessage(), t);
        }
    }

    @Override
    protected void terminated() {
        super.terminated();
        threads.clear();
        taskNames.clear();
        LOGGER.info("ThreadPool terminated");
    }

    /**
     * 스레드 정보를 담는 불변 record
     */
    public record ThreadInfo(String name, long id, Thread.State state, 
                             boolean alive, boolean interrupted) {
        @Override
        public String toString() {
            return String.format("ThreadInfo{name='%s', id=%d, state=%s, alive=%s, interrupted=%s}",
                    name, id, state, alive, interrupted);
        }
    }
}