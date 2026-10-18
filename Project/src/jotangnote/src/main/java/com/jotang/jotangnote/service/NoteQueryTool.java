package com.jotang.jotangnote.service;

import com.jotang.jotangnote.entity.Note;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "根据笔记 ID 查询笔记全文"这个工具（Tool / Function Calling）。
 *
 * 它由两部分组成：
 *   ① definition() —— 写给大模型看的"说明书"：这个工具叫什么、干什么用、要什么参数。
 *      每次请求都会把它附在请求体里，大模型才知道"原来我还能查笔记"。
 *   ② execute()    —— 真正干活的代码：大模型决定调用它时，由我们来执行。
 *
 * 记住这个分工：大模型只负责"决定调用哪个函数、参数是什么"，
 * 真正执行的一定是我们自己的代码（否则这个模型就能为所欲为了）。
 */
@Component
public class NoteQueryTool
{
    private static final Logger log = LoggerFactory.getLogger(NoteQueryTool.class);

    public static final String TOOL_NAME = "get_note_by_id";

    private final NoteService noteService;
    private final ObjectMapper objectMapper;

    public NoteQueryTool(NoteService noteService, ObjectMapper objectMapper)
    {
        this.noteService = noteService;
        this.objectMapper = objectMapper;
    }

    /** 工具的 JSON Schema（OpenAI tools 格式），每次请求都附给大模型 */
    public List<Map<String, Object>> definition()
    {
        Map<String, Object> idProperty = new LinkedHashMap<>();
        idProperty.put("type", "integer");
        idProperty.put("description", "笔记的 ID");

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", Map.of("id", idProperty));
        parameters.put("required", List.of("id"));

        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", TOOL_NAME);
        function.put("description",
                "根据笔记 ID 查询这篇笔记的标题和正文全文。"
                        + "当用户要求总结、改写、翻译、分析某篇笔记时，先调用这个工具拿到全文。");
        function.put("parameters", parameters);

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);

        return List.of(tool);
    }

    /**
     * 执行工具。
     * 参数是大模型给的 JSON 字符串（例如 {"id":17}），返回值也是一个 JSON 字符串，
     * 会被作为 role=tool 的消息放回对话上下文里。
     */
    public String execute(String argumentsJson)
    {
        long id;
        try
        {
            Map<?, ?> args = objectMapper.readValue(argumentsJson, Map.class);
            id = Long.parseLong(String.valueOf(args.get("id")));
        }
        catch (Exception e)
        {
            log.warn("工具参数解析失败: {}", argumentsJson);
            return "{\"found\":false,\"error\":\"参数解析失败，需要形如 {\\\"id\\\": 1} 的参数\"}";
        }

        Note note = noteService.findById(id);
        if (note == null)
        {
            // 告诉大模型"这篇笔记不存在"，让它自己决定怎么回复用户
            return "{\"found\":false,\"id\":" + id + ",\"message\":\"这篇笔记不存在\"}";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", true);
        result.put("id", note.getId());
        result.put("title", note.getTitle());
        result.put("content", note.getContent());
        result.put("authorId", note.getUserId());
        return objectMapper.writeValueAsString(result);
    }
}
