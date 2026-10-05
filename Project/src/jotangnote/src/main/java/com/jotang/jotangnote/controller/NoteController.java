package com.jotang.jotangnote.controller;

import com.jotang.jotangnote.entity.Note;
import com.jotang.jotangnote.service.NoteService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notes")
public class NoteController
{

    private final NoteService noteService;

    public NoteController(NoteService noteService)
    {
        this.noteService = noteService;
    }

    @PostMapping
    public Note create(@RequestBody Note note)
    {
        return noteService.create(note);
    }

    @GetMapping("/{id}")
    public Note findById(@PathVariable Long id)
    {
        return noteService.findById(id);
    }

    @PutMapping("/{id}")
    public Note update(@PathVariable Long id, @RequestBody Note note)
    {
        return noteService.update(id, note);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id)
    {
        noteService.delete(id);
    }
}