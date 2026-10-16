package com.jotang.jotangnote.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 会话服务：这是整个 AI 功能的大脑。
 *
 * 它负责三件事：
 *   ① 会话管理 —— 把一段对话的历史消息存在 Redis 里（key = chat:conversation:{id}）。
 *      大模型自己是"没有记忆"的，每次都要把整段历史重新发给它，它才"记得"之前聊了什么。
 *   ② 调用大模型 —— 通过 AiClient 把上下文发出去。
 *   ③ 处理 Tool Use —— 大模型说要调用工具时，本地执行，再把结果放回上下文，然后【再问一次】。
 *
 * 为什么要存 Redis 而不是内存里的 Map？
 *   存 Map 的话，服务一重启会话就全丢了（招新题里把"会话持久化"列为加分项）。
 *   存 Redis 天然带过期时间，多个实例也能共享，正好复用我们已有的 Redis。
 */
@Service
public class ConversationService
{
    private static final String KEY_PREFIX = "chat:conversation:";

    /** 最多循环几轮"调用工具 → 再回答"，防止大模型来回调工具停不下来 */
    private static final int MAX_TOOL_ROUNDS = 5;

    /**
     * 系统提示词：告诉大模型它是谁、该怎么用工具。
     *
     * ⚠️ 这里面最重要的一句是"先调用工具拿到全文，再基于全文回答，不要编造"——
     * 不加这句，大模型很可能直接瞎编一篇笔记的内容。
     */
    private static final String SYSTEM_PROMPT = """
            你是 JotangNote 笔记平台的 AI 助手，用中文回答，尽量简洁。
            当用户让你总结、改写、翻译或分析某篇笔记时，
            先调用 get_note_by_id 工具拿到那篇笔记的全文，再基于全文回答。
            不要编造笔记内容。
            """;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AiClient aiClient;
    private final NoteQueryTool noteQueryTool;
    private final Duration ttl;

    public ConversationService(StringRedisTemplate redisTemplate,
                               ObjectMapper objectMapper,
                               AiClient aiClient,
                               NoteQueryTool noteQueryTool,
                               @Value("${jotangnote.ai.conversation-ttl-minutes:60}") long ttlMinutes)
    {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.aiClient = aiClient;
        this.noteQueryTool = noteQueryTool;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    /**
     * 处理一轮对话，返回大模型的最终回复。
     */
    public ChatMessage chat(String conversationId, String userMessage)
    {
        // ① 取出这段会话的历史（第一次对话时是空的）
        List<ChatMessage> messages = load(conversationId);
        if (messages.isEmpty())
        {
            messages.add(new ChatMessage("system", SYSTEM_PROMPT));
        }
        messages.add(new ChatMessage("user", userMessage));

        // ② 循环：发给大模型 → 如果它要调用工具就本地执行、把结果塞回上下文 → 再发一次
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++)
        {
            ChatMessage reply = aiClient.chat(messages, noteQueryTool.definition());

            // ⚠️ 大模型的回复（包括里面的 tool_calls）也要计入上下文 —— 招新题明确要求
            messages.add(reply);

            List<ChatMessage.ToolCall> toolCalls = reply.getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty())
            {
                save(conversationId, messages);
                return reply;                  // 没有工具调用，这就是最终回答
            }

            // ③ 执行每一个工具调用，把结果作为 role=tool 的消息放回上下文
            for (ChatMessage.ToolCall call : toolCalls)
            {
                String result = noteQueryTool.execute(call.getFunction().getArguments());
                ChatMessage toolMessage = new ChatMessage("tool", result);
                toolMessage.setToolCallId(call.getId());     // 必须指回是哪一次调用
                messages.add(toolMessage);
            }
        }

        // 循环到上限还没收敛（大模型一直在调工具），就把最后一条当回答返回，别把请求挂死
        save(conversationId, messages);
        return messages.get(messages.size() - 1);
    }

    /** 读一段会话的历史消息（测试和排查问题时很有用） */
    public List<ChatMessage> history(String conversationId)
    {
        return load(conversationId);
    }

    private List<ChatMessage> load(String conversationId)
    {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + conversationId);
        if (json == null || json.isBlank())
        {
            return new ArrayList<>();
        }
        try
        {
            ChatMessage[] array = objectMapper.readValue(json, ChatMessage[].class);
            return new ArrayList<>(Arrays.asList(array));
        }
        catch (Exception e)
        {
            // 存的是旧格式或坏数据，当作新会话，别让接口 500
            return new ArrayList<>();
        }
    }

    private void save(String conversationId, List<ChatMessage> messages)
    {
        redisTemplate.opsForValue().set(KEY_PREFIX + conversationId,
                objectMapper.writeValueAsString(messages), ttl);
    }
}
