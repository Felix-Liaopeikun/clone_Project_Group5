package com.example.dockb.client;

import com.example.dockb.client.dto.ChatRequest;
import com.example.dockb.client.dto.ChatResponse;
import com.example.dockb.client.dto.QaResult;
import com.example.dockb.client.dto.RankedHit;
import com.example.dockb.config.DeepSeekProperties;
import com.example.dockb.config.M3Properties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * M3Client 的默认实现：基于 Spring RestClient 调用 OpenAI 兼容协议。
 *
 * <p>失败重试 1 次（指数退避）。
 */
@Slf4j
@Component
public class DefaultM3Client implements M3Client {

    private static final Pattern JSON_ARRAY = Pattern.compile("\\[.*?\\]", Pattern.DOTALL);
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{[\\s\\S]*}");
    private static final Pattern QUOTED_STRING = Pattern.compile("\"([^\"]+)\"");

    private final RestClient restClient;
    private final RestClient streamingRestClient;
    private final M3Properties props;
    private final DeepSeekProperties deepSeekProps;
    private final ObjectMapper objectMapper;

    public DefaultM3Client(RestClient m3RestClient,
                           @Qualifier("streamingRestClient") RestClient streamingRestClient,
                           M3Properties props,
                           DeepSeekProperties deepSeekProps,
                           ObjectMapper objectMapper) {
        this.restClient = m3RestClient;
        this.streamingRestClient = streamingRestClient;
        this.props = props;
        this.deepSeekProps = deepSeekProps;
        this.objectMapper = objectMapper;
        if (!props.isKeyConfigured()) {
            log.warn("[M3] API key is not configured (still placeholder). M3 calls will fail until you set "
                    + "environment variable MiniMax_API_KEY or change 'mini-max.api-key' in application.yml. "
                    + "You can still test upload/list endpoints.");
        } else {
            log.info("[M3] Using model={}, baseUrl={}", props.getModel(), props.getBaseUrl());
        }
        if (!deepSeekProps.isKeyConfigured()) {
            log.warn("[DeepSeek] API key is not configured (still placeholder). "
                    + "Set environment variable DEEPSEEK_API_KEY or change 'deepseek.api-key' in application.yml.");
        } else {
            log.info("[DeepSeek] Using model={}, baseUrl={}", deepSeekProps.getModel(), deepSeekProps.getBaseUrl());
        }
    }

    @Override
    public boolean ping() {
        try {
            ChatResponse resp = chat(ChatRequest.builder()
                    .model(props.getModel())
                    .messages(List.of(ChatRequest.Message.builder()
                            .role("user")
                            .content("ping")
                            .build()))
                    .build());
            return resp != null && resp.firstContent() != null;
        } catch (Exception e) {
            log.debug("[M3] ping failed: {}", e.getMessage());
            return false;
        }
    }

    // ==================== 分类 ====================

    @Override
    public String classify(String text, List<String> candidates) {
        return classify(text, candidates, null);
    }

    @Override
    public String classify(String text, List<String> candidates, String model) {
        String prompt = "你是文档分类助手。请从候选类别中选一个最合适的，**只返回类别名称本身**，不要其他任何内容。\n"
                + "注意：不要输出思考过程或推理步骤，直接返回分类结果。\n"
                + "候选类别：" + String.join("、", candidates) + "\n"
                + "文档内容（前 2000 字）：\n" + truncate(text, 2000);
        String content = callAsString(prompt, model);
        return matchCandidate(content, candidates);
    }

    // ==================== 摘要 ====================

    @Override
    public String summarize(String text) {
        return summarize(text, null);
    }

    @Override
    public String summarize(String text, String model) {
        String prompt = "你是文档摘要助手。请用中文输出 200~500 字的摘要，要求保留核心观点，输出纯文本。\n"
                + "注意：不要输出任何思考过程或推理步骤，直接返回摘要内容。\n"
                + truncate(text, 6000);
        return callAsString(prompt, model);
    }

    // ==================== 标签抽取 ====================

    @Override
    public List<String> extractTags(String text) {
        return extractTags(text, null);
    }

    @Override
    public List<String> extractTags(String text, String model) {
        String prompt = "你是关键词提取助手。请从下面的文档中提取 3~8 个关键标签，输出 **严格的 JSON 数组** 形式"
                + "（如 [\"AI\",\"大模型\"]），不要其他任何说明。\n"
                + "注意：不要输出思考过程，直接返回 JSON 数组。\n" + truncate(text, 4000);
        String content = callAsString(prompt, model);
        return parseStringList(content);
    }

    // ==================== 重排 ====================

