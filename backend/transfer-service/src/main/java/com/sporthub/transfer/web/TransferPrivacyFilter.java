package com.sporthub.transfer.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TransferPrivacyFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getRequestURI().startsWith("/api/v1/transfers")) {
      response.setHeader("Cache-Control", "private, no-store, max-age=0");
      response.setHeader("Pragma", "no-cache");
    }
    chain.doFilter(request, response);
  }
}
