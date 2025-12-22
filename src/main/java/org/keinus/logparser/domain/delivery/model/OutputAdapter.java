package org.keinus.logparser.domain.delivery.model;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

import lombok.Getter;

/**
 * 처리된 메시지를 최종 목적지(sink)로 전송하는 모든 출력 어댑터의 추상 기반 클래스입니다.
 * <p>
 * 이 클래스는 다양한 출력 방식(예: Console, Kafka, OpenSearch)을 표준화된 인터페이스로 추상화합니다.
 * 모든 구체적인 출력 어댑터는 이 클래스를 상속받아 {@link #send(Map, String)} 메서드를 구현해야 합니다.
 * <p>
 * 각 어댑터는 특정 메시지 타입({@code messagetype})에 바인딩될 수 있으며,
 * 최종 출력에 원본 로그 텍스트를 포함할지 여부({@code add_origin_text})를 설정할 수 있습니다.
 *
 * @see java.io.Closeable
 * @see org.keinus.logparser.core.dispatch.OutputAdapterProcedure
 */
public abstract class OutputAdapter implements Closeable {
	@Getter
	private Long id;
	@Getter
	private String name;

	@Getter
	private String type = "";

	@Getter
	private boolean addOriginText = false;

	protected OutputAdapter(Map<String, String> obj) throws IOException {
		if (obj == null) {
			throw new IOException("Property not found.");
		}
		// ID는 OutputFactory에서 설정하거나 Config 객체를 전달받도록 수정해야 함.
		// 현재 구조상 Map을 전달받으므로 Map에서 꺼내야 함.
		if (obj.containsKey("id")) {
			this.id = Long.parseLong(obj.get("id"));
		}
		this.type = obj.getOrDefault("messagetype", null);
		this.addOriginText = Boolean.parseBoolean(obj.get("add_origin_text"));
		this.name = getClass().getSimpleName() + ":" + obj.toString();
	}

	public String getMessageType() {
		return type;
	}

	public abstract void send(Map<String, Object> json, String jsonString);

	@Override
	public String toString() {
		return this.name;
	}
}
