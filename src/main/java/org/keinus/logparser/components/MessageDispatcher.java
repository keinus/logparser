package org.keinus.logparser.components;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.keinus.logparser.util.ThreadManager;
import org.keinus.logparser.util.ThreadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.keinus.logparser.schema.FilteredMessage;
import org.keinus.logparser.schema.Message;
import org.keinus.logparser.config.ApplicationProperties;
import org.keinus.logparser.dispatch.ParseService;
import org.keinus.logparser.dispatch.TransformService;


/**
 * MessageDispatcher 클래스는 로그 메시지를 입력 어댑터로부터 받아와서 파싱하고,  
 * 변환 후 출력 어댑터를 통해 전송하는 역할을 합니다.  
 * 이 클래스는 Spring Framework의 @Service 애노테이션으로 선언되어 있으며,  
 * 싱글톤 스코프로 관리됩니다.
 */
@Service
public class MessageDispatcher {
    /**
     * 로그 기록을 위한 Logger 인스턴스
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageDispatcher.class);

    /**
     * 입력 메시지를 저장하는 BlockingQueue. 큐 크기는 10000으로 제한됩니다.
     */
    private static final BlockingQueue<Message> globalMessageQueue = new LinkedBlockingQueue<>(10000);

    /**
     * 출력 메시지를 저장하는 BlockingQueue의 HashMap. 큐 크기는 10000으로 제한됩니다.
     */
    private static final BlockingQueue<FilteredMessage> outputMessageQueue = new LinkedBlockingQueue<>(10000);

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
            @Autowired ThreadManager threadManager, 
            @Autowired ApplicationProperties appProp) {

        this.threadManager = threadManager;
        this.parseService = new ParseService(appProp.getParser());
        this.transformService = new TransformService(appProp.getTransform());
        int threads = appProp.getParserThreads();
        for(int i = 1; i <= threads; i++) {
            threadManager.startThread("parseAndTransform-"+i, this::parseAndTransform);
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
        this.threadManager.stopAllThreads();
        LOGGER.info("Message Dispatcher closed");
    }

    public void parseAndTransform() {
        while (running.get()) {
            Message message = null;
            try {
                message = globalMessageQueue.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
                LOGGER.error("Interrupted while waiting for message", e);
                Thread.currentThread().interrupt();
                return;
            }

            if (message == null)
                return;

            Map<String, Object> parsedStr = parseService.parse(message.getOriginText(), message.getType());
            if (parsedStr == null) {
                LOGGER.error("Failed to parse message, {}", message.getOriginText());
                continue;
            }

            if (this.transformService.transform(parsedStr, message.getType())) {
                FilteredMessage outputMsg = new FilteredMessage(message.getOriginText(), parsedStr, message.getType());
                putOutputMsg(outputMsg);
            } 
		}
	}

    public static boolean putGlobalMsg(Message msg) {
        while (!globalMessageQueue.offer(msg)) {
            ThreadUtil.sleep(1);
        }
        return true;
    }

    public static Message getGlobalMsg() {
        return globalMessageQueue.poll();
    }

    public static boolean putOutputMsg(FilteredMessage msg) {
        while(!outputMessageQueue.offer(msg)) {
            ThreadUtil.sleep(1);
        }
        return true;
    }

    public static FilteredMessage getOutputMsg() {
        return outputMessageQueue.poll();
    }


    public void shutdown() {
        running.set(false);
	}

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutting down MessageDispatcher...");
            running.set(false);
        }));
    }
}