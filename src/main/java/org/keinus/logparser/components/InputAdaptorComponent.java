package org.keinus.logparser.components;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.keinus.logparser.config.ApplicationProperties;
import org.keinus.logparser.config.InputAdapterConfig;
import org.keinus.logparser.core.dispatch.InputFactory;
import org.keinus.logparser.core.interfaces.InputAdapter;
import org.keinus.logparser.core.util.ThreadManager;
import org.keinus.logparser.core.schema.LogEvent;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;


/**
 * 입력 어댑터들을 관리하고 실행하는 컴포넌트입니다.
 * <p>
 * 이 클래스는 애플리케이션 설정({@link ApplicationProperties})을 기반으로
 * 다양한 종류의 입력 어댑터({@link InputAdapter})를 생성하고, 각각을 별도의 스레드에서 실행합니다.
 * 각 어댑터는 외부 소스로부터 로그 메시지를 수신하는 역할을 담당합니다.
 * <p>
 * 생성된 어댑터는 {@link InputFactory}를 통해 동적으로 로드되며,
 * {@link ThreadManager}에 의해 관리되는 스레드에서 동작합니다.
 * 수신된 메시지는 {@link MessageDispatcher}로 전달되어 파이프라인의 다음 단계로 넘어갑니다.
 *
 * @see org.keinus.logparser.config.ApplicationProperties
 * @see org.keinus.logparser.core.dispatch.InputFactory
 * @see org.keinus.logparser.core.interfaces.InputAdapter
 * @see org.keinus.logparser.core.util.ThreadManager
 */
@Slf4j
@Component
public class InputAdaptorComponent {
    /**
     * input adapter
     */
    private List<InputAdapter> inputList = new ArrayList<>();
    private static final AtomicBoolean running = new AtomicBoolean(true);
    private final ThreadManager threadManager;
    private final MessageDispatcher dispatcher;

    public InputAdaptorComponent(ApplicationProperties appProp, ThreadManager threadManager, MessageDispatcher dispatcher) {
        this.threadManager = threadManager;
        this.dispatcher = dispatcher;

        for (InputAdapterConfig param : appProp.getInput()) {
            try {
                InputAdapter adapter = InputFactory.getInputAdapter(param);
                this.inputList.add(adapter);

                log.info("InputAdapter {} registered", adapter.getClass().getSimpleName());
            } catch (Exception e) {
                log.error("InputAdapter {} initialize error. {}, {}", param.getMessagetype(), e.getMessage());
            }
        }
    }

    public void initialize() {
        log.info("Starting Input Adaptor Component with {} adapters...", inputList.size());
        running.set(true);
        int count = 1;
        for (InputAdapter adapter : inputList) {
            threadManager.executeWithName(adapter.toString() + "-" + count++, () -> this.processInputAdapter(adapter));
            log.info("Started adapter: {}", adapter);
        }
    }

    @PostConstruct
    public void startPipeline() {
        try {
            log.info("Starting Input Adaptor Component with {} adapters...", inputList.size());
            running.set(true);
            int count = 1;
            for (InputAdapter adapter : inputList) {
                threadManager.executeWithName(adapter.toString() + "-" + count++, () -> this.processInputAdapter(adapter));
                log.info("Started adapter: {}", adapter);
            }
        } catch (Exception e) {
            log.error("Failed to initialize input adapters.", e);
            throw new RuntimeException("ETL Pipeline startup failed", e);
        }
    }

    @PreDestroy
    public void stopPipeline() {
        try {
            close();
            log.info("All Input Adapters stopped successfully");
        } catch (Exception e) {
            log.error("Error during ETL Pipeline shutdown", e);
        }
    }

    private void processInputAdapter(InputAdapter mInputAdapter) {
        while (running.get()) {
            if (Thread.currentThread().isInterrupted()) {
                try {
                    mInputAdapter.close();
                } catch (IOException e) {
                    log.error("Failed to close InputAdapter", e);
                }
            }
            LogEvent logEvent = mInputAdapter.run();
            if (logEvent != null) {

                if (!dispatcher.putGlobalMsg(logEvent)) {
                    log.warn("MessageQueue Full. Message discarded. {}", logEvent.getMessageType());
                }
            }
        }
    }

    public void close() {
        running.set(false);
        for (InputAdapter adapter : inputList) {
            try {
                adapter.close();
                log.info("InputAdapter {} closed", adapter.getClass().getSimpleName());
            } catch (IOException e) {
                log.error("Failed to close InputAdapter", e);
            }
        }
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down InputAdaptorComponent...");
            running.set(false);
        }));
    }
}
