package com.mini.g2p.notifications;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
  // Reuse existing payments exchange from your payment/mockbank services
  // EXCHANGE name confirmed in your code: "g2p.payments". :contentReference[oaicite:0]{index=0}
  public static final String PAYMENTS_EXCHANGE = "g2p.payments";

  // New generic events exchange for program/enrollment
  public static final String EVENTS_EXCHANGE = "g2p.events";

  // Queues local to notifications-service
  public static final String Q_PROGRAM   = "q.notifications.program";
  public static final String Q_ENROLL    = "q.notifications.enrollment";
  public static final String Q_PAYMENT   = "q.notifications.payment";

  @Bean TopicExchange paymentsExchange(){ return new TopicExchange(PAYMENTS_EXCHANGE, true, false); }
  @Bean TopicExchange eventsExchange(){   return new TopicExchange(EVENTS_EXCHANGE,   true, false); }

  @Bean Queue qProgram(){ return QueueBuilder.durable(Q_PROGRAM).build(); }
  @Bean Queue qEnroll(){  return QueueBuilder.durable(Q_ENROLL).build(); }
  @Bean Queue qPayment(){ return QueueBuilder.durable(Q_PAYMENT).build(); }

  // Bindings:
  @Bean Binding bProgram(Queue qProgram, TopicExchange eventsExchange){
    return BindingBuilder.bind(qProgram).to(eventsExchange).with("program.activated");
  }
  @Bean Binding bEnroll(Queue qEnroll, TopicExchange eventsExchange){
    return BindingBuilder.bind(qEnroll).to(eventsExchange).with("enrollment.*");
  }
  @Bean Binding bPaySucc(Queue qPayment, TopicExchange paymentsExchange){
    return BindingBuilder.bind(qPayment).to(paymentsExchange).with("payment.success");
  }
  @Bean Binding bPayFail(Queue qPayment, TopicExchange paymentsExchange){
    return BindingBuilder.bind(qPayment).to(paymentsExchange).with("payment.failed");
  }

  @Bean Jackson2JsonMessageConverter messageConverter(){ return new Jackson2JsonMessageConverter(); }
  @Bean RabbitTemplate rabbitTemplate(ConnectionFactory cf, Jackson2JsonMessageConverter mc){
    var t = new RabbitTemplate(cf); t.setMessageConverter(mc); return t;
  }
}
