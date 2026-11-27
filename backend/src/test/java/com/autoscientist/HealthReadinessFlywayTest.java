package com.autoscientist;

import com.autoscientist.controller.HealthController;
import com.autoscientist.dto.common.ApiResponse;
import com.autoscientist.util.FileStorageService;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthReadinessFlywayTest {

    @Test
    @SuppressWarnings("unchecked")
    void readinessReportsDownWhenFlywayHasPendingMigrations() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);

        FileStorageService storage = mock(FileStorageService.class);
        when(storage.isHealthy()).thenReturn(true);

        Flyway flyway = mock(Flyway.class);
        MigrationInfoService info = mock(MigrationInfoService.class);
        MigrationInfo current = mock(MigrationInfo.class);
        when(flyway.info()).thenReturn(info);
        when(info.current()).thenReturn(current);
        when(current.getState()).thenReturn(MigrationState.SUCCESS);
        when(info.pending()).thenReturn(new MigrationInfo[]{mock(MigrationInfo.class)});

        ObjectProvider<Flyway> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(flyway);

        HealthController controller = new HealthController(dataSource, storage, provider);
        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.readiness();

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().get("status")).isEqualTo("DOWN");
        assertThat(response.getBody().getData().get("migrations")).isEqualTo("DOWN");
    }

    @Test
    @SuppressWarnings("unchecked")
    void readinessReportsUpWhenFlywayHasNoPendingMigrations() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);

        FileStorageService storage = mock(FileStorageService.class);
        when(storage.isHealthy()).thenReturn(true);

        Flyway flyway = mock(Flyway.class);
        MigrationInfoService info = mock(MigrationInfoService.class);
        MigrationInfo current = mock(MigrationInfo.class);
        when(flyway.info()).thenReturn(info);
        when(info.current()).thenReturn(current);
        when(current.getState()).thenReturn(MigrationState.SUCCESS);
        when(info.pending()).thenReturn(new MigrationInfo[0]);

        ObjectProvider<Flyway> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(flyway);

        HealthController controller = new HealthController(dataSource, storage, provider);
        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.readiness();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().get("migrations")).isEqualTo("UP");
    }
}
