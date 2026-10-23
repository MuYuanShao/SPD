package com.hospital.spd.system;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Calls the configured hospital model for conversation and image recognition without business writes. */
@Service
public class AiAssistantService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AiAssistantService.class);
    private final RestClient client;
    private final String model;

    public AiAssistantService(@Value("${spd.ai.base-url}") String baseUrl,
                              @Value("${spd.ai.model}") String model,
                              @Value("${spd.ai.timeout-ms:120000}") int timeout) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(timeout);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.model = model;
    }

    public record Message(String role, String text, String image) {}
    public record ChatRequest(List<Message> messages) {}
    public record ChatReply(String model, String text) {}

    public JsonNode models() {
        try {
            return client.get().uri("/api/tags").retrieve().body(JsonNode.class);
        } catch (RestClientException ex) {
            throw modelFailure("模型列表", ex);
        }
    }

    public ChatReply chat(ChatRequest request) {
        if (request.messages() == null || request.messages().isEmpty() || request.messages().size() > 20) {
            throw new IllegalArgumentException("对话消息数量须为1至20条");
        }
        var messages = new ArrayList<Map<String, Object>>();
        messages.add(Map.of("role", "system", "content", "你是院内SPD业务助手，请用中文回答。你没有业务数据库访问权限，不能声称查询了实时库存、患者、订单或执行了操作，不能编造业务数量和金额。可以解释SPD流程和识别用户上传的耗材图片；图片无法确定的信息应说明不确定。科室取出不代表收费，SPD收到收费数据后记录使用并扣库存。"));
        int imageCount = 0;
        int textLength = 0;
        for (Message message : request.messages()) {
            if (message == null || !List.of("user", "assistant").contains(message.role() == null ? "" : message.role())
                    || message.text() == null || message.text().isBlank() || message.text().length() > 8000) {
                throw new IllegalArgumentException("消息角色或内容无效，每条消息最多8000字");
            }
            textLength += message.text().length();
            Object content = message.text();
            if (message.image() != null) {
                if (!"user".equals(message.role()) || ++imageCount > 3 || message.image().length() > 2800000
                        || !message.image().matches("^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/]+={0,2}$")) {
                    throw new IllegalArgumentException("仅支持最多3张、每张不超过2MB的PNG、JPEG或WebP图片");
                }
                content = List.of(Map.of("type", "text", "text", message.text()),
                        Map.of("type", "image_url", "image_url", Map.of("url", message.image())));
            }
            messages.add(Map.of("role", message.role(), "content", content));
        }
        if (textLength > 32000 || !"user".equals(request.messages().get(request.messages().size() - 1).role())) {
            throw new IllegalArgumentException("对话内容过长或最后一条不是用户消息");
        }
        try {
            JsonNode response = client.post().uri("/v1/chat/completions")
                    .body(Map.of("model", model, "messages", messages, "stream", false))
                    .retrieve().body(JsonNode.class);
            String text = response == null ? "" : response.path("choices").path(0).path("message").path("content").asText("");
            if (text.isBlank()) throw new IllegalArgumentException("AI模型未返回有效回答，请重试");
            return new ChatReply(model, text);
        } catch (RestClientException ex) {
            throw modelFailure("对话", ex);
        }
    }

    private static IllegalArgumentException modelFailure(String operation, RestClientException ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        log.warn("AI {} failed: {}", operation, cause.getClass().getSimpleName());
        if (ex instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            if (status == 404) return new IllegalArgumentException("AI模型或接口不存在，请检查模型名称和服务地址");
            return new IllegalArgumentException("AI模型服务返回异常状态（HTTP " + status + "），请检查模型服务日志");
        }
        if (cause instanceof java.net.SocketTimeoutException || cause instanceof java.net.http.HttpTimeoutException) {
            return new IllegalArgumentException("AI模型服务连接或响应超时，请检查模型服务与网络");
        }
        if (cause instanceof java.net.ConnectException) {
            return new IllegalArgumentException("无法连接AI模型服务，请确认模型服务已启动且地址正确");
        }
        return new IllegalArgumentException("无法访问AI模型服务，请检查服务地址与网络");
    }
}
