package com.jotang.jotangnote.mapper;

import com.jotang.jotangnote.entity.Note;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface NoteMapper
{
    int insert(Note note);
    Note findById(Long id);

    /** 查询全部笔记（最新的在前）：前端列表页要用 */
    List<Note> findAll();

    int update(Note note);
    int deleteById(Long id);
}