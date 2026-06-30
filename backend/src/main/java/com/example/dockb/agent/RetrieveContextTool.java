package com.example.dockb.agent;

import com.example.dockb.service.QaService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 上下文检索工具 — 获取与特定主题相关的详细文档上下文。
 */
@Component
public class RetrieveContextTool implements Tool {

    private final QaService qaService;

    public RetrieveContextTool(QaService qaService) {
        this.qaService = qaService;
    }

    @Override
    public String name() { return "retrieveContext"; }

    @Override
    public String description() {
        return "获取与特定主题相关的详细文档上下文片段，用于深入理解某个主题或回答问题。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("topic", Map.of("type", "string", "description", "需要检索的主题或问题"));
        props.put("maxChunks", Map.of("type", "integer", "description", "最大返回分块数，默认5"));
        params.put("properties", props);
        params.put("required", List.of("topic"));
        return params;
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        String topic = (String) arguments.getOrDefault("topic", "");
        int maxChunks = arguments.containsKey("maxChunks")
                ? ((Number) arguments.get("maxChunks")).intValue()
                : 5;
        maxChunks = Math.max(1, Math.min(maxChunks, 10));

        List<String> contexts = qaService.buildContext(topic, maxChunks, null, false);
        if (contexts.isEmpty()) {
            return Map.of("found", false, "message", "未找到相关上下文");
        }

        return Map.of(
                "found", true,
                "count", contexts.size(),
                "contexts", contexts.stream()
                        .map(s -> s.length() > 800 ? s.substring(0, 800) + "..." : s)
                        .toList()
        );
    }
}
