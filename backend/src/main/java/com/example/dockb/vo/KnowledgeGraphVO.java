package com.example.dockb.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/** 知识图谱数据结构 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KnowledgeGraphVO {
    private List<GraphNode> nodes;
    private List<GraphEdge> edges;

    @Data
    public static class GraphNode {
        private String id;
        private String label;
        private String type;   // DOCUMENT / TAG / CATEGORY
        private int size;      // 节点大小（用于力导向图）
        private Long documentId; // 文档节点对应的 ID
    }

    @Data
    public static class GraphEdge {
        private String source;
        private String target;
        private String relation; // HAS_TAG / BELONGS_TO
    }
}
