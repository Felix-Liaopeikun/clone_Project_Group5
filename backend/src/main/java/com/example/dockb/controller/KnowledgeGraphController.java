package com.example.dockb.controller;

import com.example.dockb.common.Result;
import com.example.dockb.service.KnowledgeGraphService;
import com.example.dockb.vo.KnowledgeGraphVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 知识图谱 API */
@RestController
@RequestMapping("/api/documents")
public class KnowledgeGraphController {

    private final KnowledgeGraphService kgService;

    public KnowledgeGraphController(KnowledgeGraphService kgService) {
        this.kgService = kgService;
    }

    @GetMapping("/knowledge-graph")
    public Result<KnowledgeGraphVO> knowledgeGraph() {
        return Result.success(kgService.buildGraph());
    }
}
