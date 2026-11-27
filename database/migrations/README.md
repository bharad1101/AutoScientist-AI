# database/migrations

Versioned schema migrations live with the backend, on the classpath, so they
ship inside the packaged JAR and cannot drift from the code that depends on
them:

```
backend/src/main/resources/db/migration/
  V1__baseline.sql                       full baseline (fresh installs)
  V2__provenance_delta.sql               additive, guarded legacy delta
  V3__association_confidence_intervals.sql
  V4__password_reset_tokens.sql
  V5__jobs_and_model_artifacts.sql
  V6__model_artifact_longblob.sql
  V7__job_ownership_and_parameters.sql   guarded legacy jobs-table delta
```

Flyway runs them automatically when the backend starts with the `mysql`
profile (`spring.flyway.enabled=true`, `baseline-on-migrate=true`,
`baseline-version=1`). Databases that predate Flyway are baselined at V1 and
receive the additive V2-V7 deltas; nothing is dropped or rewritten. Fresh
databases run V1-V7. V2, V3, V6 and V7 use MySQL-only conditional DDL and are
validated against disposable MySQL; the H2 tests cap Flyway at V1 and apply
the portable V5 DDL separately. V5–V7 are new, previously untracked files in
this working tree, so correcting them here does not rewrite an already
published Flyway history; any local database that already applied an earlier
draft must be rebuilt or repaired with `flyway repair` before reuse.

`model_artifacts.experiment_version_id` has a NOT NULL foreign key to
`experiment_versions.id` with no cascade. `DatasetService.deleteDataset`
therefore deletes artifacts and model runs before experiment versions; jobs
carry `dataset_id` without a foreign key and are deleted only when their owner
matches the dataset owner.

`database/schema.sql` remains the human-readable reference schema (including
MySQL-specific decorations such as `ENGINE=InnoDB` and charset clauses) and is
kept in sync with the JPA entities — enforced by
`SchemaReferenceSyncTest`.

See `docs/DEPLOYMENT.md` §3 for initialization, migration and recovery steps.
