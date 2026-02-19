package br.com.dms.service.publisher;

import br.com.dms.domain.AutomaticIngestionMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RabbitDocumentIngestionPublisher implements DocumentIngestionPublisher {

    private static final Logger logger = LoggerFactory.getLogger(RabbitDocumentIngestionPublisher.class);

    private final AmqpTemplate amqpTemplate;
    private final TopicExchange exchange;
    private final String routingKey;
    private final String dlqExchangeName;
    private final String dlqRoutingKey;
    private final int maxAttempts;
    private final long retryBackoffMs;

    public RabbitDocumentIngestionPublisher(AmqpTemplate amqpTemplate,
                                            @Qualifier("watcherExchange") TopicExchange exchange,
                                            @Value("${watcher.messaging.routing-key:watcher.ingestion}") String routingKey,
                                            @Value("${watcher.messaging.dlq.exchange:watcher.documents.dlx}") String dlqExchangeName,
                                            @Value("${watcher.messaging.dlq.routing-key:watcher.ingestion.dlq}") String dlqRoutingKey,
                                            @Value("${watcher.messaging.retry.max-attempts:3}") int maxAttempts,
                                            @Value("${watcher.messaging.retry.backoff-ms:500}") long retryBackoffMs) {
        this.amqpTemplate = amqpTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.dlqExchangeName = dlqExchangeName;
        this.dlqRoutingKey = dlqRoutingKey;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBackoffMs = Math.max(0, retryBackoffMs);
    }

    @Override
    public void publish(AutomaticIngestionMessage message) {
        logger.info("Publishing automatic ingestion message for file {}", message.getStoredPath());

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                amqpTemplate.convertAndSend(exchange.getName(), routingKey, message);
                if (attempt > 1) {
                    logger.info("Publish recovered on retry attempt {} for file {}", attempt, message.getStoredPath());
                }
                return;
            } catch (AmqpException ex) {
                if (attempt == maxAttempts) {
                    logger.error("Failed to publish after {} attempts. Routing file {} to DLQ", maxAttempts, message.getStoredPath(), ex);
                    routeToDlq(message);
                    return;
                }

                logger.warn("Publish attempt {} failed for file {}. Retrying...", attempt, message.getStoredPath(), ex);
                sleepBeforeRetry();
            }
        }
    }

    private void routeToDlq(AutomaticIngestionMessage message) {
        try {
            amqpTemplate.convertAndSend(dlqExchangeName, dlqRoutingKey, message);
        } catch (AmqpException dlqEx) {
            logger.error("Failed to route message to DLQ for file {}", message.getStoredPath(), dlqEx);
        }
    }

    private void sleepBeforeRetry() {
        if (retryBackoffMs <= 0) {
            return;
        }
        try {
            Thread.sleep(retryBackoffMs);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            logger.warn("Retry backoff interrupted");
        }
    }
}
