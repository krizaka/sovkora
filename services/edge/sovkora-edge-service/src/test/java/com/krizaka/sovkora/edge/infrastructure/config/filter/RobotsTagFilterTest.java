package com.krizaka.sovkora.edge.infrastructure.config.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/** The edge is an API: nothing it answers is ever indexed (api.sovkora.com, #2132). */
class RobotsTagFilterTest {

  /** [2132] every answer of the edge says noindex, before the chain runs. */
  @Test
  void everyAnswerIsNoindex() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    new RobotsTagFilter()
        .doFilter(
            new org.springframework.mock.web.MockHttpServletRequest("GET", "/"),
            response,
            (request, answered) ->
                assertThat(((MockHttpServletResponse) answered).getHeader("X-Robots-Tag"))
                    .isEqualTo("noindex, nofollow"));

    assertThat(response.getHeader(RobotsTagFilter.HEADER)).isEqualTo(RobotsTagFilter.VALUE);
  }
}
