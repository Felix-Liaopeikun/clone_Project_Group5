package com.example.dockb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型对比记录实体。
 */
@Data
@TableName("model_comparison")
public class ModelComparison {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发起对比的用户 ID */
    private Long userId;

    /** 用户问题 */
    private String question;

    /** 模型 A 名称 */
    private String modelA;

    /** 模型 B 名称 */
    private String modelB;

    /** 模型 A 的回答 */
    private String answerA;

    /** 模型 B 的回答 */
    private String answerB;

    /** 模型 A 的引用来源（JSON） */
    private String citationsA;

    /** 模型 B 的引用来源（JSON） */
    private String citationsB;

    /** 胜者：A / B / TIE */
    private String winner;

    /** 投票时间 */
    private LocalDateTime votedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
