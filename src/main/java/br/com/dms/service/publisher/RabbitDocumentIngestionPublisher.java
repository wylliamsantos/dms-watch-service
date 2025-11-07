package br.com.dms.service.publisher;

import br.com.dms.domain.AutomaticIngestionMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RabbitDocumentIngestionPublisher implements DocumentIngestionPublisher {

    private static final Logger logger = LoggerFactory.getLogger(RabbitDocumentIngestionPublisher.class);

    private final AmqpTemplate amqpTemplate;
    private final TopicExchange exchange;
    private final String routingKey;

    public RabbitDocumentIngestionPublisher(AmqpTemplate amqpTemplate,
                                            TopicExchange exchange,
                                            @Value("${watcher.messaging.routing-key:watcher.ingestion}") String routingKey) {
        this.amqpTemplate = amqpTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    @Override
    public void publish(AutomaticIngestionMessage message) {
        logger.info("Publishing automatic ingestion message for file {}", message.getStoredPath());
        amqpTemplate.convertAndSend(exchange.getName(), routingKey, message);
    }
}
