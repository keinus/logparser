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
 * 애플리케이션의 스레드를 중앙에서 관리하고 모니터링하는 확장된 {@link ThreadPoolExecutor} 입니다.
 * <p>
 * 이 클래스는 일반적인 스레드 풀 기능에 더하여, 각 스레드에 의미 있는 이름을 부여하고
 * 실행 중인 스레드를 추적하는 기능을 제공하여 디버깅과 관리를 용이하게 합니다.
 * {@link CustomThreadFactory}를 사용하여 스레드 이름을 생성합니다.
 * <p>
 * 주요 기능:
 * <ul>
 * <li><b>스레드 이름 지정 실행:</b> {@code executeWithName(name, task)} 메서드를 통해
 * 실행될 작업에 특정 스레드 이름을 동적으로 할당할 수 있습니다.</li>
 * <li><b>스레드 추적:</b> 실행 전후({@code beforeExecute}, {@code afterExecute})에 스레드를
 * 내부 맵에 등록하고 제거하여 현재 실행 중인 스레드를 추적합니다.</li>
 * <li><b>스레드 중지:</b> 이름으로 특정 스레드를 찾아 인터럽트를 발생시킬 수 있습니다.
 * ({@code stopThread})</li>
 * <li><b>활성 스레드 조회:</b> 현재 활성 상태인 스레드들의 이름 목록을 조회할 수 있습니다.</li>
 * </ul>
 *
 * @see java.util.concurrent.ThreadPoolExecutor
 * @see CustomThreadFactory
 */
