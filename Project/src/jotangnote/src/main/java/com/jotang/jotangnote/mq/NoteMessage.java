package com.jotang.jotangnote.mq;

/**
 * 投递到 MQ 的"笔记操作"消息。
 *
 * 它就是一个数据包裹，接口层把用户的操作封装成它，消费者再从它里面读出要干什么。
 *
 * 为什么每个字段都要？—— 因为消费者是【另起一个线程】干活的，
 * 它拿不到接口那边的 HttpServletRequest、也拿不到 UserContext（ThreadLocal），
 * 所有需要的信息都必须塞进消息里带过去。
 */
public class NoteMessage
{
    /** 三种操作 */
    public enum Operation
    {
        CREATE, UPDATE, DELETE
    }

    /** 这条消息的唯一 id：用于日志追踪（接口也会把它返回给客户端，出问题方便对账） */
    private String messageId;

    /** 要做什么 */
    private Operation operation;

    /** 笔记 id（CREATE 时还没有，是 null） */
    private Long noteId;

    /** 操作人 id */
    private Long userId;

    private String title;
    private String content;

    /** Jackson 反序列化需要无参构造器 */
    public NoteMessage()
    {
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public Operation getOperation() { return operation; }
    public void setOperation(Operation operation) { this.operation = operation; }

    public Long getNoteId() { return noteId; }
    public void setNoteId(Long noteId) { this.noteId = noteId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
