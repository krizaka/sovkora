---
title: "Sovkora — État des lieux technique et reste à faire"
description: "Analyse du code réel après la vague 1 de remédiation : ce qui est solide, ce qui reste ouvert, et les tâches priorisées."
category: Architecture
order: 21
---

# Sovkora — État des lieux technique

> **Méthode.** Toute affirmation ci-dessous a été vérifiée dans le code, pas reprise d'un document.
> C'est une précaution née de l'expérience : l'audit précédent (`PRODUCTION_READINESS_AUDIT.md`)
> s'est révélé faux sur trois faits vérifiables et a manqué deux instances. Chaque constat porte donc
> sa mesure ou son fichier. Analyse au commit `d58d58a4`, build exécuté et vert.

| | |
|:---|:---|
| **Verdict** | L'architecture est saine. Ce qui reste n'est pas structurel — c'est une dette d'exploitation et de test, chiffrable en semaines. |
| **Bloquant lancement** | **0** — le dernier P0 (`knowledge-service`) est clos par T1 |
| **Java** | 114 716 lignes / 1 068 fichiers · 8 services · 60 tables sur 6 contextes |
| **Tests** | 45 227 lignes / 460 fichiers · 10 suites de gouvernance |
| **Build** | Réacteur complet vert · E2E vert · `docs build --check` vert |
| **Avancement** | Vague 1 close · générateur de doc refactoré · `package` → `pack` · **T1, T2a, T16–T18 faites**. Reste T2–T15. |

---

## 1. Ce qui est solide — à ne pas toucher

Ces points sont au-dessus de la médiane, y compris pour des systèmes déjà en production. Ils sont
listés d'abord parce que la suite est critique et que le ratio induirait en erreur.

**Le grand livre de crédits.** 9 garde-fous SQL sur `70-billing.sql` : `CHECK` anti-découvert,
`UPDATE` conditionnel en une instruction, trigger `BEFORE UPDATE OR DELETE` pour l'append-only,
idempotence par clé `UNIQUE` **et** table `processed_messages`. Deux gardes indépendants sur la seule
opération dont l'échec est irrattrapable.

**La gouvernance lie vraiment les décisions.** Ce n'est pas décoratif : ADR-034 a déplacé
l'interpréteur DAG hors de `business` parce que SEAM-002 l'interdisait, et la vague 1 a choisi
l'autorité `SERVICE` parce que le convertisseur d'autorités rendait `SCOPE_internal` inapplicable.
Les règles ont changé le design, pas l'inverse.

**La topologie AMQP.** DLQ par file, retry exponentiel, `prefetch=1` avec `concurrency=2..4` sur les
consommateurs d'inférence — et le raisonnement est dans les commentaires YAML. La contre-pression sur
une machine à mémoire contrainte a été pensée, pas héritée d'un template.

**Le cloisonnement multi-tenant du Studio.** Chaque endpoint prend `actor.getSubject()` et le passe à
la requête. Aucun ne fait confiance à un id de chemin seul.

**Depuis la vague 1** : la surface média est propriétaire-scopée avec 404 (jamais 403, pour ne pas
confirmer l'existence), `/internal/v1/**` exige `SERVICE` sur trois services, et deux fitness
functions empêchent le motif de revenir — vérifiées par mutation, y compris contre l'affaiblissement
subtil (`.authenticated()` au lieu de `hasAuthority`).

---

## 2. P0 — bloque le lancement (et une correction)

### 2.1 ✅ Déclassé — le chemin de migration n'est pas un bloquant de lancement

> **Correction.** Cette section classait Flyway en P0. C'est faux, et l'erreur vient d'avoir
> confondu « nécessaire avant que la prod évolue » avec « bloque le lancement ». Elle est conservée
> ici plutôt que supprimée, parce que le raisonnement importe autant que la conclusion.

**Ce qui est vrai** : `flyway.enabled: false` dans 4 services, aucun `db/migration`, 60 tables qui
n'existent que dans `infra/initdb/`, exécuté uniquement sur répertoire de données vide.

**Ce qui est faux** : que cela bloque le lancement. Le premier déploiement se fait sur une base
**vide** — `initdb` s'exécute et le schéma est correct. Rien ne casse. Le besoin réel apparaît au
**premier changement de schéma après le premier déploiement**, ce qui arrive typiquement des jours
ou des semaines plus tard.

**Et le coût d'adoption ne croît pas** : au moment du déploiement, on prend le schéma tel qu'il est
comme `V1__baseline` — un fichier par contexte, généré. Adopter aujourd'hui n'est pas moins cher ;
c'est plus cher, parce que le schéma bouge encore (le Studio vient d'arriver, les vagues 2 à 4 en
ajouteront). On écrirait `V2`, `V3`… pour un schéma que personne n'a déployé, avec les dérives de
checksum que cela implique. AGENTS.md §0 dit d'ailleurs de ne pas implémenter les environnements
différés avant qu'on le demande.

