package com.example.dockb.agent;

import com.example.dockb.service.DocumentService;
import com.example.dockb.vo.DocumentVO;
import com.example.dockb.vo.SearchHitVO;
import com.example.dockb.vo.SearchResponseVO;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识库搜索工具。
 */
@Component
public class SearchDocumentsTool implements Tool {

    private final DocumentService documentService;

    public SearchDocumentsTool(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Override
    public String name() { return "searchDocuments"; }

    @Override
    public String description() {
        return "在知识库中搜索与关键词相关的文档片段，返回最相关的 topK 条结果。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("query", Map.of("type", "string", "description", "搜索关键词"));
        props.put("topK", Map.of("type", "integer", "description", "返回结果数量，默认5"));
        params.put("properties", props);
        params.put("required", List.of("query"));
        return params;
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        String query = (String) arguments.getOrDefault("query", "");
        int topK = arguments.containsKey("topK")
                ? ((Number) arguments.get("topK")).intValue()
                : 5;
        topK = Math.max(1, Math.min(topK, 10));

        SearchResponseVO result = documentService.search(query, topK);
        if (result.getHits() == null || result.getHits().isEmpty()) {
            return Map.of("found", false, "message", "未找到相关文档");
        }

        List<Map<String, Object>> hits = result.getHits().stream()
                .limit(topK)
                .map(h -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("title", h.getTitle());
                    m.put("snippet", h.getSnippet());
                    m.put("score", h.getScore());
                    m.put("documentId", h.getDocumentId());
                    return m;
                })
                .collect(Collectors.toList());

        return Map.of("found", true, "count", hits.size(), "results", hits);
    }
}
