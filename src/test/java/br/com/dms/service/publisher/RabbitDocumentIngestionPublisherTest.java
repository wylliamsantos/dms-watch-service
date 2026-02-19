package br.com.dms.service.publisher;

import br.com.dms.domain.AutomaticIngestionMessage;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.TopicExchange;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RabbitDocumentIngestionPublisherTest {

    @Test
    void shouldRetryAndSucceedBeforeSendingToDlq() {
        AmqpTemplate amqpTemplate = mock(AmqpTemplate.class);
        TopicExchange exchange = new TopicExchange("watcher.documents");
        AutomaticIngestionMessage message = new AutomaticIngestionMessage();
        message.setStoredPath("/tmp/file.pdf");

        doThrow(new AmqpException("temporary"))
            .doNothing()
            .when(amqpTemplate)
            .convertAndSend("watcher.documents", "watcher.ingestion", message);

        RabbitDocumentIngestionPublisher publisher = new RabbitDocumentIngestionPublisher(
            amqpTemplate,
            exchange,
            "watcher.ingestion",
            "watcher.documents.dlx",
            "watcher.ingestion.dlq",
            3,
            0
        );

        publisher.publish(message);

        verify(amqpTemplate, times(2)).convertAndSend("watcher.documents", "watcher.ingestion", message);
        verify(amqpTemplate, times(0)).convertAndSend("watcher.documents.dlx", "watcher.ingestion.dlq", message);
    }

    @Test
    void shouldRouteToDlqAfterRetriesExhausted() {
        AmqpTemplate amqpTemplate = mock(AmqpTemplate.class);
        TopicExchange exchange = new TopicExchange("watcher.documents");
        AutomaticIngestionMessage message = new AutomaticIngestionMessage();
        message.setStoredPath("/tmp/file.pdf");

        doThrow(new AmqpException("broken"))
            .when(amqpTemplate)
            .convertAndSend("watcher.documents", "watcher.ingestion", message);

        RabbitDocumentIngestionPublisher publisher = new RabbitDocumentIngestionPublisher(
            amqpTemplate,
            exchange,
            "watcher.ingestion",
            "watcher.documents.dlx",
            "watcher.ingestion.dlq",
            3,
            0
        );

        publisher.publish(message);

        verify(amqpTemplate, times(3)).convertAndSend("watcher.documents", "watcher.ingestion", message);
        verify(amqpTemplate, times(1)).convertAndSend("watcher.documents.dlx", "watcher.ingestion.dlq", message);
    }
}
