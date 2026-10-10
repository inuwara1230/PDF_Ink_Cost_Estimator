package com.inuwara.pdf_ink_cost_estimator.trial;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

@Configuration
public class TrialConfig implements WebMvcConfigurer {

    private final TrialService trial;

    public TrialConfig(TrialService trial) {
        this.trial = trial;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws IOException {
                if (trial.check().expired()) {
                    response.sendRedirect("/trial-expired.html");
                    return false;
                }
                return true;
            }
        }).excludePathPatterns("/trial-expired.html", "/api/trial");
    }
}