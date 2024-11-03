package org.keinus.logparser.dispatch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.keinus.logparser.util.CustomExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.keinus.logparser.schema.Message;
import org.keinus.logparser.config.ApplicationProperties;
import org.keinus.logparser.interfaces.ITransform;
import org.keinus.logparser.interfaces.InputAdapter;
import org.keinus.logparser.interfaces.OutputAdapter;


@Service
public class MessageDispatcher implements Runnable {
	private static final Logger LOGGER = LoggerFactory.getLogger( MessageDispatcher.class );

	private static final BlockingQueue<Message> InputMessageQueue = new LinkedBlockingQueue<>(100);
	CustomExecutorService customExecutorService;

	private List<InputAdapterProcedure> inputList = new ArrayList<>();
	private Map<String, OutputAdapterProcedure> outputMap = new HashMap<>();
	private ParseService parseService = null;
	private TransformService transformService = null;
	
	public MessageDispatcher(
			@Autowired CustomExecutorService customThreadExecutor, 
			@Autowired ApplicationProperties appProp) {

		this.customExecutorService = customThreadExecutor;
		this.parseService = new ParseService(appProp.getParser());
		this.transformService = new TransformService(appProp.getTransform());

		initializeInputAdater(appProp.getInput());
		initializeOutputAdapter(appProp.getOutput(), appProp.isAddOriginText());

		customExecutorService.execute(this);
	}

	/**
	 * 종료 함수
	 * 스레드 실행기를 중지한다.
	 * @throws IOException 
	 */
	public void close() throws IOException {
		InputMessageQueue.clear();
		customExecutorService.close();
		LOGGER.info("Message Dispatcher closed");
	}
	
	private void initializeInputAdater(List<Map<String, String>> inputList) {
		for(Map<String, String> param : inputList) {
			InputAdapter adapter = InputFactory.getInputAdapter(param);
			adapter.init(param);
			InputAdapterProcedure proc = new InputAdapterProcedure(adapter);
			this.inputList.add(proc);
			customExecutorService.execute(() -> {
				while(true) {
					Message msg = null;
					try {
						msg = proc.process();
					} catch(IOException e) {
						LOGGER.error(e.getMessage());
						continue;
					}
					if(msg != null) {
						try {
							while(!InputMessageQueue.offer(msg)) {}
						} catch(IllegalStateException e) {
							LOGGER.error("Internal message queue is full.");
						}
					} else
						try { Thread.sleep(100); } catch (InterruptedException e) { e.printStackTrace(); }
				}
			});
			LOGGER.info("InputAdapter {} registerd", adapter.getClass().getSimpleName());
		}
	}

	private void initializeOutputAdapter(List<Map<String, String>> outputList, boolean addOriginText) {
		for(Map<String, String> param : outputList) {
			OutputAdapter adapter = OutputFactory.getOutputAdapter(param);
			adapter.init(param);
			
			var typeList = param.get("inputtype") != null ? param.get("inputtype").split(",") : new String[]{"all"};
			for(String type : typeList) {
				if(type.length() <= 1)
					continue;
                        
				if(outputMap.get(type) == null) {
					outputMap.put(type, new OutputAdapterProcedure(type, addOriginText));
					customExecutorService.execute(outputMap.get(type));
				} 
				outputMap.get(type).addOutputAdapter(adapter);
				LOGGER.info("OutputAdapter {} registerd", adapter.getClass().getSimpleName());
			}
		}
	}

	/**
	 * inputThread 함수
	 * 메시지 큐에서 메시지를 하나 꺼낸다.
	 * 꺼낸 메시지를 파싱 후 output queue에 넣는다.
	 */
	@Override
	public void run() {
		while (true) {
			Message message = null;
			try {
				message = InputMessageQueue.take();
			} catch (InterruptedException e) {
				e.printStackTrace();
				LOGGER.error("Interrupted while waiting for message", e);
				Thread.currentThread().interrupt();
			}
			
			if(message == null)
			    continue;
			
			Map<String, Object> parsedStr = parseService.parse(message);
			if(!this.transformService.transform(message))
				continue;

			if(parsedStr != null) {
				message.putAll(parsedStr);
			} else {
				LOGGER.error("Failed to parse message, {}", message.getOriginText());
				message.put("ERROR", "Failed to parse message");
			}

			var output = outputMap.get(message.getType());
			if(output != null)
				output.enqueue(message);
			var allOutput = outputMap.get("all");
			if(allOutput != null) {
				allOutput.enqueue(message);
			}
		}
	}
}
