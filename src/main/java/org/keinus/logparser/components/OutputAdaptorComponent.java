package org.keinus.logparser.components;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.keinus.logparser.config.ApplicationProperties;
import org.keinus.logparser.dispatch.OutputAdapterProcedure;
import org.keinus.logparser.dispatch.OutputFactory;
import org.keinus.logparser.interfaces.OutputAdapter;
import org.keinus.logparser.schema.FilteredMessage;
import org.keinus.logparser.util.MergingHashMap;
import org.keinus.logparser.util.ThreadManager;
import org.keinus.logparser.util.ThreadUtil;
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
    private MergingHashMap<OutputAdapterProcedure> outputMap = new MergingHashMap<>();
  
    public OutputAdaptorComponent(ApplicationProperties appProp, ThreadManager threadManager) {
        for (Map<String, String> param : appProp.getOutput()) {
            try {
                OutputAdapter adapter = OutputFactory.getOutputAdapter(param);
                OutputAdapterProcedure procedure = new OutputAdapterProcedure(adapter);
                String msgType = adapter.getType();
                outputMap.put(msgType, procedure);
                threadManager.startThread(adapter.toString(), procedure);
                LOGGER.info("OutputAdapter {} registered", adapter.getClass().getSimpleName());
            } catch(Exception e) {
                LOGGER.error("OutputAdapter {} initialize error. {}", param.get("type"), e.getMessage());
            }
        }
        threadManager.startThread("processOutputAdapter", this::processOutputAdapter);
    }

    private void processOutputAdapter() {
        while (running.get()) {
            FilteredMessage msg = MessageDispatcher.getOutputMsg();
            if(msg == null) {
                ThreadUtil.sleep(1000);
                continue;
            }

            String messagetype = msg.getType();                
            for(var proc : outputMap.get(messagetype)) {
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