**Quand le faire** : la semaine précédant le premier déploiement, schéma stabilisé. → **T2, P1.**

### 2.1bis 🟠 Ce qui reste vrai et utile dès maintenant : deux natures de seed mélangées

En vérifiant les 35 `INSERT`, une seconde erreur du rapport initial est apparue — il disait « les
seeds doivent sortir des `V*` », en bloc. C'est faux : ils sont de **deux natures opposées**.

| Nature | Tables | Destination en production |
|:---|:---|:---|
| **Données de référence** | `orazaka_models`, `ai_providers`, `orazaka_capabilities`, `billing_plan`, `billing_pack`, `credit_pricebook`, `studio` + `studio_blueprint`, `interceptor_policy`, `rate_limit_tiers`, `orazaka_routing_rules`, les `*_runtime_config` | **Doivent y être.** C'est la configuration produit, délibérément donnée-et-non-code (AGENTS.md §4). |
| **Fixtures de dev** | `users`, `user_profiles`, `credit_wallet`, `credit_ledger_entry`, `billing_subscription`, `rate_limits`, `orazaka_tools_rag_source` | **Ne doivent jamais y être.** Un utilisateur admin factice, son portefeuille, une écriture de grand livre et un abonnement inventés. |

Aujourd'hui les deux sont dans les mêmes fichiers, exécutés ensemble. Conséquence immédiate, sans
attendre la production : **on ne peut pas monter un environnement propre** — il vient toujours avec
un admin factice et une écriture au grand livre.

Cette séparation a de la valeur maintenant, elle est indépendante de Flyway, et elle rend l'adoption
ultérieure quasi triviale. → **T2a, P1.**

### 2.2 ✅ CLOS (T1) — `knowledge-service` : surface interne sans aucune sécurité

**Vérifié** : `0` fichier de configuration de sécurité, et `spring-boot-starter-security` absent du
classpath. `KnowledgeController` expose `/internal/v1/knowledge/retrieve` et
`/internal/v1/knowledge/sources/search` — joignables **sans credential**, sans même un `permitAll()`
à retirer.

Ce service lit les sources RAG de l'acteur. C'est la 4ᵉ instance du finding #3, absente de l'audit et
hors du périmètre de la vague 1.

La règle `assertNoPermitAllOnInternalOrUploads` **ne l'attrape pas** : il n'y a rien à attraper. Le
complément manquant est une règle qui échoue sur *un service exposant `/internal/**` sans sécurité au
classpath*. La doc générée le montre déjà (`⚠ no rule`), mais montrer n'est pas bloquer.

---

## 3. P1 — opérabilité (vague 2)

### 3.1 🟠 Aucune stratégie de sauvegarde ni de restauration

Un conteneur Postgres héberge six bases, dont le grand livre financier. Un seul RabbitMQ. Pas de
réplication, pas de PITR, pas de planification, pas de restauration documentée — et surtout **aucun
exercice de restauration effectué**. Une sauvegarde jamais restaurée est une hypothèse, pas une
sauvegarde.

### 3.2 🟠 Limitation de débit : 1 service sur 8

**Vérifié** : `RateLimitFilter` n'existe que dans `conversation-service`. Absent partout ailleurs —
y compris `job-service`, que l'audit croyait couvert.

Le plus grave : **`identity-service` n'en a pas**, et c'est le service qui expose les seuls endpoints
publics du produit. Sans limite sur `/auth/login`, le bourrage d'identifiants est gratuit ; sans
limite sur `/auth/forgot`, on obtient un bombardement de mails doublé d'une énumération de comptes.

`studio` non plus, alors que `POST /runs` est l'endpoint le plus coûteux du produit : un appel
déclenche jusqu'à `run.fan-out-max` jobs d'inférence. Le plafond de concurrence existe, mais il ne
borne pas le **débit d'arrivée**.

