package com.sporthub.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Protects management handlers even when a service is reached without the gateway. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ManagementAccessFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String path = request.getServletPath();
        if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
        boolean health = (path.equals("/actuator/health") || path.startsWith("/actuator/health/"))
                && (request.getMethod().equals("GET") || request.getMethod().equals("HEAD"));
        if ((path.equals("/actuator") || path.startsWith("/actuator/")) && !health) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (path.startsWith("/api/v1/")) {
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Pragma", "no-cache");
        }
        chain.doFilter(request, response);
    }
}
