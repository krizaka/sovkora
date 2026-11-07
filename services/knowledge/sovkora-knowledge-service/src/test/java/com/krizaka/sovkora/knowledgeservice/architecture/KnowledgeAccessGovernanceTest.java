package com.krizaka.sovkora.knowledgeservice.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaModifier.PUBLIC;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.krizaka.sovkora.knowledgeservice.application.service.KnowledgeQueryService;
import com.krizaka.sovkora.knowledgeservice.domain.model.ActingPrincipal;
import com.krizaka.sovkora.knowledgeservice.infrastructure.adapter.rest.KnowledgeController;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.core.domain.properties.HasOwner;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * [ACL-001] The access rule cannot be skipped by construction (ADR-091, sovkora-engine#107): a read
 * of the knowledge is made for a principal, the vector store is searched in one place — the one
 * that adds the principal's filter — and an internal endpoint cannot be written without the
 * verified token that names the principal.
 */
class KnowledgeAccessGovernanceTest {

  private static final String BASE_PACKAGE = "com.krizaka.sovkora.knowledgeservice";

  private static JavaClasses production;

  @BeforeAll
  static void importClasses() {
    production =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);
  }

  @Test
  @DisplayName("[ACL-001] only KnowledgeQueryService searches the vector store")
  void onlyTheQueryServiceSearchesTheVectorStore() {
    noClasses()
        .that()
        .doNotHaveFullyQualifiedName(KnowledgeQueryService.class.getName())
        .and()
        .resideOutsideOfPackage("..recommendation..")
        .should()
        .callMethodWhere(
            JavaCall.Predicates.target(
                HasOwner.Predicates.With.owner(assignableTo(VectorStore.class))
                    .and(HasName.Predicates.name("similaritySearch"))))
        .because("a search of the store that does not go through the access filter leaks")
        .check(production);
  }

  @Test
  @DisplayName("[ACL-001] every public read of KnowledgeQueryService takes the principal first")
  void everyReadTakesAPrincipal() {
    methods()
        .that()
        .areDeclaredIn(KnowledgeQueryService.class)
        .and()
        .haveModifier(PUBLIC)
        .should(takeAPrincipalFirst())
        .because("there is no way to read the knowledge for nobody")
        .check(production);
  }

  @Test
  @DisplayName("[ACL-001] every endpoint of the knowledge controller takes the verified token")
  void everyEndpointTakesTheToken() {
    methods()
        .that()
        .areDeclaredIn(KnowledgeController.class)
        .and()
        .haveModifier(PUBLIC)
        .should(
            new ArchCondition<JavaMethod>("take the verified Jwt") {
              @Override
              public void check(JavaMethod method, ConditionEvents events) {
                boolean takesToken =
                    method.getRawParameterTypes().stream()
                        .anyMatch(type -> type.isAssignableTo(Jwt.class));
                events.add(
                    new SimpleConditionEvent(
                        method, takesToken, method.getFullName() + " takes a Jwt: " + takesToken));
              }
            })
        .check(production);
  }

  @Test
  @DisplayName("[ACL-001] the controller's request bodies carry no identity")
  void requestBodiesCarryNoIdentity() {
    classes()
        .that()
        .resideInAPackage(BASE_PACKAGE + ".infrastructure.adapter.rest.dto")
        .should(
            new ArchCondition<JavaClass>("name no user or tenant") {
              @Override
              public void check(JavaClass type, ConditionEvents events) {
                type.getFields()
                    .forEach(
                        field -> {
                          String name = field.getName().toLowerCase(Locale.ROOT);
                          boolean identity =
                              name.contains("user")
                                  || name.contains("tenant")
                                  || name.contains("role");
                          events.add(
                              new SimpleConditionEvent(
                                  field,
                                  !identity,
                                  type.getName() + "." + field.getName() + " names an identity"));
                        });
              }
            })
        .because("who is asking is the token's claim, never a field a caller can write")
        .check(production);
  }

  private static ArchCondition<JavaMethod> takeAPrincipalFirst() {
    return new ArchCondition<>("take an ActingPrincipal as first parameter") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        var parameters = method.getRawParameterTypes();
        boolean ok =
            !parameters.isEmpty() && parameters.getFirst().isEquivalentTo(ActingPrincipal.class);
        events.add(
            new SimpleConditionEvent(method, ok, method.getFullName() + " has no principal"));
      }
    };
  }
}