La table `rate_limit_tiers` existe déjà.

### 3.3 🟠 L'edge n'a ni timeout de lecture ni disjoncteur

**Vérifié** : `connectTimeout(5s)` et rien d'autre ; `0` occurrence de Resilience4j. Le commentaire
justifie l'absence par les flux SSE — c'est un vrai argument, mais il vaut *pour les routes SSE*, pas
pour la table entière. Un backend qui accepte la connexion puis se fige tient l'appel ouvert
indéfiniment ; sockets, descripteurs et pool de connexions fuient, et la façade se dégrade **pour
toutes les routes** parce qu'une seule est malade.

### 3.4 🟡 `PathResolver` : heuristique de poste de dev en production

**Vérifié**, identique dans deux services :

```java
while (root != null && !Files.exists(root.resolve("AGENTS.md"))) { root = root.getParent(); }
```

Localiser la racine des uploads en remontant jusqu'à un *fichier de gouvernance du dépôt*. Dans un
conteneur, `AGENTS.md` n'existe pas, la boucle s'épuise et le code retombe silencieusement sur le
répertoire courant du processus. Le placement des médias dépend alors du CWD au démarrage —
différent en Docker, en Kubernetes, sous un autre entrypoint.

---

## 4. P2 — dette révélée ou créée

### 4.1 🟠 Trois classes décident de la facturation, zéro test unitaire

**Mesuré** :

| Classe | Production | Test unitaire | Couverture réelle |
|:---|---:|---:|:---|
| `RunSagaService` | 612 lignes | **0** | 1 IT Testcontainers |
| `CreditLedgerService` | 549 lignes | **0** | 1 IT Testcontainers |
| `StudioRunService` | 365 lignes | **0** | 1 IT Testcontainers |

Ce sont les classes qui décident **si un client payant est débité**. Elles ne sont pas non testées —
un IT les couvre — mais chaque cas limite exige une base de données, ce qui rend le test coûteux,
donc rare, donc absent. La cause est structurelle : elles injectent `JdbcTemplate` directement, il
n'y a rien à substituer.

À titre de comparaison, `JobListener` (442 lignes) a 439 lignes de test unitaire.

### 4.2 🟠 SQL dans la couche application — 26 classes, pas 17

**Mesuré** : 26 classes de `application/service` injectent `JdbcTemplate` et écrivent du SQL en
ligne. Les pires : `CreditLedgerService` (12 appels), `RunSagaService` (11), `StudioRunService` (10).

Cela inverse l'hexagone : la couche qui devrait exprimer *ce que fait le produit* est couplée à la
syntaxe Postgres. Cela passe la gouvernance parce que `assertServicePackageOnlyServices` vérifie les
**noms** de types, pas leur contenu — une lacune de règle, pas une violation.

Le bon motif existe déjà, appliqué exactement une fois :
`JdbcBlueprintRepositoryAdapter` derrière le port `BlueprintRepository`.

### 4.3 🟠 Duplication mesurée : ~3 000 lignes

| Classe | Copies | Lignes |
|:---|---:|---:|
| `SecurityConfig` | 5 | 685 |
| `AmqpConfiguration` | 4 | 435 |
| `OutboxRelay` | 4 | 347 |
| `ServiceTokenProvider` | 4 | 336 |
| `MessageDedupService` | 5 | 258 |
| `JobCommand` | 2 | 224 |
| `OutboxService` · `ContextService` · `DataSourceConfig` · `AmqpConstants` · `MediaFileStore` · `PathResolver` · `SessionJwtProperties` | 2–5 | 935 |

