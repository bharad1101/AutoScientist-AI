package com.autoscientist;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Boots the full application context against H2 in MySQL-compatibility mode
 * with Flyway enabled and Hibernate set to {@code ddl-auto=validate}.
 *
 * This proves, on every {@code ./mvnw test} run, that:
 *   1. the versioned baseline migration (V1__baseline.sql) applies cleanly, and
 *   2. the migrated schema satisfies every JPA entity (Hibernate validation),
 *
 * i.e. the reference schema, the migrations and the entities cannot silently
 * drift apart. Flyway's target is capped at 1 because V2/V3 use MySQL-only
 * conditional DDL (information_schema + PREPARE); they are exercised against a
 * real MySQL instance — see docs/DEPLOYMENT.md and
 * docs/IMPLEMENTATION_PROGRESS.md for that validation record. V4 is portable
 * but is also applied manually to MySQL; the H2 INIT shim creates its table so
 * the baseline validation can still cover the password-reset entity mapping.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:mig_baseline;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;INIT=RUNSCRIPT FROM 'classpath:db/test/password_reset_tokens.sql'",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=true",
        "spring.flyway.baseline-version=0",
        "spring.flyway.target=1",
        "app.upload.dir=./target/test-uploads-migration",
        "app.jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
        "app.jwt.expiration-ms=3600000"
})
class MysqlBaselineMigrationTest {

    @Test
    void contextStartsAndHibernateValidatesMigratedSchema() {
        // Context load IS the assertion: Flyway migrated V1 and Hibernate
        // schema validation (ddl-auto=validate) rejected nothing.
    }
}
