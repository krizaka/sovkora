package com.krizaka.sovkora.edge.infrastructure.config.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** The edge is an API: nothing it answers is ever indexed (api.sovkora.com, #2132). */
class RobotsTagFilterTest {

  @Test
  @DisplayName("[2132] every answer of the edge says noindex, a refusal included")
  void everyAnswerIsNoindex() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    new RobotsTagFilter()
        .doFilter(new MockHttpServletRequest("GET", "/"), response, new MockFilterChain());

    assertThat(response.getHeader("X-Robots-Tag")).isEqualTo("noindex, nofollow");
  }
}
