package org.keinus.logparser.components;

import java.util.concurrent.atomic.AtomicBoolean;

import org.keinus.logparser.config.ApplicationProperties;
import org.keinus.logparser.config.OutputAdapterConfig;
import org.keinus.logparser.core.dispatch.OutputAdapterProcedure;
import org.keinus.logparser.core.dispatch.OutputFactory;
import org.keinus.logparser.core.interfaces.OutputAdapter;
import org.keinus.logparser.core.util.MergingHashMap;
import org.keinus.logparser.core.util.ThreadManager;
import org.keinus.logparser.core.util.ThreadUtil;
import org.keinus.logparser.core.schema.LogEvent;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;


/**
 * 출력 어댑터들을 관리하고 메시지 전송을 처리하는 컴포넌트입니다.
 * <p>
 * 이 클래스는 애플리케이션 설정({@link ApplicationProperties})을 바탕으로
 * 다양한 출력 어댑터({@link OutputAdapter})를 생성하고, 각 어댑터에 대한
 * 처리 절차({@link OutputAdapterProcedure})를 설정합니다.
 * <p>
 * {@link OutputFactory}를 통해 어댑터를 동적으로 생성하며, {@link MergingHashMap}을 사용하여
 * 메시지 타입별로 하나 이상의 출력 절차를 매핑할 수 있습니다.
 * `processOutputAdapter` 메서드는 {@link MessageDispatcher}의 레거시 출력 큐에서
 * 메시지를 가져와 해당 메시지 타입에 맞는 모든 출력 절차에 전달하는 역할을 합니다.
 *
 * @see org.keinus.logparser.config.ApplicationProperties
 * @see org.keinus.logparser.dispatch.OutputFactory
 * @see org.keinus.logparser.dispatch.OutputAdapterProcedure
 * @see org.keinus.logparser.interfaces.OutputAdapter
 * @see org.keinus.logparser.core.util.MergingHashMap
 */
@Slf4j
@Component
public class OutputAdaptorComponent {
    /**
	 * 스레드 실행 지속 여부
	 */
    private static final AtomicBoolean running = new AtomicBoolean(true);

    /**
	 * output adapter
	 */
    private MergingHashMap<OutputAdapterProcedure> outputMap = new MergingHashMap<>();

    private final ThreadManager threadManager;
    private final MessageDispatcher dispatcher;
  
    public OutputAdaptorComponent(ApplicationProperties appProp, ThreadManager threadManager, MessageDispatcher dispatcher) {
        this.threadManager = threadManager;
        this.dispatcher = dispatcher;

        for (OutputAdapterConfig config : appProp.getOutput()) {
            try {
                OutputAdapter adapter = OutputFactory.getOutputAdapter(config);
                OutputAdapterProcedure procedure = new OutputAdapterProcedure(adapter);
                String msgType = adapter.getType();
                outputMap.put(msgType, procedure);
                log.info("OutputAdapter {} registered", adapter.getClass().getSimpleName());
            } catch(Exception e) {
                log.error("OutputAdapter {} initialize error. {}", config.getType(), e.getMessage());
            }
        }
    }

    @PostConstruct
    public void startPipeline() {
        try {
            log.info("Starting Output Adaptor Component...");
            running.set(true);

            // Start individual adapter procedure threads
            int procedureCount = 0;
            for (String msgType : outputMap.getAllKeys()) {
                for (OutputAdapterProcedure procedure : outputMap.get(msgType)) {
                    threadManager.executeWithName("OutputProcedure-" + (msgType != null ? msgType : "all") + "-" + procedureCount, procedure);
                    procedureCount++;
                    log.info("Started output procedure for message type: {}", msgType != null ? msgType : "all");
                }
            }

            // Start output message processor
            threadManager.executeWithName("processOutputAdapter", this::processOutputAdapter);
            log.info("Started output message processor and {} output procedures", procedureCount);
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

    private void processOutputAdapter() {
        while (running.get()) {
            LogEvent logEvent = dispatcher.getOutputMsg();
            if(logEvent == null) {
                ThreadUtil.sleep(100);
                continue;
            }

            String messageType = logEvent.getMessageType();
            var procedures = outputMap.get(messageType);
            if(!procedures.isEmpty()) {
                for(var proc : procedures) {
                    proc.enqueue(logEvent);
                    log.debug("Enqueued log event to output procedure for type: {}", messageType);
                }
            } else {
                log.error("No output procedures found for message type: {}", messageType);
            }
        }
    }

    public void close() {
        log.info("Shutting down OutputAdaptorComponent...");

        // 실행 중단 플래그 설정
        running.set(false);

        // 모든 output adapter procedure 종료
        for (String msgType : outputMap.getAllKeys()) {
            for (OutputAdapterProcedure procedure : outputMap.get(msgType)) {
                try {
                    procedure.close();
                    log.info("Closed output procedure for message type: {}", msgType != null ? msgType : "all");
                } catch (Exception e) {
                    log.error("Error closing output procedure for type {}: {}", msgType, e.getMessage());
                }
            }
        }

        // ThreadManager 종료
        try {
            threadManager.shutdownAllThreads();
            log.info("All output adapter threads have been shut down");
        } catch (Exception e) {
            log.error("Error shutting down thread manager: {}", e.getMessage());
        }

        // outputMap 정리
        outputMap.clear();
        log.info("OutputAdaptorComponent shutdown completed");
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down InputAdaptorComponent...");
            running.set(false);
        }));
    }
}
