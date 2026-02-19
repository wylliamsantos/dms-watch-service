package br.com.dms.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class MessagingConfiguration {

    @Bean
    TopicExchange watcherExchange(@Value("${watcher.messaging.exchange:watcher.documents}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    TopicExchange watcherDlqExchange(@Value("${watcher.messaging.dlq.exchange:watcher.documents.dlx}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    Queue watcherQueue(@Value("${watcher.messaging.queue:watcher.ingestion.queue}") String queueName,
                       @Value("${watcher.messaging.dlq.exchange:watcher.documents.dlx}") String dlqExchange,
                       @Value("${watcher.messaging.dlq.routing-key:watcher.ingestion.dlq}") String dlqRoutingKey) {
        return new Queue(queueName, true, false, false, Map.of(
            "x-dead-letter-exchange", dlqExchange,
            "x-dead-letter-routing-key", dlqRoutingKey
        ));
    }

    @Bean
    Queue watcherDlqQueue(@Value("${watcher.messaging.dlq.queue:watcher.ingestion.queue.dlq}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    Binding watcherQueueBinding(Queue watcherQueue,
                                TopicExchange watcherExchange,
                                @Value("${watcher.messaging.routing-key:watcher.ingestion}") String routingKey) {
        return BindingBuilder.bind(watcherQueue).to(watcherExchange).with(routingKey);
    }

    @Bean
    Binding watcherDlqQueueBinding(Queue watcherDlqQueue,
                                   TopicExchange watcherDlqExchange,
                                   @Value("${watcher.messaging.dlq.routing-key:watcher.ingestion.dlq}") String routingKey) {
        return BindingBuilder.bind(watcherDlqQueue).to(watcherDlqExchange).with(routingKey);
    }

    @Bean
    MessageConverter watcherMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
