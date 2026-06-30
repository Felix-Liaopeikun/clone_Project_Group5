package com.example.dockb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计日志实体。
 */
@Data
@TableName("audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作用户 ID（未登录时为 null） */
    private Long userId;

    /** 操作用户名 */
    private String username;

    /** 操作名称（如"文档上传"、"用户登录"） */
    private String action;

    /** 操作目标 ID（如文档 ID、用户 ID，可为 null） */
    private String targetId;

    /** 操作详情（扩展信息，可为 null） */
    private String detail;

    /** 请求来源 IP */
    private String sourceIp;

    /** 客户端 User-Agent */
    private String userAgent;

    /** 操作结果：SUCCESS / FAIL */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
