package com.example.dockb.service;

import com.example.dockb.common.PageResult;
import com.example.dockb.dto.CompareRequest;
import com.example.dockb.vo.CompareResultVO;
import com.example.dockb.vo.CompareStatsVO;

/**
 * 多模型对比服务。
 */
public interface ComparisonService {

    /** 执行多模型并行对比 */
    CompareResultVO compare(CompareRequest req, Long userId, boolean isAdmin);

    /** 用户投票 */
    void vote(Long comparisonId, String winner, Long userId);

    /** 对比历史（分页） */
    PageResult<CompareResultVO> history(long page, long size, Long userId);

    /** 统计各模型胜率 */
    CompareStatsVO stats();
}
