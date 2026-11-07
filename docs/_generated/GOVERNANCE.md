---
title: Governance Rules
description: The architecture rules enforced at build time, and the suites that enforce each one, extracted from orazaka-test-support.
category: Architecture
order: 8
generated: true
---

# Governance Rules


A rule with **no suite** is a rule that enforces nothing — it is written, it compiles, and no
build fails when it is violated. Treat an empty cell as a defect, not as a gap.


## ConfigBindingRules

| Rule | Enforced by | Summary |
|:---|:---|:---|
| `assertConfigurationBindsUnambiguously` | `AutomationServiceGovernanceTest`<br/>`ConfigBindingGuardTest`<br/>`EdgeGovernanceTest`<br/>`JobServiceGovernanceTest`<br/>`KnowledgeServiceGovernanceTest`<br/>`RouterGovernanceTest`<br/>`StudioServiceGovernanceTest` | [CFG-001] over the Sovkora and Krizaka classes on this module's classpath — krizaka-test-support's com.krizaka.test.architecture.ConfigBindingRules#assertConfigurationBindsUnambiguously. |
| `assertInjectableComponentsHaveOneConstructor` | `AutomationServiceGovernanceTest`<br/>`EdgeGovernanceTest`<br/>`JobServiceGovernanceTest`<br/>`KnowledgeServiceGovernanceTest`<br/>`RouterGovernanceTest`<br/>`StudioServiceGovernanceTest` | [CFG-001] every Spring bean on this module's classpath has a constructor it can be built with. |

## ExecutorCoherenceRules

| Rule | Enforced by | Summary |
|:---|:---|:---|
| `assertEveryCapabilityHasAnExecutor` | `AutomationServiceGovernanceTest`<br/>`BusinessGovernanceTest`<br/>`EdgeGovernanceTest`<br/>`InterceptorsGovernanceTest`<br/>`JobServiceGovernanceTest`<br/>`KnowledgeServiceGovernanceTest`<br/>`RouterGovernanceTest`<br/>`StudioServiceGovernanceTest` | [EXEC-001] Every enabled capability executed <b>in process</b> has a JobExecutor for its handler_key. |
| `assertEveryCapabilityIsDrained` | `AutomationServiceGovernanceTest`<br/>`BusinessGovernanceTest`<br/>`EdgeGovernanceTest`<br/>`InterceptorsGovernanceTest`<br/>`JobServiceGovernanceTest`<br/>`KnowledgeServiceGovernanceTest`<br/>`RouterGovernanceTest`<br/>`StudioServiceGovernanceTest` | [EXEC-002] Every enabled capability's routing_key is drained by some worker's declared bindings. |

## GovernanceRules

