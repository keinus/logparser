package org.keinus.logparser.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *     <li><b>스레드 이름 지정 실행:</b> {@code executeWithName(name, task)} 메서드를 통해
 *         실행될 작업에 특정 스레드 이름을 동적으로 할당할 수 있습니다.</li>
 *     <li><b>스레드 추적:</b> 실행 전후({@code beforeExecute}, {@code afterExecute})에 스레드를
 *         내부 맵에 등록하고 제거하여 현재 실행 중인 스레드를 추적합니다.</li>
 *     <li><b>스레드 중지:</b> 이름으로 특정 스레드를 찾아 인터럽트를 발생시킬 수 있습니다. ({@code stopThread})</li>
 *     <li><b>활성 스레드 조회:</b> 현재 활성 상태인 스레드들의 이름 목록을 조회할 수 있습니다.</li>
 * </ul>
 *
 * @see java.util.concurrent.ThreadPoolExecutor
 * @see CustomThreadFactory
 */
public class ThreadManager extends ThreadPoolExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadManager.class);
    private final Map<String, Thread> threads = new ConcurrentHashMap<>();

    public ThreadManager(String threadName) {
        super(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), new CustomThreadFactory(threadName));
    }

    public ThreadManager(String threadName, int nThreads) {
        super(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), new CustomThreadFactory(threadName));
    }

    public void executeWithName(String threadName, Runnable task) {
        Runnable namedTask = () -> {
            Thread.currentThread().setName(threadName);
            task.run();
        };
        this.execute(namedTask);
        LOGGER.info("Task submitted with thread name: {}", threadName);
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
        threads.put(t.getName(), t);
        LOGGER.info("Thread {} is starting task: {}", t.getName(), r);
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        Thread current = Thread.currentThread();
        threads.remove(current.getName());
        if (t != null) {
            LOGGER.error("Task completed by thread: {}, with error: {}", current.getName(), t.getMessage());
        } else {
            LOGGER.info("Task completed by thread: {}", current.getName());
        }
    }

    @Override
    public void terminated() {
        super.terminated();
        LOGGER.info("ThreadPool has been terminated");
    }
}