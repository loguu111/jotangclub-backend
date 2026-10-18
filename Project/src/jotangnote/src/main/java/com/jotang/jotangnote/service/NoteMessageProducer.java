package com.jotang.jotangnote.service;

import com.jotang.jotangnote.entity.NoteMessage;

import com.jotang.jotangnote.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 消息生产者：把一次笔记操作封装成消息，投递到交换机，然后立刻返回。
 *
 * 它只负责"把消息送出去"，不关心消费者什么时候处理、处理成不成功。
 * 这正是异步的好处（接口响应快），也是它的代价（客户端拿不到执行结果）。
 */
@Component
public class NoteMessageProducer
{
    private final RabbitTemplate rabbitTemplate;

    public NoteMessageProducer(RabbitTemplate rabbitTemplate)
    {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 组装并投递一条消息，返回消息 id（接口把它回给客户端，方便排查）。
     */
    public String send(NoteMessage.Operation operation, Long noteId, Long userId,
                       String title, String content)
    {
        NoteMessage message = new NoteMessage();
        message.setMessageId(UUID.randomUUID().toString().replace("-", ""));
        message.setOperation(operation);
        message.setNoteId(noteId);
        message.setUserId(userId);
        message.setTitle(title);
        message.setContent(content);

        // 发到指定交换机 + 路由键（队列由绑定关系决定）
        rabbitTemplate.convertAndSend(RabbitMQConfig.NOTE_EXCHANGE,
                RabbitMQConfig.NOTE_ROUTING_KEY, message);

        return message.getMessageId();
    }
}
