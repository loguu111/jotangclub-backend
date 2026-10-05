package com.jotang.jotangnote.service;

import com.jotang.jotangnote.entity.Note;
import com.jotang.jotangnote.mapper.NoteMapper;
import org.springframework.stereotype.Service;

@Service
public class NoteService
{

    private final NoteMapper noteMapper;

    public NoteService(NoteMapper noteMapper)
    {
        this.noteMapper = noteMapper;
    }

    public Note create(Note note)
    {
        noteMapper.insert(note);
        return note;
    }

    public Note findById(Long id)
    {
        return noteMapper.findById(id);
    }

    public Note update(Long id, Note note)
    {
        note.setId(id);
        noteMapper.update(note);
        return noteMapper.findById(id);
    }

    public void delete(Long id)
    {
        noteMapper.deleteById(id);
    }
}