Le nombre de lignes est le petit problème. Le vrai est que la vague 1 a dû corriger la même règle de
sécurité dans **cinq** fichiers, et que le `ServiceTokenProvider` a dû être écrit **quatre** fois —
la quatrième copie (l'edge) a été oubliée et n'a été rattrapée que par l'E2E, après avoir cassé
toutes les clés d'API.

`JobCommand` est un cas à part : c'est un **contrat de transport entre deux contextes** avec deux
propriétaires. C'est ainsi qu'un producteur ajoute un champ que le consommateur ignore en silence.

### 4.4 🟡 Six règles de gouvernance n'imposent rien

**Vérifié** : `assertAdaptersImplementPorts`, `assertApplicationInputsAreRecords`,
`assertPersistencePackageHygiene`, `assertNoUnapprovedUtilsInProduction`, `assertNoBannedPatterns` et
une autre — **zéro appelant** hors de leur définition. Écrites, compilées, sans effet.

Le registre généré (`docs/_generated/GOVERNANCE.md`) les affiche désormais en `none`. Les câbler fera
probablement échouer des modules qui les violent aujourd'hui sans le savoir : c'est un chantier, pas
un raccord.

### 4.5 🟡 La frontière de confiance M2M est le secret partagé

Tout processus détenant `IDENTITY_JWT_SECRET` peut forger un token `SERVICE`. ADR-035 l'écrit et
place deux renforcements en file : credentials par service (le moins cher), puis mTLS. À traiter
avant le premier déploiement multi-tenant réel, pas avant le lancement.

### 4.6 🟡 Aucun chiffrement au repos

**Vérifié** : `0` occurrence de `pgcrypto` dans l'initdb. Messages de chat, entrées de run et configs
de blueprint — qui contiennent des `secrets` selon `RunScope` — sont en clair sur disque.

### 4.7 🟡 18 avis npm restants

11 modérés, 7 élevés, plus aucun critique. Tous tracent vers `expo@57` ou un « correctif »
`react-native@0.72` **plus ancien** que l'installé. C'est de l'outillage de build (Metro, PostCSS,
CLI Expo), absent du bundle embarqué. Les purger = migration Expo SDK 53 → 54+.

---

## 4bis. Trouvailles de la revue du 2026-08-10

Cette passe a examiné trois zones que les analyses précédentes n'avaient pas ouvertes. Deux
constats nouveaux, et une confirmation.

### 4bis.1 🟠 Les threads virtuels ne sont pas activés là où ils comptent le plus

**Vérifié** : `spring.threads.virtual.enabled: true` est présent dans **6 services sur 8**. Manquent
`conversation-service` et `automation-service`, et aucun profil ne compense.

