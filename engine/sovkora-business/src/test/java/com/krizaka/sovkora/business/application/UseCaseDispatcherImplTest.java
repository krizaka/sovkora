package com.krizaka.sovkora.business.application;

import static org.junit.jupiter.api.Assertions.*;

import com.krizaka.sovkora.business.api.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UseCaseDispatcherImplTest {

  private static UseCase<Payload, String> chatUseCase(RbacPolicy rbac) {
    return new UseCase<>() {
      @Override
      public UseCaseDescriptor descriptor() {
        return new UseCaseDescriptor(
            "chat.x", Capability.CHAT, Set.of(), PlanningMode.DETERMINISTIC, Set.of(), rbac);
      }

      @Override
      public String execute(UseCaseContext ctx, Payload input) {
        return "handled:" + ((ChatPayload) input).prompt();
      }
    };
  }

  private static Intention chatIntention(Set<String> authorities) {
    return new Intention(
        null,
        IntentionType.QUERY,
        ExecutionMode.SYNC,
        Capability.CHAT,
        "chat",
        new ChatPayload("hi", null),
        new IntentionContext("session-1", "actor-1", authorities, null));
  }

  @Test
  void dispatch_resolvesAndExecutes() {
    var dispatcher =
        new UseCaseDispatcherImpl(
            new SpringUseCaseRegistry(List.of(chatUseCase(RbacPolicy.PERMIT_ALL))));
    String result = dispatcher.dispatch(chatIntention(Set.of()));
    assertEquals("handled:hi", result);
  }

  @Test
  void dispatch_noUseCaseForCapability_throwsErr410() {
    var dispatcher = new UseCaseDispatcherImpl(new SpringUseCaseRegistry(List.of()));
    var ex =
        assertThrows(
            UseCaseResolutionException.class, () -> dispatcher.dispatch(chatIntention(Set.of())));
    assertTrue(ex.getMessage().contains("ERR-410"));
  }

  @Test
  void dispatch_rbacDenied_throwsErr403() {
    var dispatcher =
        new UseCaseDispatcherImpl(
            new SpringUseCaseRegistry(List.of(chatUseCase(new RbacPolicy(Set.of("ROLE_ADMIN"))))));
    var ex =
        assertThrows(
            UseCaseResolutionException.class,
            () -> dispatcher.dispatch(chatIntention(Set.of("ROLE_USER"))));
    assertTrue(ex.getMessage().contains("ERR-403"));
  }

  @Test
  void dispatch_rbacAllowed_whenActorHoldsRequiredAuthority() {
    var dispatcher =
        new UseCaseDispatcherImpl(
            new SpringUseCaseRegistry(List.of(chatUseCase(new RbacPolicy(Set.of("ROLE_ADMIN"))))));
    assertEquals("handled:hi", dispatcher.dispatch(chatIntention(Set.of("ROLE_ADMIN"))));
  }

  @Test
  void registry_resolvesByCapability_andAllListsDescriptors() {
    var registry = new SpringUseCaseRegistry(List.of(chatUseCase(RbacPolicy.PERMIT_ALL)));
    assertTrue(registry.resolve(chatIntention(Set.of())).isPresent());
    assertEquals(1, registry.all().size());
    assertEquals("chat.x", registry.all().iterator().next().id());
  }
}
