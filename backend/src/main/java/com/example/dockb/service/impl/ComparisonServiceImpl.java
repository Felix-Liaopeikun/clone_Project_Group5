package com.example.dockb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.dockb.common.BizException;
import com.example.dockb.common.PageResult;
import com.example.dockb.common.ResultCode;
import com.example.dockb.dto.CompareRequest;
import com.example.dockb.entity.ModelComparison;
import com.example.dockb.mapper.ModelComparisonMapper;
import com.example.dockb.service.ComparisonService;
import com.example.dockb.service.M3Service;
import com.example.dockb.service.QaService;
import com.example.dockb.vo.CitationVO;
import com.example.dockb.vo.CompareResultVO;
import com.example.dockb.vo.CompareStatsVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ComparisonServiceImpl implements ComparisonService {

    private static final String[] TAGS = {"A", "B", "C", "D"};

    private final ModelComparisonMapper comparisonMapper;
    private final QaService qaService;
    private final M3Service m3Service;
    private final ObjectMapper objectMapper;

    public ComparisonServiceImpl(ModelComparisonMapper comparisonMapper,
                                  QaService qaService,
                                  M3Service m3Service,
                                  ObjectMapper objectMapper) {
        this.comparisonMapper = comparisonMapper;
        this.qaService = qaService;
        this.m3Service = m3Service;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CompareResultVO compare(CompareRequest req, Long userId, boolean isAdmin) {
        int topK = req.getTopK() == null ? 5 : Math.min(Math.max(req.getTopK(), 1), 10);
        List<String> models = req.getModels();

        // 1) 构建共享上下文（所有模型使用相同的检索结果）
        List<String> context = qaService.buildContext(req.getQuestion(), topK, userId, isAdmin);

        // 2) 并行调用各模型
        List<CompletableFuture<CompareResultVO.ModelAnswerVO>> futures = new ArrayList<>();
        for (int i = 0; i < models.size(); i++) {
            final String model = models.get(i);
            final String tag = TAGS[i];
            futures.add(CompletableFuture.supplyAsync(() -> {
                long start = System.currentTimeMillis();
                try {
                    String answer;
                    if (context.isEmpty()) {
                        answer = "知识库中暂无相关资料，无法进行对比。";
                    } else {
                        var result = m3Service.answerWithFallback(req.getQuestion(), context, model);
                        answer = result.getAnswer() == null ? "" : result.getAnswer();
                    }
                    long elapsed = System.currentTimeMillis() - start;

                    CompareResultVO.ModelAnswerVO vo = new CompareResultVO.ModelAnswerVO();
                    vo.setModel(model);
                    vo.setTag(tag);
                    vo.setAnswer(answer);
                    vo.setLatencyMs(elapsed);
                    vo.setCitations(Collections.emptyList()); // 简化：对比模式下不展示引用
                    return vo;
                } catch (Exception e) {
                    log.warn("[Compare] model {} failed: {}", model, e.getMessage());
                    CompareResultVO.ModelAnswerVO vo = new CompareResultVO.ModelAnswerVO();
                    vo.setModel(model);
                    vo.setTag(tag);
                    vo.setAnswer("模型调用失败: " + e.getMessage());
                    vo.setLatencyMs(System.currentTimeMillis() - start);
                    vo.setCitations(Collections.emptyList());
                    return vo;
                }
            }));
        }

        // 3) 等待所有模型返回（最多 120 秒）
        List<CompareResultVO.ModelAnswerVO> results = futures.stream()
                .map(f -> {
                    try {
                        return f.get(120, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        log.warn("[Compare] timeout or error: {}", e.getMessage());
                        CompareResultVO.ModelAnswerVO vo = new CompareResultVO.ModelAnswerVO();
                        vo.setModel("unknown");
                        vo.setTag("?");
                        vo.setAnswer("请求超时或失败");
                        vo.setLatencyMs(120_000);
                        return vo;
                    }
                })
                .collect(Collectors.toList());

        // 4) 持久化对比记录
        ModelComparison record = new ModelComparison();
        record.setUserId(userId);
        record.setQuestion(req.getQuestion());
        record.setModelA(models.get(0));
        record.setModelB(models.size() > 1 ? models.get(1) : "");
        record.setAnswerA(results.size() > 0 ? results.get(0).getAnswer() : "");
        record.setAnswerB(results.size() > 1 ? results.get(1).getAnswer() : "");
        try {
            record.setCitationsA(results.size() > 0 ? objectMapper.writeValueAsString(results.get(0).getCitations()) : "[]");
            record.setCitationsB(results.size() > 1 ? objectMapper.writeValueAsString(results.get(1).getCitations()) : "[]");
        } catch (Exception e) {
            record.setCitationsA("[]");
            record.setCitationsB("[]");
        }
        record.setCreatedAt(LocalDateTime.now());
        comparisonMapper.insert(record);

        // 5) 组装返回
        CompareResultVO vo = new CompareResultVO();
        vo.setId(record.getId());
        vo.setQuestion(req.getQuestion());
        vo.setResults(results);
        vo.setCreatedAt(record.getCreatedAt());
        return vo;
    }

    @Override
    public void vote(Long comparisonId, String winner, Long userId) {
        ModelComparison record = comparisonMapper.selectById(comparisonId);
        if (record == null) {
            throw new BizException(ResultCode.NOT_FOUND, "对比记录不存在");
        }
        if (!"A".equals(winner) && !"B".equals(winner) && !"TIE".equals(winner)) {
            throw new BizException(ResultCode.BAD_REQUEST, "无效的投票选项，有效值为 A/B/TIE");
        }
        record.setWinner(winner);
        record.setVotedAt(LocalDateTime.now());
        comparisonMapper.updateById(record);
        log.info("[Compare] vote for comparison {}: {}, userId={}", comparisonId, winner, userId);
    }

    @Override
    public PageResult<CompareResultVO> history(long page, long size, Long userId) {
        if (page < 1) page = 1;
        if (size < 1) size = 10;
        if (size > 50) size = 50;

        LambdaQueryWrapper<ModelComparison> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.and(w -> w.isNull(ModelComparison::getUserId).or().eq(ModelComparison::getUserId, userId));
        } else {
            wrapper.isNull(ModelComparison::getUserId);
        }
        wrapper.orderByDesc(ModelComparison::getCreatedAt);

        Page<ModelComparison> result = comparisonMapper.selectPage(new Page<>(page, size), wrapper);
        List<CompareResultVO> list = result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.of(list, result.getTotal(), page, size);
    }

    @Override
    public CompareStatsVO stats() {
        CompareStatsVO vo = new CompareStatsVO();
        try {
            List<Map<String, Object>> rows = comparisonMapper.winnerStats();
            Map<String, Long> wins = new HashMap<>();
            long totalWithWinner = 0;
            for (Map<String, Object> row : rows) {
                String winner = (String) row.get("winner");
                Object cntObj = row.get("cnt");
                long cnt = cntObj instanceof Number ? ((Number) cntObj).longValue() : 0;
                if (!"TIE".equals(winner)) {
                    wins.put(winner, cnt);
                    totalWithWinner += cnt;
                }
            }

            long total = comparisonMapper.selectCount(null);
            vo.setTotalComparisons(total);
            vo.setModelWins(wins);

            Map<String, Double> rates = new HashMap<>();
            final long finalTotalWithWinner = totalWithWinner;
            if (finalTotalWithWinner > 0) {
                wins.forEach((model, count) ->
                        rates.put(model, Math.round((double) count / finalTotalWithWinner * 1000) / 10.0));
            }
            vo.setModelWinRate(rates);
        } catch (Exception e) {
            log.warn("[Compare] stats failed: {}", e.getMessage());
            vo.setTotalComparisons(0);
            vo.setModelWins(Collections.emptyMap());
            vo.setModelWinRate(Collections.emptyMap());
        }
        return vo;
    }

    private CompareResultVO toVO(ModelComparison record) {
        CompareResultVO vo = new CompareResultVO();
        vo.setId(record.getId());
        vo.setQuestion(record.getQuestion());

        List<CompareResultVO.ModelAnswerVO> results = new ArrayList<>();

        CompareResultVO.ModelAnswerVO voA = new CompareResultVO.ModelAnswerVO();
        voA.setModel(record.getModelA());
        voA.setTag("A");
        voA.setAnswer(record.getAnswerA());
        voA.setCitations(parseCitations(record.getCitationsA()));
        results.add(voA);

        if (record.getModelB() != null && !record.getModelB().isBlank()) {
            CompareResultVO.ModelAnswerVO voB = new CompareResultVO.ModelAnswerVO();
            voB.setModel(record.getModelB());
            voB.setTag("B");
            voB.setAnswer(record.getAnswerB());
            voB.setCitations(parseCitations(record.getCitationsB()));
            results.add(voB);
        }

        vo.setResults(results);
        vo.setWinner(record.getWinner());
        vo.setCreatedAt(record.getCreatedAt());
        return vo;
    }

    private List<CitationVO> parseCitations(String json) {
        if (json == null || json.isBlank() || "null".equalsIgnoreCase(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<CitationVO>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
