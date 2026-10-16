package com.jotang.jotangnote.controller;

import com.jotang.jotangnote.common.UserContext;
import com.jotang.jotangnote.entity.Note;
import com.jotang.jotangnote.service.NoteService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 笔记接口层：只负责"收发 HTTP"，业务逻辑全部交给 NoteService。
 *
 * 接口清单：
 *   POST   /notes       新增一篇笔记（需登录）→ 202 已受理
 *   GET    /notes/{id}  根据 ID 查询一篇笔记（无需登录）→ 200
 *   PUT    /notes/{id}  修改笔记（需登录，只能改自己的）→ 202 已受理
 *   DELETE /notes/{id}  删除笔记（需登录，只能删自己的）→ 202 已受理
 *
 * P3-01 之后，三个写接口都是【异步】的：
 *   请求进来 → 校验 → 把操作投递到 RabbitMQ → 立刻返回 202 和消息 id
 *   真正的写库由 NoteMessageConsumer 在后台完成。
 *
 * 这也是为什么写接口不再返回笔记内容：投递的那一刻数据库还没改，
 * 新笔记的 id 甚至都还不存在（它是数据库自增生成的）。
 */
@RestController
@RequestMapping("/notes")
public class NoteController
{

    private final NoteService noteService;

    public NoteController(NoteService noteService)
    {
        this.noteService = noteService;
    }

    /** 新增一篇笔记：投递后立即返回 202，发布者 = 当前登录用户 */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> create(@RequestBody Note note)
    {
        String messageId = noteService.create(UserContext.getUserId(), note);
        return accepted(messageId);
    }

    /** 查询全部笔记（最新在前）：给前端列表页用，不需要登录 */
    @GetMapping
    public List<Note> findAll()
    {
        return noteService.findAll();
    }

    /** 根据 ID 查询一篇笔记：查不到返回 404（查询仍然是同步的） */
    @GetMapping("/{id}")
    public Note findById(@PathVariable Long id)
    {
        Note note = noteService.findById(id);
        if (note == null)
        {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "笔记不存在: id=" + id);
        }
        return note;
    }

    /** 修改笔记：不存在返回 404，不是自己的返回 403，通过则 202 已受理 */
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Note note)
    {
        return accepted(noteService.update(id, UserContext.getUserId(), note));
    }

    /** 删除笔记：不存在 404，不是自己的 403，通过则 202 已受理 */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> delete(@PathVariable Long id)
    {
        return accepted(noteService.delete(id, UserContext.getUserId()));
    }

    /**
     * 受理成功的统一返回体。
     * 202 Accepted 的意思是："你的请求我收到了，但还没处理完。"
     * 把 messageId 回给客户端，方便出问题时去日志/管理界面里对这条消息。
     */
    private Map<String, Object> accepted(String messageId)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("messageId", messageId);
        result.put("status", "ACCEPTED");
        result.put("message", "操作已受理，正在后台处理");
        return result;
    }
}