public class ThreadManager extends ThreadPoolExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadManager.class);
    private final Map<String, Thread> threads = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> pendingThreads = new ConcurrentHashMap<>();
    private final Map<Runnable, String> taskToThreadName = new ConcurrentHashMap<>();

    public ThreadManager(String threadName) {
        super(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), new CustomThreadFactory(threadName));
    }

    public ThreadManager(String threadName, int nThreads) {
        super(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), new CustomThreadFactory(threadName));
    }

    /**
     * 지정된 이름으로 스레드를 실행합니다.
     * <p>
     * 동시성 안전성: putIfAbsent를 사용한 원자적 체크-앤-실행으로 race condition 방지
     *
     * @param threadName 실행할 스레드의 이름
     * @param task 실행할 작업
     * @throws IllegalArgumentException threadName이 null이거나 비어있는 경우
     * @throws IllegalStateException 동일한 이름의 스레드가 이미 실행 중이거나 pending 상태인 경우
     */
    public void executeWithName(String threadName, Runnable task) {
        if (threadName == null || threadName.trim().isEmpty()) {
            throw new IllegalArgumentException("Thread name cannot be null or empty");
        }

        // putIfAbsent를 사용한 원자적 체크-앤-실행
        Boolean previous = pendingThreads.putIfAbsent(threadName, Boolean.TRUE);
        if (previous != null) {
            throw new IllegalStateException(
                String.format("Thread with name '%s' is already running or pending", threadName)
            );
        }

        Runnable namedTask = () -> {
            Thread current = Thread.currentThread();
            current.setName(threadName);

            try {
                task.run();
            } finally {
                // 완료 시 pending 상태 제거
                pendingThreads.remove(threadName);
            }
        };

        try {
            // Store mapping from task to custom thread name BEFORE executing
            taskToThreadName.put(namedTask, threadName);

            LOGGER.info(">>> DEBUG: Before execute '{}' - PoolSize: {}, ActiveCount: {}, CorePoolSize: {}, MaxPoolSize: {}, QueueSize: {}",
                    threadName, this.getPoolSize(), this.getActiveCount(), this.getCorePoolSize(),
                    this.getMaximumPoolSize(), this.getQueue().size());
            this.execute(namedTask);
            LOGGER.info(">>> DEBUG: After execute '{}' - PoolSize: {}, ActiveCount: {}, CorePoolSize: {}, MaxPoolSize: {}, QueueSize: {}",
                    threadName, this.getPoolSize(), this.getActiveCount(), this.getCorePoolSize(),
                    this.getMaximumPoolSize(), this.getQueue().size());
        } catch (Exception e) {
            // 제출 실패 시 정리
            taskToThreadName.remove(namedTask);
            pendingThreads.remove(threadName);
            LOGGER.error("Failed to submit task with thread name '{}': {}", threadName, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 지정된 이름의 스레드를 중지합니다.
     *
     * @param threadName 중지할 스레드의 이름
     * @throws IllegalArgumentException 해당 이름의 스레드를 찾을 수 없는 경우
     */
    public void stopThread(String threadName) {
        Thread thread = threads.get(threadName);
        if (thread != null && thread.isAlive()) {
            LOGGER.info("Interrupting thread: {}", threadName);
            thread.interrupt();
        } else {
            String message = String.format("No active thread found with the name: %s", threadName);
            LOGGER.error(message);
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 모든 스레드가 종료될 때까지 대기합니다.
     *
     * @throws InterruptedException 대기 중 인터럽트가 발생한 경우
     */
    public void waitForAllThreadsToFinish() throws InterruptedException {
        List<Thread> threadList = new ArrayList<>(threads.values());
        for (Thread thread : threadList) {
            if (thread.isAlive()) {
                LOGGER.debug("Waiting for thread to finish: {}", thread.getName());
                thread.join();
            }
        }
    }

    /**
     * 현재 실행 중인 스레드의 이름 목록을 반환합니다.
     *
     * @return 실행 중인 스레드 이름 목록
     */
    public List<String> getActiveThreads() {
        List<String> activeThreads = new ArrayList<>();
        for (Map.Entry<String, Thread> entry : threads.entrySet()) {
            if (entry.getValue().isAlive()) {
                activeThreads.add(entry.getKey());
            }
        }
        return Collections.unmodifiableList(activeThreads);
    }

    /**
     * 현재 관리 중인 모든 스레드의 정보를 반환합니다.
     *
     * @return 스레드 정보 목록 (이름, 상태, ID 등)
     */
    public List<ThreadInfo> getAllThreadInfo() {
        List<ThreadInfo> infoList = new ArrayList<>();
        for (Map.Entry<String, Thread> entry : threads.entrySet()) {
            Thread thread = entry.getValue();
            infoList.add(new ThreadInfo(
                entry.getKey(),
                thread.threadId(),
                thread.getState(),
                thread.isAlive(),
                thread.isInterrupted()
            ));
        }
        return Collections.unmodifiableList(infoList);
    }

    /**
     * 특정 이름의 스레드 정보를 반환합니다.
     *
     * @param threadName 조회할 스레드 이름
     * @return 스레드 정보, 없으면 null
     */
    public ThreadInfo getThreadInfo(String threadName) {
        Thread thread = threads.get(threadName);
        if (thread == null) {
            return null;
        }
        return new ThreadInfo(
            threadName,
            thread.threadId(),
            thread.getState(),
            thread.isAlive(),
            thread.isInterrupted()
        );
    }

    /**
     * 스레드 정보를 담는 불변 클래스
     */
    public static class ThreadInfo {
        private final String name;
        private final long id;
        private final Thread.State state;
        private final boolean alive;
        private final boolean interrupted;

        public ThreadInfo(String name, long id, Thread.State state, boolean alive, boolean interrupted) {
            this.name = name;
            this.id = id;
            this.state = state;
            this.alive = alive;
            this.interrupted = interrupted;
        }

        public String getName() {
            return name;
        }

        public long getId() {
            return id;
        }

        public Thread.State getState() {
            return state;
        }

        public boolean isAlive() {
            return alive;
        }

        public boolean isInterrupted() {
            return interrupted;
        }

        @Override
        public String toString() {
            return String.format("ThreadInfo{name='%s', id=%d, state=%s, alive=%s, interrupted=%s}",
                name, id, state, alive, interrupted);
        }
    }

    public void shutdownAllThreads() {
        LOGGER.info("Shutting down all threads. Active threads: {}", getActiveThreads().size());

        // 모든 활성 스레드에 인터럽트 발생
        for (Thread thread : threads.values()) {
            if (thread.isAlive()) {
                LOGGER.info("Interrupting thread: {}", thread.getName());
                thread.interrupt();
            }
        }

        // ThreadPool 종료 시작
        this.shutdown();

        try {
            // 최대 30초 대기
            if (!this.awaitTermination(30, TimeUnit.SECONDS)) {
                LOGGER.warn("ThreadPool did not terminate gracefully, forcing shutdown");
                this.shutdownNow();

                // 강제 종료 후 추가 대기
                if (!this.awaitTermination(10, TimeUnit.SECONDS)) {
                    LOGGER.error("ThreadPool did not terminate after forced shutdown");
                }
            }
        } catch (InterruptedException e) {
            LOGGER.error("Interrupted while waiting for thread termination", e);
            Thread.currentThread().interrupt();
        }

        LOGGER.info("All threads have been shut down");
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);

        // Look up the custom thread name from the mapping
        String customThreadName = taskToThreadName.get(r);

        if (customThreadName != null) {
            // Use the custom name for registration and rename the thread
            t.setName(customThreadName);
            threads.put(customThreadName, t);
            LOGGER.info(">>> DEBUG: beforeExecute - Thread '{}' (custom name) registered in ThreadManager, total threads: {}",
                        customThreadName, threads.size());
        } else {
            // Fallback to factory name if no mapping found
            String factoryThreadName = t.getName();
            threads.put(factoryThreadName, t);
            LOGGER.info(">>> DEBUG: beforeExecute - Thread '{}' (factory name) registered in ThreadManager, total threads: {}",
                        factoryThreadName, threads.size());
        }
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        Thread current = Thread.currentThread();
        String threadName = current.getName();
        threads.remove(threadName);
        pendingThreads.remove(threadName);  // pending 스레드도 제거
        taskToThreadName.remove(r);  // Clean up the mapping to prevent memory leak

        if (t != null) {
            LOGGER.error("Task failed in thread '{}': {}", threadName, t.getMessage(), t);
        } else {
            LOGGER.debug("Task completed by thread '{}'", threadName);
        }
    }

    @Override
    public void terminated() {
        super.terminated();
        threads.clear();
        LOGGER.info("ThreadPool has been terminated");
    }
}