AGENTS.md §4 l'impose sans réserve : *« Spring MVC + virtual threads (Loom) […] Enable
`spring.threads.virtual.enabled=true` »*. L'ironie est que le service manquant est **l'ingress
interactif** — celui qui tient les flux SSE du chat, exactement la charge pour laquelle Loom existe.
Avec des threads plateforme et le plafond Tomcat par défaut (200), 200 flux SSE simultanés épuisent
le pool ; chaque appel bloquant (base, annuaire d'identité, Ollama) immobilise un worker.

Nuance à ne pas confondre : `SecurityExecutorConfiguration` y utilise bien
`newVirtualThreadPerTaskExecutor()`, mais c'est l'exécuteur de propagation du `SecurityContext`, pas
le pool de traitement des requêtes. Le premier ne compense pas l'absence du second.

### 4bis.2 🟠 Trois exigences d'AGENTS.md ne sont vérifiées par rien

Le vrai enseignement de la trouvaille précédente n'est pas le réglage manquant — c'est qu'**aucune
règle ne pouvait le voir**. En sondant, le même trou apparaît ailleurs :

| Exigence AGENTS.md | Respectée ? | Règle qui la vérifie |
|:---|:---|:---|
| `spring.threads.virtual.enabled=true` (§4) | 6/8 | **aucune** |
| `.tsx` ≤ 250 lignes (§8) | 1 violation : `icon.tsx`, **841 lignes** | **aucune** |
| Dates via `date-fns` uniquement [ERR-108] | 17 usages de `new Date(` à qualifier | **aucune** |

C'est le miroir des six règles orphelines de §4.4 : là, des règles écrites que personne n'appelle ;
ici, des exigences écrites que rien ne traduit en règle. Les deux produisent le même résultat — un
contrat qui a l'air tenu et ne l'est pas.

Sur `.tsx` : trois fichiers dépassent aussi le seuil en `.ts` — `translations.ts` (1 442),
`templates.ts` (1 071), `translations.types.ts` (667). Ce sont des **registres**, pas des
composants ; la règle telle qu'écrite ne leur convient pas. Il faut trancher explicitement — soit
une dérogation écrite dans AGENTS.md §8, soit un découpage — plutôt que laisser une règle
documentée silencieusement violée.

### 4bis.3 🟡 Le worker Python n'a aucune barrière de qualité

**Vérifié** : 1 490 lignes sur 7 fichiers, **1 fichier de test**, aucun `pyproject.toml`, donc ni
lint ni typage déclarés. Le `pom.xml` l'exclut explicitement des gates (« EXCLUDE […] per mandate »)
et ne référence sa couverture que pour Sonar.

C'est le composant qui manipule ffmpeg et écrit sur le disque — la seule primitive d'écriture du
produit hors base. Il mérite au minimum `ruff` + `pytest` dans `orazaka test`.

### 4bis.4 ✅ Confirmation : aucune dette de TODO

Zéro marqueur `TODO`/`FIXME` réel dans le code de production. Les cinq occurrences trouvées sont
dans `templates.ts` — des placeholders **intentionnels** du code que le CLI génère pour une nouvelle
feature — et une règle de validation qui interdit justement les placeholders dans les réponses.


---

## 5. Tâches exécutables

Chaque tâche est autonome, se termine verte, et constitue un point de reprise valide.
Elles sont formulées pour être confiées telles quelles.

### P0

- [x] **T1 ✅ — Fermer `knowledge-service`.** Ajouter `spring-boot-starter-security` + un `SecurityConfig`
      copié de billing (`/internal/v1/**` → `hasAuthority("SERVICE")`), équiper `HttpKnowledgeAdapter`
      (dans `orazaka-core`) d'un `ServiceTokenProvider`. **+ étendre la règle** :
      `assertInternalSurfaceRequiresServiceAuthority` doit aussi échouer si un service expose un
      contrôleur `/internal/**` sans `SecurityConfig` — sinon le prochain service ouvert passera
      encore. Vérifier par mutation. *(~1/2 journée)*

*(T2 a été déplacée en P1 — voir §2.1.)*

### P1

- [x] **T2a ✅ — Séparer fixtures et données de référence dans `infra/initdb/`.** Les 7 tables de
      fixtures sortent vers un fichier `9x-dev-fixtures.sql` appliqué sur profil dev uniquement ; les
      données de référence restent dans les fichiers de contexte. Bénéfice immédiat : un
      environnement propre devient possible. Prérequis naturel de T2. *(~1/2 journée)*

- [ ] **T2 — Baseline Flyway par contexte** — *à faire la semaine précédant le premier déploiement,
      pas avant.* `V1__<contexte>_baseline.sql` généré depuis le schéma d'alors, données de référence
      en callback `afterMigrate` (rejouables), fixtures exclues par profil. `clean-disabled: true`,
      `validate-on-migrate: true`. **Étendre `SqlBoundaryRules` [SEAM-001] à `db/migration/**`**,
      sinon la garantie « aucune FK inter-contexte » cesse silencieusement d'être appliquée le jour
      où le SQL déménage. *(~2 journées, à faire seule)*

- [ ] **T3 — `RateLimitFilter` sur identity, billing et studio.** Lire les seuils depuis
      `rate_limit_tiers`. Commencer par `identity`, dont l'absence de limite sur
      `/auth/login` et `/auth/forgot` est la plus exposée. Un IT par service asserte le 429.

- [ ] **T4 — Edge : timeout de lecture + disjoncteur.** Timeout par route, avec une dérogation
      **explicite** pour les routes SSE (le commentaire actuel a raison pour elles, tort pour le
      reste). Resilience4j en échec rapide 503 plutôt qu'en file d'attente contre un backend mort.
      Plafond de concurrence par route.

- [ ] **T5 — Supprimer le walk-up de `PathResolver`.** `orazaka.uploads.directory` requis, absolu,
      **sans repli** : échouer au démarrage si absent. Supprimer la boucle dans les deux copies.

- [ ] **T6 — Sauvegarde Postgres avec PITR + exercice de restauration.** La restauration doit être
      **effectuée**, avec RTO/RPO écrits. Files quorum RabbitMQ dans la foulée (`orazaka.jobs` porte
      du travail payé).

### P2

- [ ] **T7 — Extraire les ports de persistance, en commençant par le chemin monétaire.**
      `LedgerRepository` et `RunRepository` en ports `domain/port`, adaptateurs
      `Jdbc*RepositoryAdapter` en `infrastructure/adapter/persistence`. Le gain immédiat n'est pas
      l'esthétique : c'est de rendre `RunSagaService` et `CreditLedgerService` **testables sans base**,
      ce qui débloque T8. Ajouter la règle `assertNoPersistenceApiInApplicationLayer` — sans elle la
      dérive revient en deux features.

- [ ] **T8 — Tests unitaires sur les trois classes de facturation.** Rendu possible par T7. Viser les
      cas limites qu'un IT rend trop coûteux : retry épuisé, hold déjà réglé, message rejoué,
      fan-out au plafond.

- [ ] **T9 — `orazaka-service-kit` (Tier-2).** Absorbe `SecurityConfig`, `AmqpConfiguration`,
      `OutboxRelay`, `MessageDedupService`, `DataSourceConfig`, `MediaFileStore` **et les quatre
      `ServiceTokenProvider`**. ~3 000 lignes supprimées, et surtout *un seul endroit* où corriger une
      règle de sécurité. `JobCommand` va en Tier-1 (`orazaka-jobs-api`), **pas** dans le kit : c'est un
      contrat de transport, pas de la plomberie.

- [ ] **T10 — Découper `RunSagaService`.** En cinq : transitions d'état · résolution de scope ·
      expansion fan-out · dépôt · (garder `CreditReservationService` et `OutboxService`). ≤200 lignes
      chacun. **Avant le prochain Studio**, pas après.

- [ ] **T11 — Câbler ou supprimer les six règles orphelines.** Une par une : câbler, constater les
      échecs, décider si le module est fautif ou la règle obsolète. Une règle qu'on ne peut pas
      appliquer doit être supprimée, pas conservée pour l'apparence.

- [x] **T16 ✅ — Activer les threads virtuels sur `conversation-service` et `automation-service`**, et
      **ajouter la règle qui le vérifie** : un service exposant un serveur web doit déclarer
      `spring.threads.virtual.enabled: true`. Sans la règle, le prochain service naîtra sans.
      *(~1 h)*

- [x] **T17 ✅ — Trancher la règle des 250 lignes `.tsx`.** Soit une dérogation explicite dans
      AGENTS.md §8 pour les registres générés (`icon.tsx`, `translations.ts`), soit un découpage.
      Puis **écrire la règle** — aujourd'hui le seuil n'est vérifié par rien. Laisser une règle
      documentée silencieusement violée est pire que les deux options. *(~2 h)*

- [x] **T18 ✅ — Barrière de qualité sur le worker Python.** `pyproject.toml` avec `ruff` + `pytest`,
      branchés dans `orazaka test`. 1 490 lignes, 1 test, et c'est la seule primitive d'écriture
      disque du produit. *(~3 h)*

### P3

- [ ] **T12 — Chiffrement au repos.** Volume + `pgcrypto` sur les colonnes portant des `secrets`.
      Ajouter le test qui asserte que `RunScope.toString()` les exclut — le design l'impose, rien ne
      le vérifie.
- [ ] **T13 — Credentials M2M par service**, puis mTLS quand une mesh existera (ADR-035).
- [ ] **T14 — Migration Expo SDK 54+** pour solder les 18 avis npm.
- [ ] **T15 — Calibrer le pricebook sur des runs mesurés.** ADR-033 dit que les estimations sont des
      valeurs de remplacement — elles le sont toujours.

---

## 6. Ce que cette analyse n'a pas pu établir

- **Aucun profilage runtime.** `maximum-pool-size: 10` face à des threads virtuels est le
  désaccordage classique : des milliers de threads virtuels se disputant dix connexions sérialisent
  le système en silence. À mesurer avant de supposer.
- **Aucun test de pénétration.** Les failles de la vague 1 ont été trouvées en lisant. Il faut
  supposer qu'il en reste et budgéter une revue externe.
- **Le worker média Python et le client mobile** n'ont été survolés que superficiellement ici.
- **La charge réelle du fan-out Studio** contre le budget MLX n'a jamais été mesurée.

---

## Documents liés

- [Contrat de gouvernance](../AGENTS.md) · ADR-035 — authentification inter-services
- [Audit de production](PRODUCTION_READINESS_AUDIT.md) — *contient trois erreurs factuelles corrigées en §2.1, §2.3 et §2.5 ; relire chaque prescription contre le code*
- [Registre de gouvernance généré](_generated/GOVERNANCE.md) · [Référence d'API générée](_generated/API_REFERENCE.md)
