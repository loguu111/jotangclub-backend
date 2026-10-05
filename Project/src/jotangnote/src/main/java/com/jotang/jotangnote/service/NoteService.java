package com.jotang.jotangnote.service;

import com.jotang.jotangnote.entity.Note;
import com.jotang.jotangnote.mapper.NoteMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * 新增一篇笔记，返回数据库里真实的那条记录。
     * insert 之后只回填了自增主键 id，created_at / updated_at 是数据库用默认值填的，
     * 所以必须再查一次，才能把真实的创建时间返回给调用方（否则返回 null）。
     */
    @Transactional
    public Note create(Note note)
    {
        noteMapper.insert(note);
        return noteMapper.findById(note.getId());
    }

    /** 根据 ID 查询笔记，查不到返回 null */
    public Note findById(Long id)
    {
        return noteMapper.findById(id);
    }

    /**
     * 修改笔记，返回修改后的最新数据；笔记不存在返回 null。
     * 先查一次是为了区分"改成功"和"改了一条不存在的笔记"。
     */
    @Transactional
    public Note update(Long id, Note note)
    {
        if (noteMapper.findById(id) == null)
        {
            return null;
        }
        note.setId(id);
        noteMapper.update(note);
        return noteMapper.findById(id);
    }

    /** 删除笔记，返回是否真的删掉了一行（false 表示这条笔记本来就不存在） */
    @Transactional
    public boolean delete(Long id)
    {
        return noteMapper.deleteById(id) > 0;
    }
}
