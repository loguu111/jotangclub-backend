package com.jotang.jotangnote.service;

import com.jotang.jotangnote.entity.NoteMessage;

import com.jotang.jotangnote.config.RabbitMQConfig;
import com.jotang.jotangnote.entity.Note;
import com.jotang.jotangnote.mapper.NoteMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 消息消费者：真正干活的"后台任务"。
 *
 * 它一直在监听队列，收到消息就执行对应的数据库操作。
 * 接口那边早就返回"已受理"了，这里做多久都不影响用户。
 *
 * 两件事：
 *   ① 执行数据库操作（新增 / 修改 / 删除）
 *   ② 删缓存（数据变了，缓存必须作废）
 */
@Component
public class NoteMessageConsumer
{
    private static final Logger log = LoggerFactory.getLogger(NoteMessageConsumer.class);

    private final NoteMapper noteMapper;
    private final NoteCacheService noteCache;

    public NoteMessageConsumer(NoteMapper noteMapper, NoteCacheService noteCache)
    {
        this.noteMapper = noteMapper;
        this.noteCache = noteCache;
    }

    /**
     * @RabbitListener 让 Spring 起一个监听容器，方法就是"每收到一条消息执行一次"。
     * @Transactional 保证这一次处理里的多条 SQL 要么全成功要么全回滚。
     */
    @RabbitListener(queues = RabbitMQConfig.NOTE_QUEUE)
    @Transactional
    public void handle(NoteMessage message)
    {
        switch (message.getOperation())
        {
            case CREATE -> insertNote(message);
            case UPDATE -> updateNote(message);
            case DELETE -> deleteNote(message);
        }

        // 数据变了，缓存作废
        if (message.getNoteId() != null)
        {
            noteCache.evict(message.getNoteId());
        }

        log.info("消息处理完成: id={} 操作={} noteId={}",
                message.getMessageId(), message.getOperation(), message.getNoteId());
    }

    private void insertNote(NoteMessage message)
    {
        Note note = new Note();
        note.setTitle(message.getTitle());
        note.setContent(message.getContent());
        note.setUserId(message.getUserId());   // 发布者来自消息（消息又来自 token）
        noteMapper.insert(note);
    }

    private void updateNote(NoteMessage message)
    {
        Note note = new Note();
        note.setId(message.getNoteId());
        note.setTitle(message.getTitle());
        note.setContent(message.getContent());
        noteMapper.update(note);
    }

    private void deleteNote(NoteMessage message)
    {
        noteMapper.deleteById(message.getNoteId());
    }
}
