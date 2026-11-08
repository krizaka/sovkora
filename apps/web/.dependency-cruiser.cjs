/** @type {import('dependency-cruiser').IConfiguration} */
module.exports = {
  forbidden: [
    {
      name: "no-circular",
      severity: "error", // 🔥 CRITICAL: Prevents compilation and execution loops
      comment:
        "Circular dependencies are strictly banned across Orazaka UI. They cause tight coupling, memory leaks, and break Next.js fast refresh cycles.",
      from: {},
      to: {
        circular: true,
      },
    },
    {
      name: "no-flat-feature-modules",
      severity: "error", // 🔥 STRUCTURE: every feature's internals live in typed sub-folders
      comment:
        "Feature code MUST live in a typed sub-folder (components/, hooks/, utils/, context/, constants/, api/) — never as a flat module in the feature root. Keeps every feature's internal structure uniform (the standard the other features already follow).",
      from: {},
      to: {
        path: "^src/features/[^/]+/[^/]+\\.(ts|tsx)$",
        pathNot: "^src/features/[^/]+/index\\.ts$",
      },
    },
    {
      name: "no-feature-cross-import",
      severity: "error", // 🔥 TOTAL ENFORCEMENT: Enforces strict module isolation
      comment:
        "Feature modules MUST NOT import from other feature modules directly. Cross-feature leaks violate encapsulation. Shared states, context, or hooks must be explicitly extracted to src/core/ or a shared kernel layer.",
      from: {
        path: "^src/features/([^/]+)/",
      },
      to: {
        path: "^src/features/([^/]+)/",
        pathNot: "^src/features/$1/",
      },
    },
    {
      name: "no-components-import-features",
      severity: "error", // 🔥 TOTAL ENFORCEMENT: Guarantees pure presentation layouts
      comment:
        "Shared UI layouts or generic design-system components must remain pure and domain-agnostic. They MUST NOT import from domain-specific feature modules.",
      from: {
        path: "^src/components/",
      },
      to: {
        path: "^src/features/",
      },
    },
    {
      name: "no-orphan-modules",
      severity: "error", // 🔥 ACTIVE PURGE: Kills dead code on every single compilation
      comment:
        "Dead code, unused files, or unlinked modules are forbidden in the production source tree. Wire files properly or delete them.",
      from: {
        orphan: true,
        pathNot: [
          "(^|/)\\.[^/]+", // Hidden dot files
          "\\.d\\.ts$", // TypeScript declaration files
          "(^|/)tsconfig\\.json$",
          "\\.test\\.(ts|tsx)$", // Test patterns
          "\\.spec\\.(ts|tsx)$", // Spec patterns
          "^src/__tests__/", // Exclude test helper files and mocks from orphan rule
          // Next.js Framework Entrypoint Invariants: Only exclude magic framework files
          "^src/app/.*\\.(ts|tsx)$",
          "^src/(middleware|instrumentation)\\.ts$",
        ],
      },
      to: {},
    },
    {
      name: "no-types-import-implementation",
      severity: "error",
      comment:
        "Type definition files must remain pure structural contracts. They are strictly forbidden from importing active component or feature implementations.",
      from: {
        path: "^src/types/",
      },
      to: {
        path: "^src/(features|components)/",
      },
    },
    {
      name: "no-direct-graphql-access",
      severity: "error",
      comment:
        "Features and components must route asynchronous data actions through the central API abstraction layer. Bypassing it to hit raw graphql schemas is forbidden.",
      from: {
        path: "^src/(features|components)/",
      },
      to: {
        path: "^src/core/graphql/",
      },
    },
    {
      name: "no-leaking-feature-constants",
      severity: "error",
      comment:
        "Transversal configurations and structural constants must live in a central directory. Do not trap domain-spanning settings inside a single feature scope.",
      from: {
        path: "^src/",
        pathNot: "^src/features/settings/",
      },
      to: {
        path: "^src/features/settings/constants/",
      },
    },
    /* 🛡️ ADVANCED ARCHITECTURAL SECURITY CONSTRAINTS 🛡️ */
    {
      name: "enforce-shared-isolation",
      severity: "error", // 🔥 Keeps the shared infrastructure layer decoupled
      comment:
        "The shared infrastructure layer (src/core) must be universally reusable. It is structurally banned from importing from domain-specific features.",
      from: {
        path: "^src/core/",
      },
      to: {
        path: "^src/features/",
      },
    },
    {
      name: "no-auth-config-in-routing",
      severity: "error", // 🔥 Enforces secure encapsulation of NextAuth options
      comment:
        "NextAuth configurations like authOptions must live securely inside src/core/auth/ or src/core/config/, NOT leaked directly inside the Next.js routing tree routes.",
      from: {
        path: "^src/",
        pathNot: "^src/app/api/auth/",
      },
      to: {
        path: "^src/app/api/auth/",
      },
    },
  ],
  options: {
    doNotFollow: {
      path: "node_modules",
    },
    tsPreCompilationDeps: true,
    tsConfig: {
      fileName: "tsconfig.json",
    },
    enhancedResolveOptions: {
      exportsFields: ["exports"],
      conditionNames: ["import", "require", "node", "default"],
    },
    reporterOptions: {
      dot: {
        collapsePattern: "node_modules/(@[^/]+/[^/]+|[^/]+)",
      },
    },
  },
};
