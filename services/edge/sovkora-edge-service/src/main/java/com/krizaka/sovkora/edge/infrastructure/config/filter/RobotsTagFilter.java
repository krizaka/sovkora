package com.krizaka.sovkora.edge.infrastructure.config.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The edge is an API for servers and apps, never a page: every answer, a refusal included, tells
 * search engines not to index it ({@code api.sovkora.com}, krizaka-internal#2132). Set before the
 * chain runs, so an answer the chain commits early still carries it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class RobotsTagFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    response.setHeader("X-Robots-Tag", "noindex, nofollow");
    chain.doFilter(request, response);
  }
}
