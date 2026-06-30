package com.example.dockb.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.dockb.entity.EvaluationReport;
import com.example.dockb.mapper.EvaluationReportMapper;
import com.example.dockb.service.EvaluationRunnerService;
import com.example.dockb.service.M3Service;
import com.example.dockb.service.QaService;
import com.example.dockb.vo.CitationVO;
import com.example.dockb.vo.EvaluationReportVO;
import com.example.dockb.vo.QaAnswerVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class EvaluationRunnerServiceImpl implements EvaluationRunnerService {

    /** 评测数据集（内嵌 30 条标准 QA 对） */
    private static final List<Map<String, Object>> EVAL_DATASET = new ArrayList<>();

    static {
        String[][] rawData = {
            {"eval-001", "系统支持哪些文件类型？", "系统支持 PDF、DOCX、TXT、MD 四种文件格式的上传和解析。", "技术", "easy"},
            {"eval-002", "文档上传后会发生什么？", "上传后系统会自动进行文本提取、分块、AI 分类、摘要生成和标签提取，最后标记为完成状态。", "技术", "easy"},
            {"eval-003", "知识库的检索原理是什么？", "先通过关键词检索候选文档分块，再通过 AI 语义重排对候选项打分排序。", "技术", "medium"},
            {"eval-004", "如何查看文档的摘要？", "在文档详情页面可以查看 AI 自动生成的结构化摘要，摘要长度在 200 到 500 字之间。", "技术", "easy"},
            {"eval-005", "什么是 RAG 问答？", "RAG 是检索增强生成，先检索相关文档片段，再将这些片段作为上下文提供给 AI 模型生成回答。", "教育", "medium"},
            {"eval-006", "系统如何处理 AI 服务不可用的情况？", "系统为每项 AI 功能提供了降级策略：摘要降级为正文前 300 字，分类降级为关键词统计，问答降级为拼接上下文。", "技术", "medium"},
            {"eval-007", "如何切换 AI 模型？", "可以通过模型选择下拉框在 MiniMax M3、GPT-4o、DeepSeek Chat 等模型之间切换，也可以在 API 层面指定 model 参数。", "技术", "medium"},
            {"eval-008", "系统的质量控制机制是什么？", "用户可以对问答结果进行 1-5 星评分、标记有用性、提供文字反馈，系统也支持 AI 自动评测。", "技术", "medium"},
            {"eval-009", "文档分块的策略是什么？", "按段落进行切分，长句按句号截断，短段落合并至约 1000 字符，确保每个分块内容完整。", "技术", "medium"},
            {"eval-010", "系统的权限模型是怎样的？", "采用三级权限：管理员可查看所有内容，登录用户可查看公开和自己的文档，匿名用户只能查看公开文档。", "技术", "medium"},
            {"eval-011", "流式输出和普通输出有什么区别？", "流式输出通过 SSE 逐 token 推送，用户可以实时看到回答生成过程；普通输出需要等待全部生成完成才返回。", "技术", "medium"},
            {"eval-012", "如何保证回答的可信度？", "每个回答都附带引用来源，标明回答来自哪个文档、哪个分块，并提供相关性得分。", "教育", "easy"},
            {"eval-013", "系统支持多轮对话吗？", "支持。通过 conversationId 关联同一会话的多轮问答，系统会携带最近 3 条对话历史作为上下文。", "技术", "medium"},
            {"eval-014", "文档的 AI 分类如何工作？", "从候选类别中选择最匹配的分类，候选类别包括技术、法律、财务、医疗、教育、商业等。AI 不可达时降级为关键词命中统计。", "技术", "hard"},
            {"eval-015", "标签提取的作用是什么？", "AI 自动从文档中提取关键词作为标签，方便用户按标签筛选和发现文档。降级策略是返回空列表。", "技术", "easy"},
            {"eval-016", "如何保证数据安全？", "系统实现了 JWT 认证、BCrypt 密码加密、RBAC 角色权限、频率限制、XSS 过滤、CSRF 防护和操作审计日志。", "技术", "hard"},
            {"eval-017", "系统的架构是什么？", "采用前后端分离架构，后端使用 Spring Boot 3.2，前端使用 Vue 3，数据库使用 MySQL 8.0。", "技术", "easy"},
            {"eval-018", "文档上传的限制是什么？", "单个文件不超过 20MB，请求总大小不超过 25MB。支持的文件类型为 PDF、DOCX、TXT、MD。", "技术", "easy"},
            {"eval-019", "评测统计面板包含哪些信息？", "包含已评测总数、平均评分、有用率、1-5 星分布等统计数据。", "技术", "easy"},
            {"eval-020", "健康检查接口的作用是什么？", "返回后端服务状态、AI 模型是否可达以及当前使用的模型名称，用于运维监控。", "技术", "easy"},
            {"eval-021", "对比技术文档和法律文档的主要差异点？", "应描述分类、标签、摘要等方面的具体差异，并指出两个领域的关键特征。", "教育", "hard"},
            {"eval-022", "系统如何处理文件安全问题？", "通过文件魔数校验验证文件真实类型，防止文件类型伪装攻击。", "技术", "hard"},
            {"eval-023", "知识图谱展示了什么关系？", "展示文档与分类之间的 BELONGS_TO 关系，文档与标签之间的 HAS_TAG 关系，以及标签与分类之间的关联。", "教育", "medium"},
            {"eval-024", "智能体（Agent）模式有什么特点？", "智能体可以分解复杂问题，逐步调用搜索、摘要、对比、上下文检索等工具，进行多步推理后给出综合答案。", "技术", "hard"},
            {"eval-025", "多模型对比功能如何使用？", "选择 2 个或更多模型，系统会并行调用它们回答同一个问题，用户可以并排查看结果并投票选择更好的回答。", "技术", "medium"},
            {"eval-026", "如何导出对话记录？", "在对话列表中选择一个对话，点击导出即可下载 Markdown 格式的对话记录，包含完整的问答内容和引用来源。", "技术", "medium"},
            {"eval-027", "系统对文本文件上传有什么安全要求？", "文本文件（TXT/MD）会被检查是否包含 null 字节，以防止二进制文件伪装成文本文件上传。", "技术", "medium"},
            {"eval-028", "PDF 文件解析的限制是什么？", "PDF 仅解析文本层，扫描件和加密 PDF 会解析失败，错误信息会记录到文档状态。", "技术", "easy"},
            {"eval-029", "系统的缓存策略是什么？", "使用 Caffeine 在内存中缓存搜索结果（5分钟）、模型列表（10分钟）、分类列表（10分钟）、健康检查（30秒）。", "技术", "medium"},
            {"eval-030", "AI 自动评测从哪些维度评估回答质量？", "从准确性、完整性、相关性、清晰度四个维度进行评测，并给出综合评分和评语。", "技术", "medium"},
        };
        for (String[] row : rawData) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row[0]);
            item.put("question", row[1]);
            item.put("expectedAnswer", row[2]);
            item.put("category", row[3]);
            item.put("difficulty", row[4]);
            item.put("relevantDocTitles", Collections.emptyList());
            EVAL_DATASET.add(item);
        }
    }

    private final QaService qaService;
    private final M3Service m3Service;
    private final EvaluationReportMapper reportMapper;
    private final ObjectMapper objectMapper;

    public EvaluationRunnerServiceImpl(QaService qaService,
                                        M3Service m3Service,
                                        EvaluationReportMapper reportMapper,
                                        ObjectMapper objectMapper) {
        this.qaService = qaService;
        this.m3Service = m3Service;
        this.reportMapper = reportMapper;
        this.objectMapper = objectMapper;
    }

    @Async("docKbExecutor")
    @Override
    public String runAsync(String model, int topK) {
        String reportId = IdUtil.fastSimpleUUID();
        log.info("[Eval] Starting evaluation run reportId={}, model={}, topK={}", reportId, model, topK);

        List<EvaluationReportVO.EvalResult> details = new ArrayList<>();
        double totalRecall = 0, totalAccuracy = 0, totalCompleteness = 0, totalRelevance = 0, totalClarity = 0, totalCitationAcc = 0, totalLatency = 0;
        int completed = 0;

        for (Map<String, Object> item : EVAL_DATASET) {
            String qId = (String) item.get("id");
            String question = (String) item.get("question");
            String expected = (String) item.get("expectedAnswer");

            try {
                long start = System.currentTimeMillis();
                QaAnswerVO answer = qaService.ask(question, topK, model, null, null, false);
                long elapsed = System.currentTimeMillis() - start;

                // Recall: 有引用即为部分成功
                double recall = (answer.getCitations() != null && !answer.getCitations().isEmpty()) ? 1.0 : 0.0;

                double citationAcc = 0.0;
                if (answer.getCitations() != null && !answer.getCitations().isEmpty()) {
                    citationAcc = Math.min(1.0, answer.getCitations().get(0).getScore());
                }

                // AI Judge
                double accuracy = 3.0, completeness = 3.0, relevance = 3.0, clarity = 3.0;
                try {
                    String evalJson = m3Service.autoEvaluate(question, answer.getAnswer(), model);
                    Map<String, Object> evalParsed = objectMapper.readValue(evalJson, new TypeReference<Map<String, Object>>() {});
                    if (evalParsed.get("accuracy") instanceof Number) accuracy = ((Number) evalParsed.get("accuracy")).doubleValue();
                    if (evalParsed.get("completeness") instanceof Number) completeness = ((Number) evalParsed.get("completeness")).doubleValue();
                    if (evalParsed.get("relevance") instanceof Number) relevance = ((Number) evalParsed.get("relevance")).doubleValue();
                    if (evalParsed.get("clarity") instanceof Number) clarity = ((Number) evalParsed.get("clarity")).doubleValue();
                } catch (Exception e) {
                    log.warn("[Eval] AI judge failed for {}: {}", qId, e.getMessage());
                }

                EvaluationReportVO.EvalResult detail = new EvaluationReportVO.EvalResult();
                detail.setQuestionId(qId);
                detail.setQuestion(question);
                detail.setExpectedAnswer(expected);
                detail.setActualAnswer(answer.getAnswer());
                detail.setRecall(recall);
                detail.setScores(Map.of("accuracy", accuracy, "completeness", completeness, "relevance", relevance, "clarity", clarity));
                detail.setCitationAccuracy(citationAcc);
                detail.setLatencyMs(elapsed);
                details.add(detail);

                totalRecall += recall;
                totalAccuracy += accuracy;
                totalCompleteness += completeness;
                totalRelevance += relevance;
                totalClarity += clarity;
                totalCitationAcc += citationAcc;
                totalLatency += elapsed;
                completed++;

                // 短暂延迟避免 API 限流
                Thread.sleep(500);
            } catch (Exception e) {
                log.error("[Eval] Question {} failed: {}", qId, e.getMessage());
                EvaluationReportVO.EvalResult detail = new EvaluationReportVO.EvalResult();
                detail.setQuestionId(qId);
                detail.setQuestion(question);
                detail.setExpectedAnswer(expected);
                detail.setActualAnswer("评测失败: " + e.getMessage());
                detail.setScores(Map.of("accuracy", 0.0, "completeness", 0.0, "relevance", 0.0, "clarity", 0.0));
                details.add(detail);
            }
        }

        // 保存报告
        EvaluationReport report = new EvaluationReport();
        report.setId(reportId);
        report.setModel(model != null ? model : "default");
        report.setTotalQuestions(EVAL_DATASET.size());
        if (completed > 0) {
            report.setAvgRecall(Math.round(totalRecall / completed * 100) / 100.0);
            report.setAvgAccuracy(Math.round(totalAccuracy / completed * 100) / 100.0);
            report.setAvgCompleteness(Math.round(totalCompleteness / completed * 100) / 100.0);
            report.setAvgRelevance(Math.round(totalRelevance / completed * 100) / 100.0);
            report.setAvgClarity(Math.round(totalClarity / completed * 100) / 100.0);
            report.setAvgOverall(Math.round((totalAccuracy + totalCompleteness + totalRelevance + totalClarity) / 4 / completed * 100) / 100.0);
            report.setAvgCitationAccuracy(Math.round(totalCitationAcc / completed * 100) / 100.0);
            report.setAvgLatencyMs(Math.round(totalLatency / completed * 100) / 100.0);
        }
        try {
            report.setDetailsJson(objectMapper.writeValueAsString(details));
        } catch (Exception e) {
            report.setDetailsJson("[]");
        }
        report.setCompletedAt(LocalDateTime.now());
        reportMapper.insert(report);

        log.info("[Eval] Completed reportId={}, completed={}/{}", reportId, completed, EVAL_DATASET.size());
        return reportId;
    }

    @Override
    public EvaluationReportVO getReport(String reportId) {
        EvaluationReport report = reportMapper.selectById(reportId);
        if (report == null) return null;
        EvaluationReportVO vo = new EvaluationReportVO();
        vo.setReportId(report.getId());
        vo.setModel(report.getModel());
        vo.setTotalQuestions(report.getTotalQuestions());
        vo.setAvgRecall(report.getAvgRecall());
        vo.setAvgAccuracy(report.getAvgAccuracy());
        vo.setAvgCompleteness(report.getAvgCompleteness());
        vo.setAvgRelevance(report.getAvgRelevance());
        vo.setAvgClarity(report.getAvgClarity());
        vo.setAvgOverall(report.getAvgOverall());
        vo.setAvgCitationAccuracy(report.getAvgCitationAccuracy());
        vo.setAvgLatencyMs(report.getAvgLatencyMs());
        try {
            String detailsJson = report.getDetailsJson();
            if (detailsJson != null && !detailsJson.isBlank()) {
                vo.setDetails(objectMapper.readValue(detailsJson, new TypeReference<List<EvaluationReportVO.EvalResult>>() {}));
            }
        } catch (Exception e) {
            vo.setDetails(Collections.emptyList());
        }
        vo.setCompletedAt(report.getCompletedAt());
        return vo;
    }

    @Override
    public List<String> listReports() {
        return reportMapper.selectList(new LambdaQueryWrapper<EvaluationReport>()
                        .orderByDesc(EvaluationReport::getCompletedAt)
                        .last("LIMIT 20"))
                .stream()
                .map(EvaluationReport::getId)
                .toList();
    }
}
