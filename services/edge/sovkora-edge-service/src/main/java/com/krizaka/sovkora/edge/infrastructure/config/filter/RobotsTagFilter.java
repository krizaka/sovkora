package com.krizaka.sovkora.edge.infrastructure.config.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * The edge is an API for servers and apps, never a page: every answer, a refusal included, tells
 * search engines not to index it ({@code api.sovkora.com}, krizaka-internal#2132). Set before the
 * chain runs, so an answer the chain commits early still carries it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class RobotsTagFilter implements Filter {

  static final String HEADER = "X-Robots-Tag";
  static final String VALUE = "noindex, nofollow";

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (response instanceof HttpServletResponse http) {
      http.setHeader(HEADER, VALUE);
    }
    chain.doFilter(request, response);
  }
}
