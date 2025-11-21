package org.keinus.logparser.application.pipeline;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.keinus.logparser.infrastructure.config.ApplicationProperties;
import org.keinus.logparser.domain.configuration.model.OutputAdapterConfig;
import org.keinus.logparser.domain.delivery.service.OutputFactory;
import org.keinus.logparser.domain.delivery.model.OutputAdapter;
import org.keinus.logparser.infrastructure.util.MergingHashMap;
import org.keinus.logparser.infrastructure.util.ThreadManager;
import org.keinus.logparser.infrastructure.util.ThreadUtil;
import org.keinus.logparser.domain.model.LogEvent;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonPrimitive;


/**
 * 출력 어댑터들을 관리하고 즉시 전송을 처리하는 통합 컴포넌트입니다.
 * <p>
 * 이 클래스는 애플리케이션 설정({@link ApplicationProperties})을 바탕으로
 * 다양한 출력 어댑터({@link OutputAdapter})를 생성하고 관리합니다.
 * <p>
 * 주요 기능:
 * <ul>
 *   <li>메시지 타입별 출력 어댑터 관리</li>
 *   <li>메시지 수신 시 즉시 전송</li>
 *   <li>출력 어댑터별 독립적인 처리</li>
 * </ul>
 *
 * @see org.keinus.logparser.config.ApplicationProperties
 * @see org.keinus.logparser.core.interfaces.OutputAdapter
 */
@Slf4j
@Component
public class OutputAdaptorComponent {

    private static final AtomicBoolean running = new AtomicBoolean(true);

    private final Gson gson;
    private final ThreadManager threadManager;
    private final MessageDispatcher dispatcher;

    // 출력 어댑터를 메시지 타입별로 그룹화
    private final MergingHashMap<OutputAdapter> outputAdapterMap = new MergingHashMap<>();

    /**
     * Instant를 ISO-8601 문자열로 직렬화하는 JsonSerializer
     */
    private static final JsonSerializer<Instant> instantSerializer = (src, typeOfSrc, context) -> {
        return new JsonPrimitive(src.toString());
    };

    /**
     * ISO-8601 문자열을 Instant로 역직렬화하는 JsonDeserializer
     */
    private static final JsonDeserializer<Instant> instantDeserializer = (json, typeOfT, context) -> {
        return Instant.parse(json.getAsString());
    };

    public OutputAdaptorComponent(ApplicationProperties appProp, ThreadManager threadManager, MessageDispatcher dispatcher) {
        this.threadManager = threadManager;
        this.dispatcher = dispatcher;

        // Gson 초기화 with Instant serializer/deserializer
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class, instantSerializer)
                .registerTypeAdapter(Instant.class, instantDeserializer)
                .create();

        initializeOutputAdapters(appProp);
    }

    private void initializeOutputAdapters(ApplicationProperties appProp) {
        for (OutputAdapterConfig config : appProp.getOutput()) {
            try {
                OutputAdapter adapter = OutputFactory.getOutputAdapter(config);
                String msgType = adapter.getType();
                outputAdapterMap.put(msgType, adapter);
                log.info("OutputAdapter {} registered for message type: {}",
                    adapter.getClass().getSimpleName(), msgType != null ? msgType : "all");
            } catch (Exception e) {
                log.error("OutputAdapter {} initialize error. {}", config.getType(), e.getMessage());
            }
        }
    }


    @PostConstruct
    public void startPipeline() {
        try {
            log.info("Starting Output Adaptor Component");
            running.set(true);

            // 메시지 처리 스레드 시작
            threadManager.executeWithName("processOutputAdapter", this::processOutputMessages);
            log.info("Started output message processor with {} output adapters",
                outputAdapterMap.getAllKeys().size());
        } catch (Exception e) {
            log.error("Failed to initialize output adapters.", e);
            throw new RuntimeException("Output Pipeline startup failed", e);
        }
    }

    @PreDestroy
    public void stopPipeline() {
        try {
            close();
            log.info("All Output Adapters stopped successfully");
        } catch (Exception e) {
            log.error("Error during Output Pipeline shutdown", e);
        }
    }

    /**
     * 메시지 처리 메인 루프 - 즉시 전송
     */
    private void processOutputMessages() {
        while (running.get()) {
            LogEvent logEvent = dispatcher.getOutputMsg();
            if (logEvent == null) {
                ThreadUtil.sleep(100);
                continue;
            }

            String messageType = logEvent.getMessageType();
            var adapters = outputAdapterMap.get(messageType);

            if (adapters.isEmpty()) {
                log.error("No output adapters found for message type: {}", messageType);
                continue;
            }

            // 즉시 전송
            sendToAdapters(adapters, logEvent);

            log.debug("Sent log event for message type: {}", messageType);
        }
    }

    /**
     * 어댑터들에게 로그 이벤트를 즉시 전송
     */
    private void sendToAdapters(Iterable<OutputAdapter> adapters, LogEvent event) {
        for (OutputAdapter adapter : adapters) {
            try {
                boolean addOriginText = adapter.isAddOriginText();
                Map<String, Object> outputMap = event.toOutputMap(addOriginText);
                String jsonString = gson.toJson(outputMap);
                adapter.send(outputMap, jsonString);
            } catch (Exception e) {
                log.error("Error sending to adapter {} for message type {}: {}",
                    adapter.getClass().getSimpleName(), event.getMessageType(), e.getMessage());
            }
        }
    }


    /**
     * 컴포넌트 종료
     */
    public void close() {
        log.info("Shutting down OutputAdaptorComponent...");

        // 실행 중단
        running.set(false);

        // 모든 어댑터 종료
        closeAllAdapters();

        // ThreadManager 종료
        shutdownThreadManager();

        log.info("OutputAdaptorComponent shutdown completed");
    }

    private void closeAllAdapters() {
        for (String msgType : outputAdapterMap.getAllKeys()) {
            for (OutputAdapter adapter : outputAdapterMap.get(msgType)) {
                try {
                    adapter.close();
                    log.info("Closed output adapter for message type: {}", msgType != null ? msgType : "all");
                } catch (Exception e) {
                    log.error("Error closing output adapter for type {}: {}", msgType, e.getMessage());
                }
            }
        }
        outputAdapterMap.clear();
    }

    private void shutdownThreadManager() {
        try {
            threadManager.shutdownAllThreads();
            log.info("All output adapter threads have been shut down");
        } catch (Exception e) {
            log.error("Error shutting down thread manager: {}", e.getMessage());
        }
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down OutputAdaptorComponent...");
            running.set(false);
        }));
    }
}