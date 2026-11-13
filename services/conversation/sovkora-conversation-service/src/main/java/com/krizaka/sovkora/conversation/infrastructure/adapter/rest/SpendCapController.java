package com.krizaka.sovkora.conversation.infrastructure.adapter.rest;

import static java.util.Objects.requireNonNull;

import com.krizaka.sovkora.engine.interceptors.governance.ProviderPrice;
import com.krizaka.sovkora.engine.interceptors.governance.SpendCap;
import com.krizaka.sovkora.engine.interceptors.governance.SpendCapAction;
import com.krizaka.sovkora.engine.interceptors.governance.SpendCapAdministration;
import com.krizaka.sovkora.engine.interceptors.governance.SpendScope;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The monthly provider-spend caps (krizaka-internal#3368): what each environment and product key
 * may spend with providers in a calendar month, and what happens at the cap. Administrators only;
 * every write is on the operator surface and leaves an audit line ({@link OperatorAuditPaths}) and
 * a change row (old and new values). There is no {@code DELETE}: a cap is created or changed, never
 * removed, so no scope runs uncapped.
 */
@RestController
@RequestMapping("/api/v1/admin/spend-caps")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class SpendCapController {

  private static final Logger logger = LoggerFactory.getLogger(SpendCapController.class);

  private final SpendCapAdministration caps;

  public SpendCapController(SpendCapAdministration caps) {
    this.caps = requireNonNull(caps, "SpendCapAdministration must not be null");
  }

  /** The body of a cap update. */
  public record Request(
      BigDecimal monthlyCapUsd, SpendCapAction action, String downgradeModelKey) {}

  /** One cap as the console shows it. */
  public record Cap(
      String environment,
      String productKey,
      BigDecimal monthlyCapUsd,
      SpendCapAction action,
      String downgradeModelKey) {}

  /** One price row, so the console shows which models a downgrade may name. */
  public record Price(
      String modelKey,
      String capability,
      BigDecimal inputUsdPerMillionTokens,
      BigDecimal outputUsdPerMillionTokens) {}

  /** The caps, the actions on offer and the price table. */
  public record Response(List<SpendCapAction> actions, List<Cap> caps, List<Price> prices) {}

  /**
   * Every cap, with the actions on offer and the price table.
   *
   * @return the caps, ordered by environment and product key
   */
  @GetMapping
  public Response list() {
    return new Response(
        List.of(SpendCapAction.values()),
        caps.caps().stream().map(SpendCapController::view).toList(),
        caps.prices().stream().map(SpendCapController::view).toList());
  }

  /**
   * Creates or changes the cap of one environment and product key.
   *
   * @param environment the environment
   * @param productKey the product key ({@code unattributed} for turns that declare none)
   * @param request the new amount (greater than zero), action and, for a downgrade, the cheaper
   *     chat model
   * @return the stored cap
   * @throws InvalidRequestProblem when the body is incomplete, the amount is not above zero or the
   *     downgrade model has no chat price row (400)
   */
  @PutMapping("/{environment}/{productKey}")
  public Cap put(
      @PathVariable String environment,
      @PathVariable String productKey,
      @RequestBody Request request) {
    if (request == null || request.monthlyCapUsd() == null || request.action() == null) {
      throw new InvalidRequestProblem("monthlyCapUsd and action are required");
    }
    try {
      SpendCap stored =
          caps.put(
              new SpendCap(
                  new SpendScope(environment, productKey),
                  request.monthlyCapUsd(),
                  request.action(),
                  request.downgradeModelKey()));
      logger.info(
          "Spend cap of scope {} set to action {}", stored.scope().label(), stored.action());
      return view(stored);
    } catch (IllegalArgumentException invalid) {
      throw new InvalidRequestProblem(invalid.getMessage(), invalid);
    }
  }

  private static Cap view(SpendCap cap) {
    return new Cap(
        cap.scope().environment(),
        cap.scope().productKey(),
        cap.monthlyCapUsd(),
        cap.action(),
        cap.downgradeModelKey());
  }

  private static Price view(ProviderPrice price) {
    return new Price(
        price.modelKey(),
        price.capability(),
        price.inputUsdPerMillionTokens(),
        price.outputUsdPerMillionTokens());
  }
}
