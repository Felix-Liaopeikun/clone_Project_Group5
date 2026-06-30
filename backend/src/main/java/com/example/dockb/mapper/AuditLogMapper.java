package com.example.dockb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.dockb.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作审计日志 Mapper。
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
