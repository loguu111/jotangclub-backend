package com.jotang.jotangnote.service;

import com.jotang.jotangnote.entity.Note;
import com.jotang.jotangnote.mapper.NoteMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

/**
 * 笔记业务层。
 * 职责：只实现业务规则，不关心 HTTP。
 * 约定：查不到/改不到的时候返回 null 或 false，由 Controller 决定转成什么 HTTP 状态码。
 */
@Service
public class NoteService
{

    private final NoteMapper noteMapper;

    public NoteService(NoteMapper noteMapper)
    {
        this.noteMapper = noteMapper;
    }

    /**
     * 新增一篇笔记，发布者就是当前登录用户。
     * insert 只回填了自增 id，创建/更新时间是数据库填的，所以要再查一次才能返回真实数据。
     */
    @Transactional
    public Note create(Long userId, Note note)
    {
        note.setUserId(userId);          // 发布者由服务端决定，不信客户端传来的 userId
        noteMapper.insert(note);
        return noteMapper.findById(note.getId());
    }

    /** 根据 ID 查询笔记，查不到返回 null */
    public Note findById(Long id)
    {
        return noteMapper.findById(id);
    }

    /** 修改笔记：只能改自己的 */
    @Transactional
    public Note update(Long id, Long userId, Note note)
    {
        checkOwner(id, userId, "修改");

        note.setId(id);
        noteMapper.update(note);
        return noteMapper.findById(id);
    }

    /** 删除笔记：只能删自己的 */
    @Transactional
    public void delete(Long id, Long userId)
    {
        checkOwner(id, userId, "删除");
        noteMapper.deleteById(id);
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
