package com.example.dockb.agent;

import com.example.dockb.service.DocumentService;
import com.example.dockb.vo.DocumentVO;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档对比工具。
 */
@Component
public class CompareDocumentsTool implements Tool {

    private final DocumentService documentService;

    public CompareDocumentsTool(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Override
    public String name() { return "compareDocuments"; }

    @Override
    public String description() {
        return "对比两份文档的主要内容差异（分类、标签、摘要等），帮助用户快速识别异同。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("documentIdA", Map.of("type", "integer", "description", "文档 A 的 ID"));
        props.put("documentIdB", Map.of("type", "integer", "description", "文档 B 的 ID"));
        params.put("properties", props);
        params.put("required", List.of("documentIdA", "documentIdB"));
        return params;
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        Long idA = ((Number) arguments.get("documentIdA")).longValue();
        Long idB = ((Number) arguments.get("documentIdB")).longValue();

        try {
            DocumentVO docA = documentService.detail(idA);
            DocumentVO docB = documentService.detail(idB);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("docA", buildDocSummary(docA));
            result.put("docB", buildDocSummary(docB));

            // 差异分析
            Map<String, Object> diff = new LinkedHashMap<>();
            diff.put("sameCategory", docA.getCategory() != null && docA.getCategory().equals(docB.getCategory()));
            diff.put("summaryA", docA.getSummary() != null ? docA.getSummary().substring(0, Math.min(100, docA.getSummary().length())) : "");
            diff.put("summaryB", docB.getSummary() != null ? docB.getSummary().substring(0, Math.min(100, docB.getSummary().length())) : "");
            result.put("diff", diff);

            return result;
        } catch (Exception e) {
            return Map.of("error", "对比失败", "detail", e.getMessage());
        }
    }

    private Map<String, Object> buildDocSummary(DocumentVO doc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", doc.getTitle());
        m.put("category", doc.getCategory());
        m.put("tags", doc.getTags());
        m.put("fileType", doc.getFileType());
        return m;
    }
}
