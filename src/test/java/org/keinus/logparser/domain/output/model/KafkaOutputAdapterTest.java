package org.keinus.logparser.domain.output.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.keinus.logparser.domain.model.LogEvent;

class KafkaOutputAdapterTest {

    @Test
    void buildProducerPropertiesUsesBoundedTimeoutAndRetrySettings() {
        Properties properties = KafkaOutputAdapter.buildProducerProperties(Map.of(
                "timeoutMs", "1000",
                "retryCount", "2",
                "retryDelayMs", "250"
        ));

        assertEquals("1000", properties.getProperty("request.timeout.ms"));
        assertEquals("1000", properties.getProperty("max.block.ms"));
        assertEquals("2", properties.getProperty("retries"));
        assertEquals("250", properties.getProperty("retry.backoff.ms"));
        assertEquals("2500", properties.getProperty("delivery.timeout.ms"));
        assertEquals("8388608", properties.getProperty("buffer.memory"));
    }

    @Test
    void sendThrowsWhenKafkaFutureFails() throws Exception {
        @SuppressWarnings("unchecked")
        Producer<String, String> producer = mock(Producer.class);
        @SuppressWarnings("unchecked")
        Future<RecordMetadata> future = mock(Future.class);

        when(producer.send(any(ProducerRecord.class))).thenReturn(future);
        when(future.get(anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenThrow(new ExecutionException(new IOException("kafka failure")));

        KafkaOutputAdapter adapter = new KafkaOutputAdapter(
                Map.of(
                        "topicid", "logs",
                        "bootstrapservers", "localhost:9092",
                        "messagetype", "test",
                        "timeoutMs", "1000"
                ),
                producer
        );

        LogEvent logEvent = new LogEvent("test log", "localhost", "test");

        assertThrows(OutputDeliveryException.class, () -> adapter.send(logEvent));
    }
}
