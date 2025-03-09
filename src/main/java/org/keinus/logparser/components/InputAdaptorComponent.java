package org.keinus.logparser.components;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.keinus.logparser.config.ApplicationProperties;
import org.keinus.logparser.dispatch.InputFactory;
import org.keinus.logparser.interfaces.InputAdapter;
import org.keinus.logparser.schema.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InputAdaptorComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(InputAdaptorComponent.class);
    /**
	 * input adapter
	 */
    private List<InputAdapter> inputList = new ArrayList<>();
    private static final AtomicBoolean running = new AtomicBoolean(true);

    public InputAdaptorComponent(ApplicationProperties appProp, ExecutorService customExecutorService) {
        var input = appProp.getInput();

        for (Map<String, String> param : input) {
            try {
                InputAdapter adapter = InputFactory.getInputAdapter(param);
                this.inputList.add(adapter);
                customExecutorService.execute(() -> this.processInputAdapter(adapter));
                LOGGER.info("InputAdapter {} registered", adapter.getClass().getSimpleName());
            } catch(Exception e) {
                LOGGER.error("InputAdapter {} initialize error. {}", param.get("type"), e.getMessage());
            }
        }
    }

    private void processInputAdapter(InputAdapter mInputAdapter) {
        while (running.get()) {
            if (Thread.currentThread().isInterrupted()) {
                try {
                    mInputAdapter.close();
                } catch (IOException e) {
                    LOGGER.error("Failed to close InputAdapter", e);
                }
            }
            Message originMsg = mInputAdapter.run();
            if(originMsg != null) {
                originMsg.setType(mInputAdapter.getType());
                originMsg.setTimestamp(Instant.now());
                
                if(MessageDispatcher.putGlobalMsg(originMsg)) {
                    LOGGER.warn("MessageQueue Full. Message discarded. {}", originMsg.getType());
                }
            }
        }
    }

    public void close() {
        running.set(false);
    }
}


