# Dependency Audit

Date: 2026-10-30. Command: `cd frontend && npm audit --json`.

## Frontend result

The initial audit reported **7 vulnerabilities: 2 critical, 1 high, 4 moderate**. Updated vulnerable packages and checked compatibility: Vite 8.3.5, Vitest 4.1.12, React Router 7.18.5, and `@vitejs/plugin-react` 6.1.2. React Router remains a production dependency. Frontend tests pass (126/126), and the post-upgrade audit reports **0 vulnerabilities**.

| Package group | Severity | Exposure | Remediation indicated by audit |
|---|---|---|---|
| `vitest`, `tinypool`, `@vitest/mocker` | Initial audit: critical/moderate | Build/test tooling | Upgraded to Vitest 4.1.12; cleared in current audit |
| `vite`, transitive `esbuild` | Initial audit: high/moderate | Build/dev tooling | Upgraded to Vite 8.3.5; cleared in current audit |
| `react-router`, `react-router-dom` | Initial audit: moderate | Browser runtime | Upgraded to React Router 7.18.5; cleared in current audit |

Vite 8 uses Rolldown/Oxc tooling; `@vitejs/plugin-react` 6 was selected because its peer range includes Vite 8. Vitest 4 requires Node 20+ and React Router v7 retains declarative routing APIs used by this app. The frontend suite verifies current application behavior. The production build still requires the actual API URL.

## Backend

No Maven vulnerability scan was run. Backend clean test was attempted; see test results. Add a dependency scan in the provider pipeline and review its report before production.
