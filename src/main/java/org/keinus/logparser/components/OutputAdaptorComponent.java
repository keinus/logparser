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
            var typeList = param.get("messagetype") != null ? param.get("messagetype").split(",") : new String[]{"all"};
            for (String messagetype : typeList) {
                if (messagetype.length() <= 1)
                    continue;

                if (outputMap.get(messagetype) == null) {
                    outputMap.put(messagetype, new ArrayList<>());
                    
                } 
                outputMap.get(messagetype).add(procedure);
                customExecutorService.execute(procedure);
                LOGGER.info("OutputAdapter {} registered", adapter.getClass().getSimpleName());
            }
        }
        customExecutorService.execute(this::processOutputAdapter);
    }

    private void processOutputAdapter() {
        while (running.get()) {
            FilteredMessage msg = MessageDispatcher.getOutputMsg();
            if(msg == null)
                continue;

            String messagetype = msg.getType();                
            var procList = outputMap.get(messagetype);
            if(procList != null) {
                for(var proc : procList) {
                    proc.enqueue(msg);
                }
            }

            var allList = outputMap.get("all");
            if(allList != null) {
                for(var proc : allList) {
                    proc.enqueue(msg);
                }
            }
        }
    }
    
}
