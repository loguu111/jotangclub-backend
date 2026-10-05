package com.jotang.jotangnote.controller;

import com.jotang.jotangnote.entity.Note;
import com.jotang.jotangnote.service.NoteService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * 笔记接口层：只负责"收发 HTTP"，业务逻辑全部交给 NoteService。
 * 接口清单：
 *   POST   /notes       新增一篇笔记
 *   GET    /notes/{id}  根据 ID 查询一篇笔记
 *   PUT    /notes/{id}  修改已发布的笔记
 *   DELETE /notes/{id}  删除已发布的笔记
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

    /** 新增一篇笔记 */
    @PostMapping
    public Note create(@RequestBody Note note)
    {
        return noteService.create(note);
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

    /** 修改已发布的笔记：笔记不存在返回 404 */
    @PutMapping("/{id}")
    public Note update(@PathVariable Long id, @RequestBody Note note)
    {
        Note updated = noteService.update(id, note);
        if (updated == null)
        {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "笔记不存在: id=" + id);
        }
        return updated;
    }

    /** 删除已发布的笔记：删除成功返回 204（无响应体），笔记不存在返回 404 */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id)
    {
        if (!noteService.delete(id))
        {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "笔记不存在: id=" + id);
        }
    }
}
