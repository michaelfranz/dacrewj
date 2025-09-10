package org.dacrewj.messaging;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Profile("server")
@Configuration
public class RabbitConfig {

    @Value("${app.rabbit.queue-name:dacrew.work}")
    private String queueName;

    @Bean
    public Queue dacrewQueue() {
        return new Queue(queueName, true);
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
