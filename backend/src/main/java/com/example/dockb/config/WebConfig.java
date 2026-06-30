package com.example.dockb.config;

import com.example.dockb.interceptor.AuthorizationInterceptor;
import com.example.dockb.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web 配置：注册安全过滤器 + JWT 认证 + 权限拦截器 + @CurrentUser 参数解析器。
 *
 * <p>过滤器执行顺序（数值越小越靠前）：
 * <ol>
 *   <li>XssFilter — HTML 转义，最先执行</li>
 *   <li>CsrfTokenFilter — CSRF Token 校验</li>
 *   <li>JwtAuthFilter — JWT 令牌解析</li>
 *   <li>RateLimitInterceptor — 频率限制</li>
 *   <li>AuthorizationInterceptor — 角色权限校验</li>
 * </ol>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.csrf.enabled:true}")
    private boolean csrfEnabled;

    @Value("${app.csrf.secret:doc-summary-kb-csrf-secret-2024}")
    private String csrfSecret;

    private final RateLimitProperties rateLimitProperties;

    public WebConfig(RateLimitProperties rateLimitProperties) {
        this.rateLimitProperties = rateLimitProperties;
    }

    /** XSS 防护过滤器 — 最早执行 */
    @Bean
    public FilterRegistrationBean<XssFilter> xssFilterRegistration() {
        FilterRegistrationBean<XssFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new XssFilter());
        reg.addUrlPatterns("/api/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return reg;
    }

    /** CSRF 防护过滤器 */
    @Bean
    public FilterRegistrationBean<CsrfTokenFilter> csrfFilterRegistration() {
        FilterRegistrationBean<CsrfTokenFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new CsrfTokenFilter(csrfSecret, csrfEnabled));
        reg.addUrlPatterns("/api/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        return reg;
    }

    /** JWT 认证过滤器 */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtFilterRegistration(JwtUtil jwtUtil) {
        FilterRegistrationBean<JwtAuthFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new JwtAuthFilter(jwtUtil));
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 3);
        return reg;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 频率限制拦截器 — 在权限拦截器之前
        registry.addInterceptor(new RateLimitInterceptor(rateLimitProperties))
                .addPathPatterns("/api/**")
                .order(Ordered.HIGHEST_PRECEDENCE + 5);

        // 权限拦截器
        registry.addInterceptor(new AuthorizationInterceptor())
                .addPathPatterns("/api/**")
                .order(Ordered.HIGHEST_PRECEDENCE + 10);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
    }
}
