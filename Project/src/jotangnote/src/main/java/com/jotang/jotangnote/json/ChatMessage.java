package com.jotang.jotangnote.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 一条对话消息。
 *
 * 字段名要和 OpenAI 兼容接口（DeepSeek、MiMo 都是这个格式）一致，
 * 它用的是下划线命名（tool_calls、tool_call_id），所以这里用 @JsonProperty 映射。
 *
 * role 的四种取值：
 *   system    —— 系统提示词（告诉 AI 它是谁、要遵守什么规则）
 *   user      —— 用户说的话
 *   assistant —— 大模型说的话（可能带 tool_calls，表示"我想调用某个工具"）
 *   tool      —— 工具执行的结果（要带着 tool_call_id 指回是哪一次调用）
 */
@JsonIgnoreProperties(ignoreUnknown = true)          // 大模型返回的字段很多，用不到的忽略掉
@JsonInclude(JsonInclude.Include.NON_NULL)           // 为 null 的字段不要发给大模型
public class ChatMessage
{
    private String role;
    private String content;

    /** 大模型要求调用工具时放在这里（可能一次要调多个） */
    @JsonProperty("tool_calls")
    private List<ToolCall> toolCalls;

    /** role=tool 的消息用它指回是哪一次工具调用 */
    @JsonProperty("tool_call_id")
    private String toolCallId;

    public ChatMessage()
    {
    }

    public ChatMessage(String role, String content)
    {
        this.role = role;
        this.content = content;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public List<ToolCall> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCall> toolCalls) { this.toolCalls = toolCalls; }

    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }

    /** 大模型发起的一次工具调用 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolCall
    {
        private String id;
        private String type;              // 固定是 "function"
        private FunctionCall function;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public FunctionCall getFunction() { return function; }
        public void setFunction(FunctionCall function) { this.function = function; }
    }

    /** 要调用哪个函数、参数是什么 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FunctionCall
    {
        private String name;

        /** ⚠️ 注意：这是一个 JSON 格式的【字符串】，不是对象！要自己解析 */
        private String arguments;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getArguments() { return arguments; }
        public void setArguments(String arguments) { this.arguments = arguments; }
    }
}
