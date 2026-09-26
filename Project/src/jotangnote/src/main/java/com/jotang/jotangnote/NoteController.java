package com.jotang.jotangnote.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NoteController {

    @GetMapping("/notes/test")
    public String test() {
        return "NoteController is working";
    }
}