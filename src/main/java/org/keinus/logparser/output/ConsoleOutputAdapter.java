package org.keinus.logparser.output;

import java.io.IOException;
import java.util.Map;

import org.keinus.logparser.core.interfaces.OutputAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
		
	public ConsoleOutputAdapter(Map<String, String> obj) throws IOException {
		super(obj);
		LOGGER.info("Console Output Adapter created");
	}

	public void send(Map<String, Object> json, String jsonString) {
		try {
			synchronized( this ) {
				LOGGER.info(jsonString);
			}
		} catch (Exception e) {
			LOGGER.error(e.getMessage());
		}
	}

	@Override
	public void close() throws IOException {
		// 불필요
	}
}
