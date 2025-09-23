
package com.mini.g2p.payment.amqp;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class RabbitConfig {
  public static final String EXCHANGE = "g2p.payments";
  public static final String RK_INSTR = "payment.instruction";
  public static final String RK_STATUS = "payment.status";
  public static final String Q_INSTR  = "q.payment.instructions";
  public static final String Q_STATUS = "q.payment.status";

  @Bean TopicExchange paymentsExchange() { return new TopicExchange(EXCHANGE, true, false); }

  @Bean Queue instructionQueue() { return QueueBuilder.durable(Q_INSTR).build(); }
  @Bean Queue statusQueue()      { return QueueBuilder.durable(Q_STATUS).build(); }

  @Bean Binding bindInstruction(Queue instructionQueue, TopicExchange paymentsExchange) {
    return BindingBuilder.bind(instructionQueue).to(paymentsExchange).with(RK_INSTR);
  }
  @Bean Binding bindStatus(Queue statusQueue, TopicExchange paymentsExchange) {
    return BindingBuilder.bind(statusQueue).to(paymentsExchange).with(RK_STATUS);
  }

  @Bean Jackson2JsonMessageConverter messageConverter() { return new Jackson2JsonMessageConverter(); }

  @Bean RabbitTemplate rabbitTemplate(ConnectionFactory cf, Jackson2JsonMessageConverter mc) {
    var tpl = new RabbitTemplate(cf); tpl.setMessageConverter(mc); return tpl;
  }

  @Bean SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
      ConnectionFactory cf, Jackson2JsonMessageConverter mc) {
    var f = new SimpleRabbitListenerContainerFactory();
    f.setConnectionFactory(cf); f.setMessageConverter(mc); return f;
  }
}
