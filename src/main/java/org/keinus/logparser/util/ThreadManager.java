package org.keinus.logparser.util;

import java.util.HashMap;
import java.util.Map;

public class ThreadManager {

    private Map<String, Thread> threads = new HashMap<>();

    /**
     * 스레드를 이름과 함께 실행합니다.
     *
     * @param threadName 스레드 이름
     * @param runnable   실행할 Runnable 객체
     */
    public void startThread(String threadName, Runnable runnable) {
        Thread thread = new Thread(runnable, threadName);
        threads.put(threadName, thread);
        thread.start();
    }

    /**
     * 특정 이름의 스레드를 중지합니다.
     *
     * @param threadName 중지할 스레드 이름
     */
    public void stopThread(String threadName) {
        Thread thread = threads.get(threadName);
        if (thread != null) {
            thread.interrupt();
        }
    }

    /**
     * 모든 스레드를 일괄 중지합니다.
     */
    public void stopAllThreads() {
        threads.forEach((name, thread) -> thread.interrupt());
    }

    /**
     * 특정 이름의 스레드 실행 상태를 확인합니다.
     *
     * @param threadName 확인할 스레드 이름
     * @return 스레드 실행 상태 (true: 실행 중, false: 중지됨)
     */
    public boolean isThreadRunning(String threadName) {
        Thread thread = threads.get(threadName);
        return thread != null && thread.isAlive();
    }

    /**
     * 모든 스레드의 실행 상태를 확인합니다.
     *
     * @return 스레드 이름과 실행 상태를 담은 Map
     */
    public Map<String, Boolean> getAllThreadStates() {
        Map<String, Boolean> threadStates = new HashMap<>();
        threads.forEach((name, thread) -> threadStates.put(name, thread.isAlive()));
        return threadStates;
    }
}