| Rule | Enforced by | Summary |
|:---|:---|:---|
| `assertCollectionFieldsPrivateFinal` | `BusinessGovernanceTest`<br/>`InterceptorsGovernanceTest`<br/>`ToolsGovernanceTest` | Collection fields are private final [ADR-007]. |
| `assertDedupIsAtomic` | `JobServiceGovernanceTest` | [KIT-002] Message deduplication has one author: krizaka-messaging. |
| `assertDomainHasNoTransportDtos` | `BusinessGovernanceTest`<br/>`StudioServiceGovernanceTest` | The domain holds no *Request/*Response [ERR-130] — krizaka-test-support's rule. |
| `assertDomainPurity` | `ToolsGovernanceTest` | [HEX-002] The domain depends on no framework. |
| `assertEndpointRuleHasOneAuthor` | `JobServiceGovernanceTest` | [CAP-002] The endpoint rule has one author. |
| `assertEveryCapabilityHasAnExecutor` | `AutomationServiceGovernanceTest`<br/>`BusinessGovernanceTest`<br/>`EdgeGovernanceTest`<br/>`InterceptorsGovernanceTest`<br/>`JobServiceGovernanceTest`<br/>`KnowledgeServiceGovernanceTest`<br/>`RouterGovernanceTest`<br/>`StudioServiceGovernanceTest` | [EXEC-001] Fails when an in-process capability's handler_key has no JobExecutor. |
| `assertEveryCapabilityIsDrained` | `AutomationServiceGovernanceTest`<br/>`BusinessGovernanceTest`<br/>`EdgeGovernanceTest`<br/>`InterceptorsGovernanceTest`<br/>`JobServiceGovernanceTest`<br/>`KnowledgeServiceGovernanceTest`<br/>`RouterGovernanceTest`<br/>`StudioServiceGovernanceTest` | [EXEC-002] Fails when a capability's routing_key is drained by no declared worker. |
| `assertFieldsPrivate` | `BusinessGovernanceTest`<br/>`InterceptorsGovernanceTest`<br/>`PersistenceBridgeGovernanceTest`<br/>`PersistenceGovernanceTest`<br/>`ToolsGovernanceTest` | Instance fields of concrete classes are private [ADR-009]. |
| `assertImplClassesPackagePrivate` | `PersistenceGovernanceTest` | *Impl classes are not public [ERR-105]. |
| `assertInternalSurfaceRequiresServiceAuthority` | `AutomationServiceGovernanceTest`<br/>`EdgeGovernanceTest`<br/>`JobServiceGovernanceTest`<br/>`KnowledgeServiceGovernanceTest`<br/>`RouterGovernanceTest`<br/>`StudioServiceGovernanceTest` | Fails when a SecurityConfig matches /internal/v1/** without demanding the SERVICE authority. |
| `assertMappersFinal` | `InterceptorsGovernanceTest`<br/>`PersistenceBridgeGovernanceTest` | *Mapper classes are final [ERR-107]. |
| `assertMappersPackagePrivate` | `InterceptorsGovernanceTest`<br/>`PersistenceBridgeGovernanceTest` | *Mapper classes are not public [ERR-107]. |
| `assertNoAnonymousClasses` | `BusinessGovernanceTest`<br/>`InterceptorsGovernanceTest`<br/>`PersistenceBridgeGovernanceTest`<br/>`PersistenceGovernanceTest`<br/>`ToolsGovernanceTest` | No anonymous class in production [GOV-001]. |
| `assertNoClassAtPackageRoot` | `PackageRootRulesTest` | Asserts nothing sits at a module's package root: a class there belongs in domain, application or infrastructure (repository layout standard, krizaka/.github docs/standards/repo-layout.md). |
| `assertNoDependencyOn` | `BusinessBoundaryTest`<br/>`InterceptorsBoundaryTest` | Asserts that classes in modulePackage do not depend on forbiddenPackage. |
| `assertNoFieldInjection` | `BusinessBoundaryTest`<br/>`InterceptorsBoundaryTest`<br/>`JobServiceGovernanceTest`<br/>`PersistenceBridgeGovernanceTest`<br/>`StudioServiceGovernanceTest` | No @Autowired field injection [GOV-005] — krizaka-test-support's rule. |
| `assertNoForeignTier3Dependency` | `AutomationServiceGovernanceTest`<br/>`JobServiceGovernanceTest`<br/>`KnowledgeServiceGovernanceTest`<br/>`StudioServiceGovernanceTest` | [SEAM-002] Asserts an autonomous service depends on no bounded context's Tier-3 (owned-domain) implementation. |
| `assertNoPackKeyConditionals` | `AutomationServiceGovernanceTest`<br/>`BusinessGovernanceTest`<br/>`EdgeGovernanceTest`<br/>`InterceptorsGovernanceTest`<br/>`JobServiceGovernanceTest`<br/>`KnowledgeServiceGovernanceTest`<br/>`RouterGovernanceTest`<br/>`StudioServiceGovernanceTest` | [PACK-003] Fails when engine code branches on one of those identifiers — inline, or through a constant declared in the same file. |
| `assertNoPackKeyLiterals` | `AutomationServiceGovernanceTest`<br/>`BusinessGovernanceTest`<br/>`EdgeGovernanceTest`<br/>`InterceptorsGovernanceTest`<br/>`JobServiceGovernanceTest`<br/>`KnowledgeServiceGovernanceTest`<br/>`RouterGovernanceTest`<br/>`StudioServiceGovernanceTest` | [PACK-002] Fails when a pack, studio, or pack-capability key appears as a literal in engine code under engine/**, services/** or workers/**. |
| `assertNoPermitAllOnInternalOrUploads` | `AutomationServiceGovernanceTest`<br/>`JobServiceGovernanceTest`<br/>`KnowledgeServiceGovernanceTest`<br/>`RouterGovernanceTest`<br/>`StudioServiceGovernanceTest` | Fails when a SecurityConfig opens /internal/** or /uploads/**. |
| `assertNoRedundantPrefix` | `BusinessGovernanceTest`<br/>`InterceptorsGovernanceTest`<br/>`JobServiceGovernanceTest`<br/>`PersistenceBridgeGovernanceTest`<br/>`PersistenceGovernanceTest`<br/>`StudioServiceGovernanceTest`<br/>`ToolsGovernanceTest` | No class name starts with Sovkora [ERR-104]. |
| `assertNoStandardStreams` | `BusinessGovernanceTest`<br/>`InterceptorsGovernanceTest`<br/>`JobServiceGovernanceTest`<br/>`PersistenceBridgeGovernanceTest`<br/>`PersistenceGovernanceTest`<br/>`StudioServiceGovernanceTest`<br/>`ToolsGovernanceTest` | No class writes to standard streams [GOV-004] — krizaka-test-support's rule. |
| `assertNoWebControllers` | `BusinessGovernanceTest`<br/>`InterceptorsGovernanceTest`<br/>`PersistenceBridgeGovernanceTest`<br/>`PersistenceGovernanceTest`<br/>`ToolsGovernanceTest` | No web controller in the module [ERR-112]. |
| `assertOneCapabilityModel` | `JobServiceGovernanceTest` | [CAP-001] One record models a capability, and the projections of it say so. |
| `assertOneDeclarationAuthor` | `StudioServiceGovernanceTest` | [SAGA-002] One author resolves what a dispatched step carries from its pack. |
| `assertOneSettlementAuthor` | `StudioServiceGovernanceTest` |  |
| `assertOneTopLevelClassPerFile` | `BusinessGovernanceTest`<br/>`InterceptorsGovernanceTest`<br/>`JobServiceGovernanceTest`<br/>`PersistenceBridgeGovernanceTest`<br/>`PersistenceGovernanceTest`<br/>`StudioServiceGovernanceTest`<br/>`ToolsGovernanceTest` | Every top-level class lives in a file named after it [ERR-103] — krizaka-test-support's rule. |
| `assertOutboxRelaysClaim` | `JobServiceGovernanceTest` | [KIT-003] An outbox store claims the rows the relay publishes, and the relay has one author. |
| `assertPersistenceAdapterPackageKind` | **none** | adapter/persistence holds only *Adapter/*Mapper [ERR-130]. |
| `assertPersistencePackageHygiene` | `PersistenceGovernanceTest` | JPA converters, entities and repositories live in their sub-packs [ERR-109]. |
| `assertSagaReadersDoNotWrite` | `StudioServiceGovernanceTest` | [SAGA-003] The saga's read-only invariants stay read-only. |
| `assertSecurityBaselineIsUniform` | `JobServiceGovernanceTest` | [KIT-001] Every service's filter chain starts from the one security baseline. |
| `assertServicePackageOnlyServices` | `JobServiceGovernanceTest`<br/>`StudioServiceGovernanceTest`<br/>`ToolsGovernanceTest` | application/service holds only capability-named *Service classes [ERR-129] — krizaka-test-support's rule. |
| `assertSessionSecurityHasOneAuthor` | `JobServiceGovernanceTest` | [KIT-004] Session-token security has one author: krizaka-security. |
| `assertStrictHexagonalBoundaries` | `ToolsGovernanceTest` | [HEX-001] Enforces strict hexagonal layer dependencies using ArchUnit's layeredArchitecture. |
| `assertSupportPackageHygiene` | **none** | Asserts infrastructure.support holds only cross-cutting helpers / shared plumbing — never a use-case *Service, a *Controller/*Adapter, or config [ERR-130]. |
| `assertVirtualThreadsEnabled` | `AutomationServiceGovernanceTest`<br/>`EdgeGovernanceTest`<br/>`JobServiceGovernanceTest`<br/>`KnowledgeServiceGovernanceTest`<br/>`RouterGovernanceTest`<br/>`StudioServiceGovernanceTest` | A service that serves HTTP runs on virtual threads (AGENTS.md §4). |

## GovernanceSubjects

| Rule | Enforced by | Summary |
|:---|:---|:---|
| `assertEveryRuleIsNonVacuous` | **none** | [GOV-006] Asserts every governance rule in this package is non-vacuous by construction. |

## LaneCoherenceRules

| Rule | Enforced by | Summary |
|:---|:---|:---|
| `assertEveryCapabilityDeclaresItsLane` | `JobServiceGovernanceTest` | [LANE-001] Every seeded capability whose routing key a lane binds declares that lane's class. |

## LoggedContentRules

| Rule | Enforced by | Summary |
|:---|:---|:---|
| `assertNoLoggingCallTakesContent` | `AutomationServiceGovernanceTest`<br/>`EdgeGovernanceTest`<br/>`GovernanceTest`<br/>`JobServiceGovernanceTest`<br/>`KnowledgeServiceGovernanceTest`<br/>`RouterGovernanceTest`<br/>`StudioServiceGovernanceTest` | Asserts [LOG-001] over the given classes: no argument of a logging call is, or was derived from, a prompt, a response body or a message text. |

## MeteringMarkerRules

| Rule | Enforced by | Summary |
|:---|:---|:---|
| `assertEveryJobProducerDeclaresItsMetering` | `MeteringMarkerTest` | Asserts that every class publishing to the jobs exchange stamps #MARKER or is exempt with a written reason. |

## PackCoherenceRules

| Rule | Enforced by | Summary |
|:---|:---|:---|
| `assertPackCatalogueIsCoherent` | `SqlBoundaryTest` | Asserts the pack catalogue is coherent: infra/initdb seeds no pack, and no bundle manifest declares a pack the database would refuse. |

## RunSurfaceRules

| Rule | Enforced by | Summary |
|:---|:---|:---|
| `assertNoInboundEntryDispatchesAJob` | `EdgeGovernanceTest`<br/>`JobServiceGovernanceTest`<br/>`KnowledgeServiceGovernanceTest`<br/>`RouterGovernanceTest`<br/>`StudioServiceGovernanceTest` | Asserts [DOOR-001] over this module's production classes, against the whole repository's sinks. |

## SourceFileScanner

| Rule | Enforced by | Summary |
|:---|:---|:---|
| `assertNoBannedLiterals` | `EdgeGovernanceTest` | Scans Java source files under sourceRoot for default banned patterns [GOV-003]. |
| `assertNoEnvironmentInjection` | `AutomationServiceGovernanceTest`<br/>`BusinessGovernanceTest`<br/>`EdgeGovernanceTest`<br/>`GovernanceTest`<br/>`InterceptorsGovernanceTest`<br/>`JobServiceGovernanceTest`<br/>`KnowledgeServiceGovernanceTest`<br/>`PersistenceBridgeGovernanceTest`<br/>`PersistenceGovernanceTest`<br/>`RouterGovernanceTest`<br/>`StudioServiceGovernanceTest`<br/>`ToolsGovernanceTest` | Scans for org.springframework.core.env.Environment imports in non-@Configuration classes [ERR-113]. |

## SqlBoundaryRules

| Rule | Enforced by | Summary |
|:---|:---|:---|
| `assertNoCrossContextForeignKeys` | `SqlBoundaryTest` | Asserts that every REFERENCES in every *.sql file under initdbDir targets a table created in the same file (same bounded context). |
| `assertNoCrossContextForeignKeysInMigrations` | `SqlBoundaryRulesTest` | [SEAM-001] on the Flyway migrations: every src/main/resources/db/migration folder under repoRoot is one context (its V files together create its tables), and a REFERENCES may only target a table that folder creates. |
