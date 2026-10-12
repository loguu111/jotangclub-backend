package com.jotang.jotangnote.controller;

import com.jotang.jotangnote.common.UserContext;
import com.jotang.jotangnote.entity.Note;
import com.jotang.jotangnote.service.NoteService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * 笔记接口层：只负责"收发 HTTP"，业务逻辑全部交给 NoteService。
 *
 * 接口清单：
 *   POST   /notes       新增一篇笔记（需登录，发布者 = 当前用户）
 *   GET    /notes/{id}  根据 ID 查询一篇笔记（无需登录）
 *   PUT    /notes/{id}  修改笔记（需登录，且只能改自己的）
 *   DELETE /notes/{id}  删除笔记（需登录，且只能删自己的）
 *
 * 登录校验由 AuthInterceptor 统一完成，这里用 UserContext.getUserId() 取当前用户。
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

    /** 新增一篇笔记：发布者 = 当前登录用户 */
    @PostMapping
    public Note create(@RequestBody Note note)
    {
        return noteService.create(UserContext.getUserId(), note);
    }

    /** 根据 ID 查询一篇笔记：查不到返回 404 */
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

    /** 修改笔记：不存在返回 404，不是自己的返回 403 */
    @PutMapping("/{id}")
    public Note update(@PathVariable Long id, @RequestBody Note note)
    {
        return noteService.update(id, UserContext.getUserId(), note);
    }

    /** 删除笔记：成功返回 204，不存在 404，不是自己的 403 */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id)
    {
        noteService.delete(id, UserContext.getUserId());
    }
}
