package com.krizaka.sovkora.conversation.infrastructure.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.krizaka.sovkora.engine.interceptors.governance.ProviderPrice;
import com.krizaka.sovkora.engine.interceptors.governance.SpendCap;
import com.krizaka.sovkora.engine.interceptors.governance.SpendCapAction;
import com.krizaka.sovkora.engine.interceptors.governance.SpendCapAdministration;
import com.krizaka.sovkora.engine.interceptors.governance.SpendScope;
import com.krizaka.web.KrizakaWebProperties;
import com.krizaka.web.problem.ProblemDetailsAdvice;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The admin endpoint of the monthly provider-spend caps (krizaka-internal#3368): read, create or
 * change, never remove; a cap above zero; administrators only, on the audited operator surface.
 */
class SpendCapControllerTest {

  private static final String BASE = "/api/v1/admin/spend-caps";

  private SpendCapAdministration caps;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    caps = mock(SpendCapAdministration.class);
    mvc =
        MockMvcBuilders.standaloneSetup(new SpendCapController(caps))
            .setControllerAdvice(new ProblemDetailsAdvice(new KrizakaWebProperties(null, null)))
            .build();
  }

  @Test
  @DisplayName("GET lists the caps, the actions on offer and the price table")
  void listsCaps() throws Exception {
    when(caps.caps())
        .thenReturn(
            List.of(
                new SpendCap(
                    new SpendScope("dev", "verikora"),
                    new BigDecimal("20"),
                    SpendCapAction.REFUSE,
                    null)));
    when(caps.prices())
        .thenReturn(
            List.of(
                new ProviderPrice(
                    "do-inference", "CHAT", new BigDecimal("0.65"), new BigDecimal("0.65"))));

    mvc.perform(get(BASE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.actions.length()").value(2))
        .andExpect(jsonPath("$.caps[0].environment").value("dev"))
        .andExpect(jsonPath("$.caps[0].productKey").value("verikora"))
        .andExpect(jsonPath("$.caps[0].action").value("REFUSE"))
        .andExpect(jsonPath("$.prices[0].modelKey").value("do-inference"));
  }

  @Test
  @DisplayName("PUT changes a cap and answers the stored row")
  void putStores() throws Exception {
    when(caps.put(any(SpendCap.class))).thenAnswer(call -> call.getArgument(0));

    mvc.perform(
            put(BASE + "/dev/verikora")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"monthlyCapUsd\":150,\"action\":\"DOWNGRADE\",\"downgradeModelKey\":\"small\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.monthlyCapUsd").value(150))
        .andExpect(jsonPath("$.action").value("DOWNGRADE"))
        .andExpect(jsonPath("$.downgradeModelKey").value("small"));

    verify(caps)
        .put(
            new SpendCap(
                new SpendScope("dev", "verikora"),
                new BigDecimal("150"),
                SpendCapAction.DOWNGRADE,
                "small"));
  }

  @Test
  @DisplayName("PUT with a cap of zero or less is a 400: a cap is never switched off by an amount")
  void putRejectsNoCap() throws Exception {
    mvc.perform(
            put(BASE + "/dev/verikora")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"monthlyCapUsd\":0,\"action\":\"REFUSE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-request"));

    verifyNoMoreInteractions(caps);
  }

  @Test
  @DisplayName("PUT naming a downgrade model with no chat price row is a 400")
  void putRejectsUnknownDowngradeModel() throws Exception {
    when(caps.put(any(SpendCap.class)))
        .thenThrow(new IllegalArgumentException("the downgrade model has no price row"));

    mvc.perform(
            put(BASE + "/dev/verikora")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"monthlyCapUsd\":10,\"action\":\"DOWNGRADE\",\"downgradeModelKey\":\"nope\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("a cap cannot be deleted: there is no DELETE")
  void noDelete() throws Exception {
    mvc.perform(delete(BASE + "/dev/verikora")).andExpect(status().isMethodNotAllowed());
  }

  @Test
  @DisplayName("the controller is administrators only, and its writes are on the audited surface")
  void adminOnlyAndAudited() {
    PreAuthorize guard = SpendCapController.class.getAnnotation(PreAuthorize.class);

    assertThat(guard).isNotNull();
    assertThat(guard.value()).isEqualTo("hasAuthority('ROLE_ADMIN')");
    assertThat(OperatorAuditPaths.audits(BASE + "/{environment}/{productKey}")).isTrue();
  }
}
