package com.mini.g2p.mockbank;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

  public static final String EXCHANGE = "g2p.payments";
  public static final String RK_INSTR = "payment.instruction";
  public static final String RK_STATUS = "payment.status";
  public static final String Q_INSTR = "q.payment.instructions"; 
  public static final String Q_STATUS = "q.payment.status";

  @Bean
  public TopicExchange paymentsExchange() {
    return new TopicExchange(EXCHANGE, true, false);
  }

  @Bean
  public Queue instructionQueue() {
    return new Queue(Q_INSTR, true);
  }

  @Bean
  public Queue statusQueue() {
    return new Queue(Q_STATUS, true);
  }

  @Bean
  public Binding bindInstruction(Queue instructionQueue, TopicExchange paymentsExchange) {
    return BindingBuilder.bind(instructionQueue).to(paymentsExchange).with(RK_INSTR);
  }

  @Bean
  public Binding bindStatus(Queue statusQueue, TopicExchange paymentsExchange) {
    return BindingBuilder.bind(statusQueue).to(paymentsExchange).with(RK_STATUS);
  }

  @Bean
  public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }

  @Bean
  public RabbitTemplate rabbitTemplate(ConnectionFactory cf, Jackson2JsonMessageConverter converter) {
    RabbitTemplate tpl = new RabbitTemplate(cf);
    tpl.setMessageConverter(converter);
    return tpl;
  }
}
