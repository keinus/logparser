package org.keinus.logparser.domain.output.model;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.keinus.logparser.domain.model.LogEvent;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

class RabbitMQAdapterTest {

    @Test
    void sendThrowsWhenPublishFails() throws Exception {
        Connection connection = mock(Connection.class);
        Channel channel = mock(Channel.class);
        doThrow(new IOException("rabbit failure"))
                .when(channel)
                .basicPublish(anyString(), anyString(), isNull(), any(byte[].class));

        RabbitMQAdapter adapter = new RabbitMQAdapter(
                Map.of(
                        "host", "localhost",
                        "port", "5672",
                        "exchange", "logs",
                        "routingkey", "test",
                        "messagetype", "test",
                        "timeoutMs", "1000"
                ),
                connection,
                channel
        );

        LogEvent logEvent = new LogEvent("test log", "localhost", "test");

        assertThrows(OutputDeliveryException.class, () -> adapter.send(logEvent));
    }
}
