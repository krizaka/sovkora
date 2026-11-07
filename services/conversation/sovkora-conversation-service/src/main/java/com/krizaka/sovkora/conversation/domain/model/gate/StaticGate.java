package com.krizaka.sovkora.conversation.domain.model.gate;

/**
 * Level 0 Immutability Gate — sealed hierarchy for exhaustive intent classification.
 *
 * <p>Each gate represents a static, compile-time-verified ingress checkpoint. The exhaustive {@code
 * switch} in {@link com.krizaka.sovkora.conversation.application.service.GateService} guarantees
 * every gate variant is handled. Level 0 gates perform ZERO database interaction.
 */
public sealed interface StaticGate permits AuthGate, SecOpsGate, CostShieldGate {}
