package com.jotang.jotangnote.mapper;

import com.jotang.jotangnote.entity.Note;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NoteMapper
{
    int insert(Note note);
    Note findById(Long id);
    int update(Note note);
    int deleteById(Long id);
}