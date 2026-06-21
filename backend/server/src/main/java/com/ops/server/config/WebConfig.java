package com.ops.server.config;

import com.ops.server.filter.CsrfFilter;
import com.ops.server.filter.ExternalApiGuardFilter;
import com.ops.server.filter.KeyAuditFilter;
import com.ops.server.filter.XssFilter;
import com.ops.server.interceptor.AuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    @Autowired
    private CsrfFilter csrfFilter;

    @Autowired
    private XssFilter xssFilter;

    @Autowired
    private KeyAuditFilter keyAuditFilter;

    @Autowired
    private ExternalApiGuardFilter externalApiGuardFilter;

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String allowedOrigins;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",
                        "/nodes/heartbeat",
                        "/ws/**",
                        "/h2-console/**",
                        "/error"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = allowedOrigins.split(",");
        registry.addMapping("/api/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept", "X-CSRF-Token")
                .exposedHeaders("Authorization")
                .maxAge(3600)
                .allowCredentials(true);

        registry.addMapping("/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET")
                .maxAge(84400);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**").addResourceLocations("classpath:/static/");
    }

    /**
     * 手动注册 CorsFilter，覆盖 Spring Boot 自动配置的 CorsFilter。
     * Spring Boot 2.7 在找到 WebMvcConfigurer.addCorsMappings() 时会自动创建一个
     * CorsFilter bean（order = HIGHEST_PRECEDENCE），该过滤器使用 DefaultCorsProcessor
     * 在请求到达 DispatcherServlet 之前进行 CORS 预检检查。
     * 如果不显式覆盖，自动配置的 CorsFilter 可能无法正确匹配自定义配置，
     * 导致跨域请求被错误拒绝（403）。
     */
    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        String[] origins = allowedOrigins.split(",");
        config.setAllowedOriginPatterns(Arrays.asList(origins));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "X-CSRF-Token"));
        config.setExposedHeaders(Arrays.asList("Authorization"));
        config.setMaxAge(3600L);
        config.setAllowCredentials(true);

        source.registerCorsConfiguration("/api/**", config);
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }

    /**
     * 注册 CSRF 过滤器 (SEC-005)
     */
    @Bean
    public FilterRegistrationBean<CsrfFilter> csrfFilterRegistration() {
        FilterRegistrationBean<CsrfFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new CsrfFilter());
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);
        registration.setName("csrfFilter");
        return registration;
    }

    /**
     * 注册 XSS 过滤器 (SEC-006)
     */
    @Bean
    public FilterRegistrationBean<XssFilter> xssFilterRegistration() {
        FilterRegistrationBean<XssFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new XssFilter());
        registration.addUrlPatterns("/api/*");
        registration.setOrder(2);
        registration.setName("xssFilter");
        return registration;
    }

    /**
     * 注册密钥审计过滤器 (SEC-008)
     */
    @Bean
    public FilterRegistrationBean<KeyAuditFilter> keyAuditFilterRegistration() {
        FilterRegistrationBean<KeyAuditFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new KeyAuditFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(0);
        registration.setName("keyAuditFilter");
        return registration;
    }

    /**
     * 注册外部 API 防护过滤器 (SEC-009)
     */
    @Bean
    public FilterRegistrationBean<ExternalApiGuardFilter> externalApiGuardFilterRegistration() {
        FilterRegistrationBean<ExternalApiGuardFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ExternalApiGuardFilter());
        registration.addUrlPatterns("/api/*");
        registration.setOrder(3);
        registration.setName("externalApiGuardFilter");
        return registration;
    }
}
