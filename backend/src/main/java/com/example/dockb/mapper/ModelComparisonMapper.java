package com.example.dockb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.dockb.entity.ModelComparison;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ModelComparisonMapper extends BaseMapper<ModelComparison> {

    /** 统计各模型胜率 */
    @Select("SELECT winner, COUNT(*) as cnt FROM model_comparison WHERE winner IS NOT NULL GROUP BY winner")
    List<Map<String, Object>> winnerStats();
}
