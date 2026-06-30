package com.example.dockb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.dockb.entity.Document;
import com.example.dockb.mapper.DocumentMapper;
import com.example.dockb.service.KnowledgeGraphService;
import com.example.dockb.vo.KnowledgeGraphVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeGraphServiceImpl implements KnowledgeGraphService {

    private final DocumentMapper documentMapper;

    public KnowledgeGraphServiceImpl(DocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    @Override
    public KnowledgeGraphVO buildGraph() {
        // 查询所有已完成的文档（仅公开文档，owner_id IS NULL）
        List<Document> docs = documentMapper.selectList(
                new LambdaQueryWrapper<Document>()
                        .eq(Document::getStatus, "done")
                        .isNull(Document::getOwnerId)
                        .orderByAsc(Document::getCreatedAt));

        if (docs.isEmpty()) {
            KnowledgeGraphVO vo = new KnowledgeGraphVO();
            vo.setNodes(Collections.emptyList());
            vo.setEdges(Collections.emptyList());
            return vo;
        }

        Map<String, KnowledgeGraphVO.GraphNode> nodeMap = new LinkedHashMap<>();
        List<KnowledgeGraphVO.GraphEdge> edges = new ArrayList<>();

        for (Document doc : docs) {
            // 文档节点
            String docId = "doc-" + doc.getId();
            nodeMap.putIfAbsent(docId, createNode(docId, doc.getTitle(), "DOCUMENT", 20, doc.getId()));

            // 分类节点 + 边
            if (doc.getCategory() != null && !doc.getCategory().isBlank()) {
                String catId = "cat-" + doc.getCategory();
                nodeMap.putIfAbsent(catId, createNode(catId, doc.getCategory(), "CATEGORY", 15, null));
                edges.add(createEdge(docId, catId, "BELONGS_TO"));
            }

            // 标签节点 + 边
            if (doc.getTags() != null && !doc.getTags().isBlank()) {
                for (String tag : doc.getTags().split(",")) {
                    tag = tag.trim();
                    if (tag.isEmpty()) continue;
                    String tagId = "tag-" + tag;
                    nodeMap.putIfAbsent(tagId, createNode(tagId, tag, "TAG", 10, null));
                    edges.add(createEdge(docId, tagId, "HAS_TAG"));

                    // 标签和分类之间的边
                    if (doc.getCategory() != null && !doc.getCategory().isBlank()) {
                        String catId = "cat-" + doc.getCategory();
                        edges.add(createEdge(tagId, catId, "IN_CATEGORY"));
                    }
                }
            }
        }

        // 去重边
        edges = edges.stream().distinct().collect(Collectors.toList());

        KnowledgeGraphVO vo = new KnowledgeGraphVO();
        vo.setNodes(new ArrayList<>(nodeMap.values()));
        vo.setEdges(edges);
        return vo;
    }

    private KnowledgeGraphVO.GraphNode createNode(String id, String label, String type, int size, Long documentId) {
        KnowledgeGraphVO.GraphNode node = new KnowledgeGraphVO.GraphNode();
        node.setId(id);
        node.setLabel(label);
        node.setType(type);
        node.setSize(size);
        node.setDocumentId(documentId);
        return node;
    }

    private KnowledgeGraphVO.GraphEdge createEdge(String source, String target, String relation) {
        KnowledgeGraphVO.GraphEdge edge = new KnowledgeGraphVO.GraphEdge();
        edge.setSource(source);
        edge.setTarget(target);
        edge.setRelation(relation);
        return edge;
    }
}
