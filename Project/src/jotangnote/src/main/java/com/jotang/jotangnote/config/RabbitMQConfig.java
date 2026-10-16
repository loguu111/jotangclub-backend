package com.jotang.jotangnote.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * RabbitMQ 的拓扑声明：交换机、队列、绑定关系。
 *
 * 应用启动时 Spring AMQP 会把下面这些"声明"发给 RabbitMQ（不存在就创建），
 * 所以不用手动去管理界面点。
 *
 * 为什么用【一个交换机 + 一个队列 + 一个路由键】这么简单的结构？
 * 因为消息的流转路径最直白：接口投递 → 队列 → 消费者，出问题一眼就能看懂。
 * 以后要分流（比如不同业务走不同队列），在这里加绑定关系就行。
 */
@Configuration
public class RabbitMQConfig
{
    public static final String NOTE_EXCHANGE = "jotangnote.note.exchange";
    public static final String NOTE_QUEUE = "jotangnote.note.queue";
    public static final String NOTE_ROUTING_KEY = "note.op";

    /** 交换机：durable=true 表示 RabbitMQ 重启后它还在 */
    @Bean
    public DirectExchange noteExchange()
    {
        return new DirectExchange(NOTE_EXCHANGE, true, false);
    }

    /** 队列：durable=true 表示 RabbitMQ 重启后队列（和里面的消息）还在 */
    @Bean
    public Queue noteQueue()
    {
        return new Queue(NOTE_QUEUE, true);
    }

    /** 绑定：把队列按 routing key 挂到交换机上 */
    @Bean
    public Binding noteBinding(Queue noteQueue, DirectExchange noteExchange)
    {
        return BindingBuilder.bind(noteQueue).to(noteExchange).with(NOTE_ROUTING_KEY);
    }

    /**
     * 消息用 JSON 序列化，而不是 Java 默认的序列化。
     * 好处：在 RabbitMQ 管理界面（http://localhost:15672）里能直接看懂消息内容。
     *
     * 注意 Spring AMQP 4 配的是 Jackson 3，所以用 JacksonJsonMessageConverter，
     * 它正好接收 Spring Boot 已经配好的 JsonMapper（支持 LocalDateTime）。
     */
    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper)
    {
        return new JacksonJsonMessageConverter(jsonMapper);
    }
}
