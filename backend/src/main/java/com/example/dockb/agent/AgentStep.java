package com.example.dockb.agent;

import lombok.Data;

import java.util.Map;

/**
 * 智能体推理步骤记录。
 */
@Data
public class AgentStep {

    private int stepNumber;
    private String thought;
    private String action;
    private Map<String, Object> actionInput;
    private Object observation;
    private long elapsedMs;
}
