package com.jotang.jotangnote.service;

import com.jotang.jotangnote.entity.Note;
import com.jotang.jotangnote.mapper.NoteMapper;
import com.jotang.jotangnote.mq.NoteMessage;
import com.jotang.jotangnote.mq.NoteMessageProducer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

/**
 * 笔记业务层。
 *
 * P1-02：一篇笔记属于一个用户，只有本人才能修改和删除，动别人的返回 403。
 * P2-01：查询走 Redis 缓存（Cache-Aside）。
 * P3-01：增删改【不再直接写数据库】，而是校验通过后把操作封装成消息投递到 RabbitMQ，
 *        真正的写库由 NoteMessageConsumer 在后台完成 —— 接口投递完立即返回。
 */
@Service
public class NoteService
{
    private static final int MAX_TITLE_LENGTH = 255;

    private final NoteMapper noteMapper;
    private final NoteCacheService noteCache;
    private final NoteMessageProducer producer;

    public NoteService(NoteMapper noteMapper, NoteCacheService noteCache, NoteMessageProducer producer)
    {
        this.noteMapper = noteMapper;
        this.noteCache = noteCache;
        this.producer = producer;
    }

    /**
     * 新增一篇笔记：校验参数 → 投递"创建"消息 → 立刻返回消息 id。
     *
     * ⚠️ 注意这里【必须自己把参数校验做完】：
     * 以前数据直接进数据库，标题为空的话数据库的 NOT NULL 约束会拦下来；
     * 现在数据先变成消息，如果放进去脏数据，报错会发生在后台消费者里，
     * 客户端根本收不到反馈，只能看到一条日志。
     */
    public String create(Long userId, Note note)
    {
        checkTitleAndContent(note);
        return producer.send(NoteMessage.Operation.CREATE, null, userId,
                note.getTitle(), note.getContent());
    }

    /**
     * 查询全部笔记（最新在前）。
     * 列表【不做缓存】：它变化频繁、命中率低，而且失效逻辑比单条复杂得多，
     * 缓存收益不划算 —— 这也是真实项目里常见的判断。
     */
    public List<Note> findAll()
    {
        return noteMapper.findAll();
    }

    /**
     * 根据 ID 查询，查不到返回 null（查询不校验归属，任何人都能看）。
     *
     * P2-01：Cache-Aside（旁路缓存）三步走：
     *   1. 先查 Redis，命中就直接返回，完全不碰 MySQL
     *   2. 没命中再去查 MySQL
     *   3. 从 MySQL 查到了，顺手写进 Redis，下次就不用查库了
     */
    public Note findById(Long id)
    {
        // 1. 先看缓存
        Note cached = noteCache.get(id);
        if (cached != null)
        {
            return cached;
        }

        // 2. 缓存没有，查数据库
        Note note = noteMapper.findById(id);
        if (note == null)
        {
            return null;      // 数据库也没有，就是真的不存在
        }

        // 3. 回写缓存，下次同样的查询就不用再查库了
        noteCache.put(note);
        return note;
    }

    /**
     * 修改笔记。
     * 归属校验仍然【同步】做：这样"不是自己的笔记"能立刻返回 403，
     * 而不是投递出去之后在后台默默失败、客户端还以为提交成功了。
     */
    public String update(Long id, Long userId, Note note)
    {
        checkOwner(id, userId, "修改");
        checkTitleAndContent(note);
        return producer.send(NoteMessage.Operation.UPDATE, id, userId,
                note.getTitle(), note.getContent());
    }

    /** 删除笔记：先校验归属，再投递"删除"消息 */
    public String delete(Long id, Long userId)
    {
        checkOwner(id, userId, "删除");
        return producer.send(NoteMessage.Operation.DELETE, id, userId, null, null);
    }

    /**
     * 标题和正文的校验。
     * 因为写库搬到了消费者那边，这里的校验就是最后一道"能给用户及时反馈"的关口。
     */
    private void checkTitleAndContent(Note note)
    {
        if (note.getTitle() == null || note.getTitle().isBlank())
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标题不能为空");
        }
        if (note.getContent() == null || note.getContent().isBlank())
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "正文不能为空");
        }
        if (note.getTitle().length() > MAX_TITLE_LENGTH)
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "标题不能超过 " + MAX_TITLE_LENGTH + " 个字符");
        }
    }

    /**
     * 归属校验（水平越权防护），修改和删除共用。
     *   笔记不存在           → 404
     *   笔记存在但不是自己的  → 403
     *
     * 注意：这一步必须由服务端做。前端不显示按钮是没用的，
     * 别人用 curl / Postman 照样能直接打到接口上。
     */
    private void checkOwner(Long noteId, Long userId, String action)
    {
        Note existing = noteMapper.findById(noteId);
        if (existing == null)
        {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "笔记不存在: id=" + noteId);
        }
        if (!Objects.equals(existing.getUserId(), userId))//不能写 != , != 比得是是不是一个对象，这里只要值相等
        {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能" + action + "自己的笔记");
        }
    }
}
