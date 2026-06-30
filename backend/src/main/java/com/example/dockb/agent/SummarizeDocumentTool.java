package com.example.dockb.agent;

import com.example.dockb.service.DocumentService;
import com.example.dockb.vo.DocumentVO;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档摘要获取工具。
 */
@Component
public class SummarizeDocumentTool implements Tool {

    private final DocumentService documentService;

    public SummarizeDocumentTool(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Override
    public String name() { return "summarizeDocument"; }

    @Override
    public String description() {
        return "获取指定文档的 AI 摘要内容，便于快速了解文档要点。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("documentId", Map.of("type", "integer", "description", "文档 ID"));
        params.put("properties", props);
        params.put("required", List.of("documentId"));
        return params;
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        Long docId = arguments.containsKey("documentId")
                ? ((Number) arguments.get("documentId")).longValue()
                : null;
        if (docId == null) {
            return Map.of("error", "请提供 documentId 参数");
        }

        try {
            DocumentVO doc = documentService.detail(docId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("title", doc.getTitle());
            result.put("category", doc.getCategory());
            result.put("summary", doc.getSummary() != null ? doc.getSummary() : "暂无摘要");
            result.put("tags", doc.getTags());
            return result;
        } catch (Exception e) {
            return Map.of("error", "文档不存在或无权访问", "detail", e.getMessage());
        }
    }
}
