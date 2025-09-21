package org.keinus.logparser.components;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import org.keinus.logparser.config.ApplicationProperties;
import org.keinus.logparser.core.dispatch.OutputAdapterProcedure;
import org.keinus.logparser.core.dispatch.ParseService;
import org.keinus.logparser.core.dispatch.TransformService;
import org.keinus.logparser.core.util.ThreadManager;
import org.keinus.logparser.core.schema.LogEvent;


/**
 * MessageDispatcher 클래스는 로그 메시지를 입력 어댑터로부터 받아와서 파싱하고,  
 * 변환 후 출력 어댑터를 통해 전송하는 역할을 합니다.  
 * 이 클래스는 Spring Framework의 @Service 애노테이션으로 선언되어 있으며,  
 * 싱글톤 스코프로 관리됩니다.
 */
@Slf4j
@Service
public class MessageDispatcher {
    /**
     * 입력 메시지를 저장하는 BlockingQueue. 큐 크기는 10000으로 제한됩니다.
     */
    private final BlockingQueue<LogEvent> globalMessageQueue;

    /**
     * 출력 메시지를 저장하는 BlockingQueue의 HashMap. 큐 크기는 10000으로 제한됩니다.
     */
    private final BlockingQueue<LogEvent> outputMessageQueue;

	/**
	 * 스레드 실행 지속 여부
	 */
    private static final AtomicBoolean running = new AtomicBoolean(true);

    /**
     * 스레드 관리자
     */
    private ThreadManager threadManager;

    /**
     * ParseService 인스턴스
     */
    private ParseService parseService = null;

    /**
     * TransformService 인스턴스
     */
    private TransformService transformService = null;

    int threads = 1;

    /**
     * MessageDispatcher 생성자.
     * threadManager와 ApplicationProperties를 주입받아 초기화합니다.
     * 또한, 입력 어댑터와 출력 어댑터를 초기화하고,
     * parseAndTransform 메서드를 별도의 스레드로 실행시킵니다.
     *
     * @param threadManager        사용자 정의 thread 관리자
     * @param appProp              ApplicationProperties 설정
     */
    public MessageDispatcher(
            ThreadManager threadManager,
            ParseService parseService,
            TransformService transformService,
            ApplicationProperties applicationProperties,
            @Value("${log.message.queue-size:10000}") int queueSize) {

        this.threadManager = threadManager;
        this.globalMessageQueue = new LinkedBlockingQueue<>(queueSize);
        this.outputMessageQueue = new LinkedBlockingQueue<>(queueSize);

        this.parseService = parseService;
        this.transformService = transformService;
        this.threads = applicationProperties.getParserThreads();
        log.info("MessageDispatcher initialized");
    }

    /** 
     * 메시지 디스패처를 초기화하고 파서 스레드를 시작
     * 메시지 디스패처를 초기화하고 파서 스레드를 시작합니다.
     * - 실행 상태 플래그 {@code running}을 활성화합니다.
     * - {@link ThreadManager}을 통해 지정된 수의 스레드를 생성하고, 각 스레드에서 {@link #parseAndTransform()} 작업을 병렬 실행합니다.
     * - 디스패처 시작 완료 시 INFO 레벨로 파서 스레드 수를 로깅합니다.
     *
     * @see ThreadManager#execute(Runnable)
     * @see Logger#info(String, Object...)
    */
    @PostConstruct
    public void startPipeline() {
        try {
            running.set(true);
            for (int i = 0; i < threads; i++) {
                threadManager.execute(this::parseAndTransform);
            }
            log.info("MessageDispatcher started with {} parser threads", threads);
        } catch (Exception e) {
            log.error("Failed to initialize input adapters.", e);
            throw new RuntimeException("ETL Pipeline startup failed", e);
        }
    }

    @PreDestroy
    public void stopPipeline() {
        try {
            close();
            log.info("All Output Adapters stopped successfully");
        } catch (Exception e) {
            log.error("Error during ETL Pipeline shutdown", e);
        }
    }

	/**
	 * MessageDispatcher를 닫고 리소스를 해제하는 메서드.
	 * 입력 메시지 큐에 있는 모든 메시지를 지우어 메모리나 자원을 해제합니다.
	 * 사용자 정의 실행 서비스를 닫아서 모든 실행 중인 작업을 중단하고 관련된 리소스를 해제합니다.
	 * @throws IOException I/O 오류가 발생할 경우 던지는 예외
	 */
    public void close() throws IOException {
        globalMessageQueue.clear();
        this.threadManager.shutdownAllThreads();
        log.info("Message Dispatcher closed");
    }

    public void parseAndTransform() {
        while (running.get()) {
            try {
                LogEvent logEvent = globalMessageQueue.take();
                if (logEvent != null) {
                    processLogEvent(logEvent);
                }
            } catch (InterruptedException e) {
                log.error("Interrupted while waiting for message in class {} method {}", this.getClass().getName(), Thread.currentThread().getStackTrace()[2].getMethodName());
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.error("Error processing log event", e);
            }

		}
	}

    private void processLogEvent(LogEvent logEvent) {
        try {
            log.debug("Processing log event of type: {}", logEvent.getMessageType());

            // 파싱 단계
            boolean parseResult = parseService.parse(logEvent);
            if (parseResult) {
                logEvent.markAsParsed();

                // 변환 단계
                boolean transformResult = transformService.transform(logEvent);
                if (transformResult) {
                    logEvent.markAsTransformed();
                    boolean queued = outputMessageQueue.offer(logEvent);
                    log.debug("Log event processed and queued: {}, queue size: {}", queued, outputMessageQueue.size());
                } else {
                    log.debug("Log event filtered out by transform service");
                }
            } else {
                log.debug("Log event parsing failed");
                logEvent.markAsError("Parsing failed");
            }
        } catch (Exception e) {
            log.error("Error processing log event: {}", logEvent, e);
            logEvent.markAsError("Processing error: " + e.getMessage());
        }
    }

    public boolean putGlobalMsg(LogEvent logEvent) {
        return globalMessageQueue.offer(logEvent);
    }

    public LogEvent getGlobalMsg() {
        return globalMessageQueue.poll();
    }

    public boolean putOutputMsg(LogEvent logEvent) {
        return outputMessageQueue.offer(logEvent);
    }

    public LogEvent getOutputMsg() {
        return outputMessageQueue.poll();
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down MessageDispatcher...");
            running.set(false);
        }));
    }
}