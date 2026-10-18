package com.jotang.jotangnote.service;

import com.jotang.jotangnote.json.ChatMessage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 调用大模型 HTTP 接口的客户端。
 *
 * DeepSeek、MiMo 都提供"OpenAI 兼容"的接口，格式是：
 *   POST {base-url}/chat/completions
 *   Header: Authorization: Bearer {api-key}
 *   Body:   {"model":"...", "messages":[...], "tools":[...]}
 *   返回:   {"choices":[{"message":{"role":"assistant","content":"..."}}]}
 *
 * 这一层只负责"把请求发出去、把响应解出来"，不掺业务逻辑。
 * 用的是 Spring 6.1 之后自带的 RestClient（写起来比 RestTemplate 顺手）。
 */
@Component
public class AiClient
{
    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    private final RestClient restClient;
    private final String model;

    public AiClient(@Value("${jotangnote.ai.base-url:https://api.deepseek.com}") String baseUrl,
                    @Value("${jotangnote.ai.api-key:}") String apiKey,
                    @Value("${jotangnote.ai.model:deepseek-chat}") String model)
    {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        this.model = model;
    }

    /**
     * 把整段对话发给大模型，返回它的回复。
     * 返回值可能是"最终回答"（content 有值），也可能是"我想调用工具"（toolCalls 有值）。
     */
    public ChatMessage chat(List<ChatMessage> messages, List<Map<String, Object>> tools)
    {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        if (tools != null && !tools.isEmpty())
        {
            body.put("tools", tools);          // 把"可用工具清单"附在请求里
        }

        ChatCompletion completion;
        try
        {
            completion = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(ChatCompletion.class);
        }
        catch (RestClientException e)
        {
            // 常见原因：API Key 没配、Key 过期、余额不足、网络不通
            log.error("调用大模型失败: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "调用 AI 接口失败，请检查 API Key 和网络：" + e.getMessage());
        }

        if (completion == null || completion.getChoices() == null || completion.getChoices().isEmpty())
        {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 接口没有返回任何结果");
        }
        return completion.getChoices().get(0).getMessage();
    }

    /** 接口返回的最外层结构：{"choices":[{"message":{...}}]} */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChatCompletion
    {
        private List<Choice> choices;

        public List<Choice> getChoices() { return choices; }
        public void setChoices(List<Choice> choices) { this.choices = choices; }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Choice
        {
            private ChatMessage message;

            public ChatMessage getMessage() { return message; }
            public void setMessage(ChatMessage message) { this.message = message; }
        }
    }
}
