package com.krizaka.sovkora.core.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import com.krizaka.sovkora.core.domain.model.security.Actor;
import com.krizaka.sovkora.core.domain.ports.inbound.McpService;
import com.krizaka.sovkora.core.domain.ports.outbound.KnowledgeService;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolRegistry;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

/**
 * [ACL-002] No retrieval or tool-execution port method is made for nobody, and no tool runs without
 * having been authorized for an actor (ADR-091, sovkora-engine#107).
 */
class AccessGovernanceTest {

  private static JavaClasses production;

  @BeforeAll
  static void importClasses() {
    production =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.krizaka.sovkora.core");
  }

  private static ArchCondition<JavaMethod> takeAnActor() {
    return new ArchCondition<>("take an Actor") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        boolean takes =
            method.getRawParameterTypes().stream()
                .anyMatch(type -> type.isEquivalentTo(Actor.class));
        events.add(
            new SimpleConditionEvent(method, takes, method.getFullName() + " takes no Actor"));
      }
    };
  }

  @Test
  @DisplayName("[ACL-002] every knowledge read and every tool listing or execution takes an Actor")
  void everyPortMethodTakesAnActor() {
    for (Class<?> port : new Class<?>[] {KnowledgeService.class, McpService.class}) {
      methods().that().areDeclaredIn(port).should(takeAnActor()).check(production);
    }
    methods()
        .that()
        .areDeclaredIn(ToolRegistry.class)
        .and()
        .haveNameMatching("getRegisteredTools|authorize")
        .should(takeAnActor())
        .check(production);
  }

  @Test
  @DisplayName("[ACL-002] only the actor-bound wrapper, the cache and the executor call a tool")
  void onlyTheExecutorCallsATool() {
    noClasses()
        .that(
            new DescribedPredicate<JavaClass>("are not the executor, its wrapper or the cache") {
              @Override
              public boolean test(JavaClass type) {
                return !"DefaultMcpService".equals(type.getSimpleName())
                    && !"ActorBoundToolCallback".equals(type.getSimpleName())
                    && !"CachingToolCallback".equals(type.getSimpleName());
              }
            })
        .should()
        .callMethod(ToolCallback.class, "call", String.class)
        .because("a tool is run for an authorized actor, through the executor")
        .check(production);
  }

  @Test
  @DisplayName("[ACL-002] the executor authorizes before it runs")
  void theExecutorAuthorizes() {
    classes()
        .that()
        .haveSimpleName("DefaultMcpService")
        .should()
        .callMethod(ToolRegistry.class, "authorize", Actor.class, String.class)
        .check(production);
  }
}
