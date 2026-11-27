# Managed MySQL Setup

Status: **configuration requirements documented; provider account, databases, credentials, and TLS state not verified.**

Use managed MySQL 8.x (Aiven or another compatible provider). Never share
preview and production credentials or databases. Never use local H2 as a
production database. No database was created, reset, or modified by this work.

## 1. Create two databases

| Environment | Database user | Access |
|---|---|---|
| Preview | dedicated preview user | preview database only |
| Production | dedicated production user | production database only |

Grant only the schema privileges needed by the application (SELECT, INSERT,
UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES on the application
schema where required by Flyway). Do not grant global admin privileges to the
application user.

Enable TLS on the provider and require TLS in the JDBC URL using
`sslMode=VERIFY_IDENTITY` (preferred), `VERIFY_CA`, or `REQUIRED`. Add the
provider CA to the container trust store if a verifying mode is used. Never put
the URL containing credentials in Git; the application reads
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and
`SPRING_DATASOURCE_PASSWORD` from the provider environment.

## 2. Flyway authority

- Flyway migrations live in `backend/src/main/resources/db/migration/V1__baseline.sql`
  through `V7__job_ownership_and_parameters.sql`.
- `spring.flyway.enabled=true` in the `mysql` profile;
  `spring.flyway.locations=classpath:db/migration`;
  `baseline-on-migrate=true`, `baseline-version=1`,
  `validate-on-migrate=true`.
- Hibernate uses `ddl-auto: validate` and never changes the production schema.
- `database/schema.sql` is a human-readable reference. It is **not** applied
  automatically and must not be applied manually on top of a Flyway-managed DB.
- A failed Flyway migration fails deployment startup; do not bypass it by
  disabling Flyway or changing `ddl-auto`.
- Do not drop or truncate tables on a live environment.

## 3. First-time migration (operator)

1. Create an empty database and user on the provider.
2. Confirm the connection with TLS from a temporary client using the provider's
   documented connection string; never print the password in terminal output.
3. Set the environment variables in Render.
4. Deploy the backend. Flyway creates `flyway_schema_history` and applies V1–V7.
5. Request `/api/health/ready` and confirm `migrations: UP`.
6. Query `flyway_schema_history` and confirm 7 successful versions.

For a legacy pre-Flyway schema, take a backup first. Flyway baselines at V1 and
applies V2–V7. Review V2 and V7 carefully on a copy first: V2 assumes its tables
may need to be created, and V7 adds a foreign key that can fail if old
`owner_id` values are orphaned. Repair data on a copy, not in production, before
rerunning a migration. If a previous draft of V5–V7 was applied outside Flyway,
use `flyway repair` only after operator review.

## 4. Backup and restore

Provider backups are operator-owned and must be configured before go-live.

Backup before every migration or risky deployment:

```bash
mysqldump --single-transaction --routines --triggers --set-gtid-purged=OFF \
  -h <host> -P 3306 -u <user> -p --ssl-mode=VERIFY_IDENTITY \
  <database> > backup_<environment>_<timestamp>.sql
```

The command prompts for the password; the password is not stored in the file.
Store backups encrypted outside the repository and verify a restore on a
disposable database:

```bash
mysql -h <host> -P 3306 -u <user> -p --ssl-mode=VERIFY_IDENTITY \
  <restore_database> < backup_<environment>_<timestamp>.sql
```

Never restore a preview backup into production and vice versa. Restoring into a
database that already has Flyway history requires operator review to avoid
version conflicts.

## 5. Verification evidence

- Locally verified: Flyway V1–V7 are packaged in the JAR and covered by SQL
  compatibility tests; H2/MySQL-syntax tests pass.
- CI verified: not yet observed on GitHub.
- Provider configured: not verified.
- Actually deployed: not verified.
