package org.keinus.logparser.domain.delivery.model;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 파이프라인의 처리 성능(throughput)을 측정하기 위한 벤치마크용 출력 어댑터입니다.
 * <p>
 * 이 클래스는 {@link OutputAdapter}를 구현하지만, 메시지를 외부로 전송하는 대신
 * 내부적으로 메시지 수신 속도를 측정합니다. {@link #send(Map, String)}가 호출될 때마다
 * 카운터를 증가시키고, 1000개의 메시지가 처리될 때마다 초당 처리 메시지 수(TPS)를
 * 계산하여 로그에 출력합니다.
 * <p>
 * 이 어댑터는 시스템의 성능을 테스트하거나 튜닝할 때 유용하게 사용될 수 있습니다.
 *
 * @see org.keinus.logparser.core.interfaces.OutputAdapter
 */
public class BenchmarkAdapter extends OutputAdapter {
	private static final Logger LOGGER = LoggerFactory.getLogger(BenchmarkAdapter.class);
	
	private final AtomicInteger intervalCounter = new AtomicInteger(0);
	private final AtomicInteger totalCounter = new AtomicInteger(0);
	private long lastLogTime = System.currentTimeMillis();
	private final AtomicBoolean closed = new AtomicBoolean(false);
	
	public BenchmarkAdapter(Map<String, String> obj) throws IOException {
		super(obj);
		LOGGER.info("Benchmark Adapter created");
		this.lastLogTime = System.currentTimeMillis();
	}

	@Override
	public void send(Map<String, Object> json, String jsonString) {
		intervalCounter.incrementAndGet();
		
		long now = System.currentTimeMillis();
		long elapsed = now - lastLogTime;
		if (elapsed >= 1000) {
			int count = intervalCounter.getAndSet(0);
			double tps = (double) count * 1000 / elapsed;
			LOGGER.info("Benchmark [{}]: {} msg/s (Total: {})", getMessageType(), String.format("%.1f", tps), totalCounter.get());
			lastLogTime = now;
		}
	}

	@Override
	public void close() throws IOException {
		// 멱등성 보장: 이미 닫혔으면 즉시 리턴
		if (!closed.compareAndSet(false, true)) {
			LOGGER.debug("Benchmark Adapter already closed, skipping");
			return;
		}

		LOGGER.info("Benchmark Adapter closed. Final Total: {}", totalCounter.get());
	}
}
