# Database Readiness

## Repository configuration

The backend uses MySQL in the `mysql` profile with Flyway migrations V1–V4. The production Hibernate DDL fallback is now `validate`; Flyway owns production schema changes.

Fresh schemas run V1–V4. Pre-Flyway schemas baseline at version 1, then run V2–V4. Migrations must remain immutable once applied. The migration account needs the DDL permissions documented in `DEPLOYMENT_GUIDE.md`; use a separate least-privilege runtime account if the provider permits separate migration credentials.

## Evidence and remaining validation

The actual dependency tree resolves Flyway 11.7.2 under Spring Boot 3.5.16; the older release gate's Flyway 9.22.3 statement is stale. Redgate's current driver reference lists verified MySQL versions 5.7, 8.0, and 9.4 ([MySQL driver reference](https://documentation.red-gate.com/flyway/reference/database-driver-reference/mysql)). Prior notes reported tests on isolated MySQL 9.7.1, which is not listed as a verified version. No staging credentials were available in this pass. Do not run the integration test against production; it creates and drops test schemas.

To verify safely, provision an isolated MySQL 8.x staging instance, confirm `as_it_fresh` and `as_it_legacy` are unused, set `AS_TEST_MYSQL_URL`, `AS_TEST_MYSQL_USER`, and `AS_TEST_MYSQL_PASSWORD`, then run `cd backend && ./mvnw -Dtest=RealMysqlMigrationTest test`. Record server/Flyway versions and results. Take provider backups before first production migration and rehearse restore.

## Blockers

- Actual MySQL host, database, credentials, TLS policy, and backup configuration must be entered in the backend provider dashboard; restart required.
- Staging MySQL 8.x migration run is pending; no production schema change should be used as a test.
