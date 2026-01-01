package org.keinus.logparser.domain.delivery.model;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.keinus.logparser.domain.model.LogEvent;

/**
 * 처리된 메시지를 콘솔(로그)에 출력하는 간단한 출력 어댑터입니다.
 * <p>
 * 이 클래스는 {@link OutputAdapter}를 구현하며, {@link #send(Map, String)}가 호출되면
 * 전달받은 JSON 형식의 문자열을 SLF4J Logger를 사용하여 INFO 레벨로 출력합니다.
 * <p>
 * 주로 개발 및 디버깅 목적으로 파이프라인의 최종 결과를 확인할 때 사용됩니다.
 *
 * @see org.keinus.logparser.core.interfaces.OutputAdapter
 * @see org.slf4j.Logger
 */
public class ConsoleOutputAdapter extends OutputAdapter {
	private static final Logger LOGGER = LoggerFactory.getLogger( ConsoleOutputAdapter.class );
	private final AtomicBoolean closed = new AtomicBoolean(false);

	public ConsoleOutputAdapter(Map<String, String> obj) throws IOException {
		super(obj);
		LOGGER.info("Console Output Adapter created");
	}

	@Override
	public void send(LogEvent logEvent) {
		try {
			// SLF4J Logger는 이미 스레드 안전하므로 synchronized 불필요
			LOGGER.info(toJson(logEvent.toOutputMap()));
		} catch (Exception e) {
			LOGGER.error(e.getMessage());
		}
	}

	@Override
	public void close() throws IOException {
		// 멱등성 보장: 이미 닫혔으면 즉시 리턴
		if (!closed.compareAndSet(false, true)) {
			LOGGER.debug("Console Output Adapter already closed, skipping");
			return;
		}

		LOGGER.info("Console Output Adapter closed");
	}
}
