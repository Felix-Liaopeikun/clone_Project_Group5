package com.example.dockb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.dockb.entity.DocumentChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {

    /**
     * MySQL FULLTEXT 全文搜索，返回相关性最高的 chunk 列表。
     * 若 MySQL 不支持 FULLTEXT 或无结果，调用方应回退至 LIKE 查询。
     *
     * @param keyword 搜索关键词（支持 BOOLEAN MODE 语法）
     * @param limit   最大返回条数
     * @return 匹配的文档分块列表
     */
    List<DocumentChunk> searchFulltext(@Param("keyword") String keyword, @Param("limit") int limit);
}