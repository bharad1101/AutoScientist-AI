# database/migrations

Versioned schema migrations live with the backend, on the classpath, so they
ship inside the packaged JAR and cannot drift from the code that depends on
them:

```
backend/src/main/resources/db/migration/
  V1__baseline.sql            full current schema (fresh installs)
  V2__provenance_delta.sql    additive, guarded delta for pre-Flyway databases
```

Flyway runs them automatically when the backend starts with the `mysql`
profile (`spring.flyway.enabled=true`, `baseline-on-migrate=true`,
`baseline-version=1`). Databases that predate Flyway are baselined at V1 and
receive only V2; nothing is dropped or rewritten.

`database/schema.sql` remains the human-readable reference schema (including
MySQL-specific decorations such as `ENGINE=InnoDB` and charset clauses) and is
kept in sync with the JPA entities — enforced by
`SchemaReferenceSyncTest`.

See `docs/DEPLOYMENT.md` §3 for initialization, migration and recovery steps.
