package org.keinus.logparser.components;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.keinus.logparser.config.ApplicationProperties;
import org.keinus.logparser.dispatch.OutputAdapterProcedure;
import org.keinus.logparser.dispatch.OutputFactory;
import org.keinus.logparser.interfaces.OutputAdapter;
import org.keinus.logparser.schema.FilteredMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OutputAdaptorComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(OutputAdaptorComponent.class);

    /**
	 * 스레드 실행 지속 여부
	 */
    private static final AtomicBoolean running = new AtomicBoolean(true);

    /**
	 * output adapter
	 */
    private Map<String, List<OutputAdapterProcedure>> outputMap = new HashMap<>();
  
    public OutputAdaptorComponent(ApplicationProperties appProp, ExecutorService customExecutorService) {
        for (Map<String, String> param : appProp.getOutput()) {
            OutputAdapter adapter = OutputFactory.getOutputAdapter(param);
            OutputAdapterProcedure procedure = new OutputAdapterProcedure(adapter);
            String msgType = adapter.getType();

            outputMap.computeIfAbsent(msgType, k -> new ArrayList<>());
            outputMap.get(msgType).add(procedure);
            customExecutorService.execute(procedure);
            LOGGER.info("OutputAdapter {} registered", adapter.getClass().getSimpleName());
        }
        customExecutorService.execute(this::processOutputAdapter);
    }

    private void processOutputAdapter() {
        while (running.get()) {
            FilteredMessage msg = MessageDispatcher.getOutputMsg();
            if(msg == null)
                continue;

            String messagetype = msg.getType();                
            for(var proc : outputMap.getOrDefault(messagetype, new ArrayList<>())) {
                proc.enqueue(msg);
            }

            for(var proc : outputMap.getOrDefault(OutputAdapter.ALL_MESSAGE_STRING, new ArrayList<>())) {
                proc.enqueue(msg);
            }
        }
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutting down InputAdaptorComponent...");
            running.set(false);
        }));
    }
    
}
