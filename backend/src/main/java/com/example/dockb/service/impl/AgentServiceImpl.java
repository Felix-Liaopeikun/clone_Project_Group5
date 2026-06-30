package com.example.dockb.service.impl;

import com.example.dockb.agent.AgentStep;
import com.example.dockb.agent.Tool;
import com.example.dockb.client.M3Client;
import com.example.dockb.client.dto.ChatRequest;
import com.example.dockb.client.dto.ChatResponse;
import com.example.dockb.client.dto.QaResult;
import com.example.dockb.dto.AgentRequest;
import com.example.dockb.service.AgentService;
import com.example.dockb.service.M3Service;
import com.example.dockb.service.QaService;
import com.example.dockb.util.SnippetUtil;
import com.example.dockb.vo.AgentResponseVO;
import com.example.dockb.vo.CitationVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ReAct 风格智能体实现。
 *
 * <p>流程：Plan（LLM思考+选工具）→ Execute（调用工具）→ Observe（记录结果）→ 循环 → Final Answer。
 */
@Slf4j
@Service
public class AgentServiceImpl implements AgentService {

    private final List<Tool> tools;
    private final M3Service m3Service;
    private final QaService qaService;
    private final M3Client m3Client;
    private final ObjectMapper objectMapper;

    public AgentServiceImpl(List<Tool> tools,
                            M3Service m3Service,
                            QaService qaService,
                            M3Client m3Client,
                            ObjectMapper objectMapper) {
        this.tools = tools;
        this.m3Service = m3Service;
        this.qaService = qaService;
        this.m3Client = m3Client;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentResponseVO execute(AgentRequest req, Long userId, boolean isAdmin) {
        long start = System.currentTimeMillis();
        int maxSteps = req.getMaxSteps() != null ? Math.min(Math.max(req.getMaxSteps(), 1), 10) : 5;
        String model = req.getModel();

        List<AgentStep> steps = new ArrayList<>();
        StringBuilder conversationBuilder = new StringBuilder();

        // 系统提示词
        String systemPrompt = buildSystemPrompt();
        conversationBuilder.append("系统指令：").append(systemPrompt).append("\n\n");
        conversationBuilder.append("用户问题：").append(req.getQuestion()).append("\n");

        // ReAct 循环
        for (int i = 0; i < maxSteps; i++) {
            long stepStart = System.currentTimeMillis();
            AgentStep step = new AgentStep();
            step.setStepNumber(i + 1);

            // 请求 LLM 给出下一步行动
            String prompt = buildStepPrompt(conversationBuilder.toString(), i + 1, maxSteps);
            String llmResponse;
            try {
                ChatRequest chatReq = ChatRequest.builder()
                        .model(model)
                        .messages(List.of(
                                ChatRequest.Message.builder().role("system").content("你是一个推理助手，请严格以 JSON 格式输出。").build(),
                                ChatRequest.Message.builder().role("user").content(prompt).build()
                        ))
                        .jsonMode(true)
                        .build();
                ChatResponse response = m3Client.chat(chatReq);
                llmResponse = response.firstContent();
                if (llmResponse == null) llmResponse = "";
            } catch (Exception e) {
                log.error("[Agent] Step {} LLM call failed: {}", i + 1, e.getMessage());
                step.setThought("LLM 调用失败");
                step.setAction("error");
                step.setObservation("调用失败: " + e.getMessage());
                step.setElapsedMs(System.currentTimeMillis() - stepStart);
                steps.add(step);
                break;
            }

            // 解析 JSON 响应
            Map<String, Object> parsed = parseJson(llmResponse);
            if (parsed == null) {
                step.setThought(llmResponse.length() > 200 ? llmResponse.substring(0, 200) : llmResponse);
                step.setAction("parse_error");
                step.setObservation("无法解析 LLM 响应格式");
                step.setElapsedMs(System.currentTimeMillis() - stepStart);
                steps.add(step);
                continue;
            }

            String thought = (String) parsed.getOrDefault("thought", "");
            String action = (String) parsed.getOrDefault("action", "");
            @SuppressWarnings("unchecked")
            Map<String, Object> actionInput = (Map<String, Object>) parsed.getOrDefault("action_input", Collections.emptyMap());

            step.setThought(thought);
            step.setAction(action);
            step.setActionInput(actionInput);

            conversationBuilder.append("\n思考：").append(thought).append("\n");
            conversationBuilder.append("行动：").append(action).append("\n");

            // 检查是否到达最终答案
            if ("final_answer".equalsIgnoreCase(action)) {
                step.setObservation("已收集足够信息，准备给出最终答案");
                step.setElapsedMs(System.currentTimeMillis() - stepStart);
                steps.add(step);
                break;
            }

            // 执行工具
            Object observation = executeTool(action, actionInput);
            step.setObservation(observation);
            try {
                conversationBuilder.append("观察结果：").append(objectMapper.writeValueAsString(observation)).append("\n");
            } catch (Exception e) {
                conversationBuilder.append("观察结果：").append(observation).append("\n");
            }
            step.setElapsedMs(System.currentTimeMillis() - stepStart);
            steps.add(step);
        }

        // 生成最终答案
        String finalPrompt = buildFinalPrompt(conversationBuilder.toString());
        String finalAnswer;
        List<CitationVO> citations = new ArrayList<>();
        try {
            // 同时检索相关文档用于引用
            List<String> context = qaService.buildContext(req.getQuestion(), 5, userId, isAdmin);
            QaResult result = m3Service.answerWithFallback(req.getQuestion(), context, model);
            finalAnswer = result.getAnswer() != null ? result.getAnswer() : "抱歉，无法生成回答。";
            // 简化引用生成
            if (context != null && !context.isEmpty()) {
                for (int i = 0; i < Math.min(context.size(), 3); i++) {
                    CitationVO vo = new CitationVO();
                    vo.setDocumentId((long) i + 1);
                    vo.setTitle("知识库文档");
                    vo.setSnippet(SnippetUtil.snippet(context.get(i), req.getQuestion(), 80));
                    vo.setScore(0.5);
                    citations.add(vo);
                }
            }
        } catch (Exception e) {
            log.warn("[Agent] final answer generation failed: {}", e.getMessage());
            finalAnswer = "经过" + steps.size() + "步推理后，AI 服务暂时不可用。请稍后重试。";
        }

        AgentResponseVO vo = new AgentResponseVO();
        vo.setQuestion(req.getQuestion());
        vo.setFinalAnswer(finalAnswer);
        vo.setCitations(citations);
        vo.setSteps(steps);
        vo.setTotalSteps(steps.size());
        vo.setTotalElapsedMs(System.currentTimeMillis() - start);
        vo.setCreatedAt(LocalDateTime.now());
        return vo;
    }

    @Override
    public List<Map<String, Object>> listTools() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Tool tool : tools) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", tool.name());
            m.put("description", tool.description());
            m.put("parameters", tool.parameters());
            result.add(m);
        }
        return result;
    }

    private String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个知识库智能体，可以通过调用以下工具来回答复杂问题：\n\n");
        for (int i = 0; i < tools.size(); i++) {
            Tool t = tools.get(i);
            sb.append(i + 1).append(". **").append(t.name()).append("**：").append(t.description()).append("\n");
            try {
                sb.append("   参数：").append(objectMapper.writeValueAsString(t.parameters())).append("\n");
            } catch (Exception e) {
                sb.append("   参数：").append(t.parameters()).append("\n");
            }
        }
        sb.append("\n推理规则：\n");
        sb.append("1. 每一步请分析当前情况(thought)，选择一个工具(action)，给出工具参数(action_input)\n");
        sb.append("2. 当信息足够回答用户问题时，action 应为 \"final_answer\"\n");
        sb.append("3. 输出格式必须是 JSON：{\"thought\": \"...\", \"action\": \"工具名或final_answer\", \"action_input\": {...}}\n");
        sb.append("4. 优先使用已有信息，避免重复调用同一工具\n");
        return sb.toString();
    }

    private String buildStepPrompt(String conversation, int stepNum, int maxSteps) {
        return conversation
                + "\n\n当前是第 " + stepNum + "/" + maxSteps + " 步。请以 JSON 格式输出下一步行动。";
    }

    private String buildFinalPrompt(String conversation) {
        return conversation
                + "\n\n请基于以上所有信息给出最终答案，确保答案准确、完整且包含具体细节。";
    }

    private Object executeTool(String toolName, Map<String, Object> args) {
        for (Tool tool : tools) {
            if (tool.name().equalsIgnoreCase(toolName)) {
                try {
                    return tool.execute(args != null ? args : Collections.emptyMap());
                } catch (Exception e) {
                    log.warn("[Agent] tool {} execution failed: {}", toolName, e.getMessage());
                    return Map.of("error", "工具执行失败", "detail", e.getMessage());
                }
            }
        }
        return Map.of("error", "未知工具: " + toolName);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            // 尝试提取 JSON 块
            String json = text.trim();
            int braceStart = json.indexOf('{');
            int braceEnd = json.lastIndexOf('}');
            if (braceStart >= 0 && braceEnd > braceStart) {
                json = json.substring(braceStart, braceEnd + 1);
            }
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.debug("[Agent] json parse failed: {}", e.getMessage());
            return null;
        }
    }
}
