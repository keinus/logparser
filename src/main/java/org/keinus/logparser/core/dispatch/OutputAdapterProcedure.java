package org.keinus.logparser.core.dispatch;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.google.gson.Gson;

import lombok.extern.slf4j.Slf4j;

import org.keinus.logparser.core.interfaces.OutputAdapter;
import org.keinus.logparser.core.schema.LogEvent;

/**
 * 개별 {@link OutputAdapter}를 위한 메시지 처리 절차를 정의하는 {@link Runnable} 클래스입니다.
 * <p>
 * 이 클래스는 특정 출력 어댑터 하나를 감싸고, 해당 어댑터로 메시지를 보내는 작업을 별도의 스레드에서
 * 수행합니다. 각 인스턴스는 자체적인 내부 메시지 큐({@link BlockingQueue})를 가지고 있어,
 * 여러 스레드로부터 메시지를 안전하게 받아 순차적으로 처리할 수 있습니다.
 * <p>
 * 주요 로직:
 * <ol>
 *     <li>내부 큐에서 처리된 메시지({@link FilteredMessage})를 대기하며 가져옵니다.</li>
 *     <li>메시지를 JSON 문자열로 변환합니다.</li>
 *     <li>래핑된 {@link OutputAdapter}의 {@code send} 메서드를 호출하여 최종적으로 메시지를 외부로 전송합니다.</li>
 * </ol>
 *
 * @see org.keinus.logparser.core.interfaces.OutputAdapter
 * @see org.keinus.logparser.components.OutputAdaptorComponent
 */
@Slf4j
public class OutputAdapterProcedure implements Runnable {
    private Gson gson = new Gson();

    private BlockingQueue<LogEvent> outputMessageQueue = new LinkedBlockingQueue<>(1000);

    private OutputAdapter outputAdapter;

    private boolean isRunning = true;

    public OutputAdapterProcedure(OutputAdapter outputAdapter) {
        this.outputAdapter = outputAdapter;
    }

    public void enqueue(LogEvent logEvent) {
        try {
            this.outputMessageQueue.put(logEvent);
        } catch (InterruptedException e) {
            log.error("Queue is full.");
        }
    }

    @Override
    public void run() {
        boolean addOriginText = outputAdapter.getAddOriginText();
        while(isRunning) {
            LogEvent logEvent;
            try {
                logEvent = outputMessageQueue.take();
            } catch (InterruptedException e) {
                continue;
            }

            if(logEvent != null) {
                // LogEvent를 출력용 맵으로 변환
                Map<String, Object> outputMap = logEvent.toOutputMap(addOriginText);
                String jsonString = gson.toJson(outputMap);
                outputAdapter.send(outputMap, jsonString);
            }
        }
    }

    public void close() {
        try {
            outputAdapter.close();
            this.isRunning = false;
            outputMessageQueue.clear();
        } catch (IOException e) {
            log.error("Error: {}", e.getMessage());
        }
    }
}
