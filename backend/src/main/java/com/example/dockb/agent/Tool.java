package com.example.dockb.agent;

import java.util.Map;

/**
 * 智能体工具接口。
 *
 * <p>每个工具实现此接口，提供名称、描述、参数 schema 和执行逻辑。
 * 工具通过 Spring 自动发现（@Component），AgentService 注入 {@code List<Tool>}。
 */
public interface Tool {

    /** 工具名称（如 "searchDocuments"） */
    String name();

    /** 工具中文描述（供 LLM 理解用途） */
    String description();

    /** JSON Schema 风格的参数定义（供 LLM 函数调用） */
    Map<String, Object> parameters();

    /** 执行工具 */
    Object execute(Map<String, Object> arguments);
}