    @Override
    public List<RankedHit> rerank(String query, List<String> candidates) {
        return rerank(query, candidates, null);
    }

    @Override
    public List<RankedHit> rerank(String query, List<String> candidates, String model) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("你是检索重排助手。用户问题：").append(query).append("\n");
        sb.append("下面是一个候选段落列表，请按与问题的相关度从高到低排序并打分（0~1）。");
        sb.append("**严格返回 JSON 数组**，每个元素 {\"index\": <候选原下标 0-based>, \"score\": <float>}。\n");
        sb.append("注意：不要输出思考过程或推理步骤，直接返回 JSON 数组。\n");
        for (int i = 0; i < candidates.size(); i++) {
            sb.append("[").append(i).append("] ")
              .append(truncate(candidates.get(i), 400))
              .append("\n");
        }
        ChatResponse resp = callRaw(ChatRequest.builder()
                .model(resolveModel(model))
                .messages(List.of(ChatRequest.Message.builder().role("user").content(sb.toString()).build()))
                .build());
        String content = resp == null ? null : resp.firstContent();
        return parseRerank(content, candidates.size());
    }

    // ==================== 问答 ====================

    @Override
    public QaResult answer(String question, List<String> context) {
        return answer(question, context, null);
    }

    @Override
    public QaResult answer(String question, List<String> context, String model) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是文档问答助手。请仅依据下面提供的【参考资料】回答用户问题。\n");
        sb.append("【参考资料】（每条前是 [i] 编号）：\n");
        if (context != null) {
            for (int i = 0; i < context.size(); i++) {
                sb.append("[").append(i).append("] ").append(truncate(context.get(i), 1500)).append("\n");
            }
        }
        sb.append("\n用户问题：").append(question).append("\n");
        sb.append("要求：\n");
        sb.append("1. 仅返回 **严格的 JSON**，形如 {\"answer\":\"...\",\"citations\":[{\"index\":0,\"snippet\":\"...\"}]}；\n");
        sb.append("2. citations 必须引用上面资料中的编号；answer 用中文；\n");
        sb.append("3. **绝对不要**输出任何思考过程、  think 标签或推理步骤，直接返回 JSON 结果；\n");
        sb.append("4. 不要在 JSON 前后添加任何额外文字。");

        ChatResponse resp = callRaw(ChatRequest.builder()
                .model(resolveModel(model))
                .messages(List.of(ChatRequest.Message.builder().role("user").content(sb.toString()).build()))
                .build());
        String content = resp == null ? null : resp.firstContent();
        return parseQa(content);
    }

    // ==================== 流式问答 ====================

    @Override
    public Flux<String> answerStream(String question, List<String> context) {
        return answerStream(question, context, null);
    }

    @Override
    public Flux<String> answerStream(String question, List<String> context, String model) {
        String effectiveModel = resolveModel(model);
        log.info("[M3] answerStream model={}", effectiveModel);
        // 构建 prompt
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是文档问答助手。请仅依据下面提供的【参考资料】回答用户问题。\n");
        prompt.append("【参考资料】（每条前是 [i] 编号）：\n");
        if (context != null) {
            for (int i = 0; i < context.size(); i++) {
                prompt.append("[").append(i).append("] ").append(truncate(context.get(i), 1500)).append("\n");
            }
        }
        prompt.append("\n用户问题：").append(question).append("\n");
        prompt.append("要求：\n");
        prompt.append("1. 仅返回 **严格的 JSON**，形如 {\"answer\":\"...\",\"citations\":[{\"index\":0,\"snippet\":\"...\"}]}；\n");
        prompt.append("2. citations 必须引用上面资料中的编号；answer 用中文；\n");
        prompt.append("3. **绝对不要**输出任何思考过程、  think 标签或推理步骤，直接返回 JSON 结果；\n");
        prompt.append("4. 不要在 JSON 前后添加任何额外文字。");

        ChatRequest request = ChatRequest.builder()
                .model(effectiveModel)
                .stream(true)
                .messages(List.of(ChatRequest.Message.builder()
                        .role("user")
                        .content(prompt.toString())
                        .build()))
                .build();

        return Flux.create(emitter -> {
            try {
                doStreamAnswer(request, emitter);
            } catch (Exception e) {
                log.error("[M3] answerStream error: {}", e.getMessage());
                emitter.error(new M3Exception("M3 streaming failed: " + e.getMessage(), e));
            }
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    // ==================== AI 自动评测 ====================

    @Override
    public String evaluateAnswer(String question, String answer) {
        return evaluateAnswer(question, answer, null);
    }

    @Override
    public String evaluateAnswer(String question, String answer, String model) {
        String prompt = "你是问答质量评测助手。请根据以下问题和AI回答，从以下四个维度给出1-5分的评分（1最低，5最高）：\n"
                + "1. 准确性：回答是否事实正确、有无错误信息\n"
                + "2. 完整性：回答是否全面覆盖了问题的要点\n"
                + "3. 相关性：回答是否紧扣问题、无偏题\n"
                + "4. 清晰度：回答是否表达清晰、易于理解\n\n"
                + "用户问题：" + (question != null ? question : "") + "\n\n"
                + "AI回答：" + (answer != null ? truncate(answer, 3000) : "") + "\n\n"
                + "请**严格返回 JSON**，格式如下：\n"
                + "{\"accuracy\":5,\"completeness\":4,\"relevance\":5,\"clarity\":4,\"overall\":4.5,\"comment\":\"总体评价（50字以内）\"}\n"
                + "注意：不要输出思考过程或推理步骤，直接返回 JSON 结果。";
        String content = callAsString(prompt, model);
        return extractJsonArrayOrObject(content);
    }

    // ==================== 通用 ====================

    @Override
    public ChatResponse chat(ChatRequest request) {
        return callRaw(request);
    }

    // ----------------------------- 内部工具 -----------------------------

    private String resolveModel(String model) {
        return (model != null && !model.isBlank()) ? model : props.getModel();
    }

    /** 根据模型名选择 API 端点：DeepSeek 模型走 DeepSeek API，其余走 MiniMax API。 */
    private String resolveBaseUrl(String model) {
        if (model != null && model.toLowerCase().startsWith("deepseek")) {
            return deepSeekProps.getBaseUrl();
        }
        return props.getBaseUrl();
    }

    /** 根据模型名选择 API Key。 */
    private String resolveApiKey(String model) {
        String key;
        if (model != null && model.toLowerCase().startsWith("deepseek")) {
            key = deepSeekProps.resolveApiKey();
        } else {
            key = props.resolveApiKey();
        }
        if (key == null || key.isBlank() || "REPLACE_WITH_YOUR_KEY".equals(key.trim())) {
            throw new M3Exception("API key not configured for model: " + model);
        }
        return key.trim();
    }

    private String callAsString(String userPrompt) {
        return callAsString(userPrompt, null);
    }

    private String callAsString(String userPrompt, String model) {
        ChatResponse resp = callRaw(ChatRequest.builder()
                .model(resolveModel(model))
                .messages(List.of(ChatRequest.Message.builder().role("user").content(userPrompt).build()))
                .build());
        String content = resp == null ? null : resp.firstContent();
        if (content == null) return "";
        return stripThinkTags(content).trim();
    }

    private ChatResponse callRaw(ChatRequest request) {
        if (request.getModel() == null || request.getModel().isBlank()) {
            request.setModel(props.getModel());
        }
        ChatResponse last = null;
        int retries = Math.max(0, props.getMaxRetries());
        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                return doCall(request);
            } catch (Exception e) {
                last = null;
                log.warn("[M3] call failed (attempt {}/{}): {}", attempt + 1, retries + 1, e.getMessage());
                if (attempt < retries) {
                    sleepBackoff(attempt);
                } else {
                    throw new M3Exception("M3 call failed: " + e.getMessage(), e);
                }
            }
        }
        throw new M3Exception("M3 call failed after retries");
    }

    private ChatResponse doCall(ChatRequest request) {
        String model = request.getModel();
        String url = trimTrailingSlash(resolveBaseUrl(model)) + "/chat/completions";
        return restClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolveApiKey(model))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ChatResponse.class);
    }

    private void sleepBackoff(int attempt) {
        try {
            long ms = (long) (500L * Math.pow(2, attempt));
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String trimTrailingSlash(String s) {
        if (s == null) {
            return "";
        }
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** 移除  和  think 标签及其内容，清理多余的空白行。 */
    private String stripThinkTags(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        // 移除 <think>...</think> 及变体（大小写不敏感，包含跨行）
        String cleaned = content.replaceAll("(?i)</?think>", "");
        // 移除  标签包裹的内容
        cleaned = cleaned.replaceAll("(?i)<think>.*?</think>", "");
        // 清理多余空行
        cleaned = cleaned.replaceAll("\n{3,}", "\n\n");
        return cleaned;
    }

    private String matchCandidate(String content, List<String> candidates) {
        if (content == null || content.isBlank()) {
            return candidates.get(0);
        }
        String trimmed = content.trim();
        for (String c : candidates) {
            if (c.equals(trimmed)) {
                return c;
            }
        }
        for (String c : candidates) {
            if (trimmed.contains(c)) {
                return c;
            }
        }
        return candidates.get(0);
    }

    private List<String> parseStringList(String content) {
        if (content == null || content.isBlank()) {
            return Collections.emptyList();
        }
        // 优先尝试 JSON 数组
        try {
            JsonNode node = objectMapper.readTree(content);
            if (node.isArray()) {
                List<String> out = new ArrayList<>();
                node.forEach(n -> out.add(n.asText()));
                return out;
            }
        } catch (JsonProcessingException ignore) {
            // not JSON
        }
        // 退而求其次：抽取 "..."
        Matcher m = QUOTED_STRING.matcher(content);
        List<String> out = new ArrayList<>();
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private List<RankedHit> parseRerank(String content, int totalCandidates) {
        if (content == null || content.isBlank()) {
            return Collections.emptyList();
        }
        try {
            JsonNode node = objectMapper.readTree(extractJsonArrayOrObject(content));
            if (node.isArray()) {
                List<RankedHit> out = new ArrayList<>();
                node.forEach(n -> {
                    int idx = n.path("index").asInt(-1);
                    double score = n.path("score").asDouble(0d);
                    if (idx >= 0 && idx < totalCandidates) {
                        out.add(new RankedHit(idx, clamp(score)));
                    }
                });
                // 排序按分数降序
                out.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
                return out;
            }
        } catch (Exception e) {
            log.debug("[M3] rerank parse failed: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private QaResult parseQa(String content) {
        if (content == null || content.isBlank()) {
            return new QaResult("", Collections.emptyList());
        }
        try {
            JsonNode node = objectMapper.readTree(extractJsonArrayOrObject(content));
            String ans = node.path("answer").asText("");
            List<QaResult.Citation> cites = new ArrayList<>();
            JsonNode arr = node.path("citations");
            if (arr.isArray()) {
                arr.forEach(c -> {
                    int idx = c.path("index").asInt(-1);
                    String snippet = c.path("snippet").asText("");
                    cites.add(new QaResult.Citation(idx, snippet));
                });
            }
            return new QaResult(ans, cites);
        } catch (Exception e) {
            log.warn("[M3] qa parse failed: {}", e.getMessage());
            // 降级：清洗 think 标签后当作纯文本
            String cleaned = stripThinkTags(content).trim();
            return new QaResult(cleaned, Collections.emptyList());
        }
    }

    private String extractJsonArrayOrObject(String content) {
        // 先尝试整段解析
        String trimmed = content.trim();
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            return trimmed;
        }
        // 抓首段 {...} 或 [...]
        Matcher m = JSON_OBJECT.matcher(trimmed);
        if (m.find()) {
            return m.group();
        }
        Matcher m2 = JSON_ARRAY.matcher(trimmed);
        if (m2.find()) {
            return m2.group();
        }
        return trimmed;
    }

    private double clamp(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return 0d;
        }
        if (v < 0d) {
            return 0d;
        }
        if (v > 1d) {
            return 1d;
        }
        return v;
    }

    /**
     * 用 java.net.http HttpClient 发送 SSE 流式请求，提取 token 并 emit。
     */
    private void doStreamAnswer(ChatRequest request, FluxSink<String> emitter) {
        String model = request.getModel();
        String url = trimTrailingSlash(resolveBaseUrl(model)) + "/chat/completions";
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            String jsonBody = objectMapper.writeValueAsString(request);

            var httpRequest = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolveApiKey(model))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT, "text/event-stream")
                    .timeout(java.time.Duration.ofMinutes(5))
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            java.net.http.HttpResponse<java.io.InputStream> response = client.send(
                    httpRequest, java.net.http.HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                emitter.error(new M3Exception("M3 HTTP " + response.statusCode()));
                return;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring("data: ".length()).trim();
                        if ("[DONE]".equals(data)) {
                            emitter.next("[DONE]");
                            emitter.complete();
                            return;
                        }
                        try {
                            JsonNode chunk = objectMapper.readTree(data);
                            JsonNode delta = chunk.path("choices")
                                    .path(0)
                                    .path("delta")
                                    .path("content");
                            if (!delta.isMissingNode()) {
                                emitter.next(delta.asText());
                            }
                        } catch (JsonProcessingException e) {
                            log.debug("[M3] stream chunk parse failed: {}", data);
                        }
                    }
                }
            }
            if (!emitter.isCancelled()) {
                emitter.complete();
            }
        } catch (Exception e) {
            log.error("[M3] doStreamAnswer error: {}", e.getMessage());
            if (!emitter.isCancelled()) {
                emitter.error(e);
            }
        }
    }
}