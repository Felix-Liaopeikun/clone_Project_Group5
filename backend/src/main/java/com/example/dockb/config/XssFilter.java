package com.example.dockb.config;

import cn.hutool.http.HtmlUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * XSS 防护过滤器。
 *
 * <p>对所有请求参数值进行 HTML 转义处理，过滤潜在的 XSS 攻击向量。
 * <p>依赖 Hutool 的 {@link HtmlUtil#filter(String)} 进行安全过滤。
 */
@Slf4j
public class XssFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        XssRequestWrapper wrapper = new XssRequestWrapper(request);
        filterChain.doFilter(wrapper, response);
    }

    /**
     * 请求包装器：重写 getParameter/getParameterValues/getParameterMap，
     * 对所有参数值进行 HTML 过滤。
     */
    static class XssRequestWrapper extends HttpServletRequestWrapper {

        XssRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getParameter(String name) {
            String value = super.getParameter(name);
            return sanitize(value);
        }

        @Override
        public String[] getParameterValues(String name) {
            String[] values = super.getParameterValues(name);
            if (values == null) return null;
            String[] sanitized = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                sanitized[i] = sanitize(values[i]);
            }
            return sanitized;
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            Map<String, String[]> map = super.getParameterMap();
            if (map == null) return null;
            for (Map.Entry<String, String[]> entry : map.entrySet()) {
                String[] values = entry.getValue();
                if (values != null) {
                    for (int i = 0; i < values.length; i++) {
                        values[i] = sanitize(values[i]);
                    }
                }
            }
            return map;
        }

        static String sanitize(String value) {
            if (value == null || value.isEmpty()) {
                return value;
            }
            // 使用 Hutool 的 HtmlUtil.filter 进行 HTML 转义
            return HtmlUtil.filter(value);
        }
    }
}
