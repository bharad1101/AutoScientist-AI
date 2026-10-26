package com.autoscientist;

import com.autoscientist.config.DeploymentProfileGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeploymentProfileGuardTest {

    @Mock
    private Environment environment;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData metadata;

    @InjectMocks
    private DeploymentProfileGuard guard;

    @Test
    void devProfileIsAcceptedLocally() {
        when(environment.acceptsProfiles(Profiles.of("dev"))).thenReturn(true);
        when(environment.getProperty("RENDER", "false")).thenReturn("false");
        when(environment.getProperty("RENDER_SERVICE_ID", "")).thenReturn("");
        when(environment.getProperty("RENDER_EXTERNAL_URL", "")).thenReturn("");

        assertThatCode(() -> guard.run(null)).doesNotThrowAnyException();
        verifyNoInteractions(dataSource);
    }

    @Test
    void devProfileIsRejectedOnRender() {
        when(environment.acceptsProfiles(Profiles.of("dev"))).thenReturn(true);
        when(environment.getProperty("RENDER", "false")).thenReturn("true");

        assertThatThrownBy(() -> guard.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dev")
                .hasMessageContaining("SPRING_PROFILES_ACTIVE=mysql");
        verifyNoInteractions(dataSource);
    }

    @Test
    void devProfileIsRejectedWhenRenderServiceIdIsPresent() {
        when(environment.acceptsProfiles(Profiles.of("dev"))).thenReturn(true);
        when(environment.getProperty("RENDER", "false")).thenReturn("false");
        when(environment.getProperty("RENDER_SERVICE_ID", "")).thenReturn("srv-123");

        assertThatThrownBy(() -> guard.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dev");
        verifyNoInteractions(dataSource);
    }

    @Test
    void nonDevProfileIsRejectedWhenDatasourceIsEmbedded() throws Exception {
        when(environment.acceptsProfiles(Profiles.of("dev"))).thenReturn(false);
        when(environment.acceptsProfiles(Profiles.of("test"))).thenReturn(false);
        when(environment.getActiveProfiles()).thenReturn(new String[] {"prod"});
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn("jdbc:h2:file:./data/h2/autoscientist_dev");

        assertThatThrownBy(() -> guard.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("embedded database")
                .hasMessageContaining("mysql");
    }

    @Test
    void testProfileIsAllowedWithEmbeddedDatabase() {
        when(environment.acceptsProfiles(Profiles.of("dev"))).thenReturn(false);
        when(environment.acceptsProfiles(Profiles.of("test"))).thenReturn(true);

        assertThatCode(() -> guard.run(null)).doesNotThrowAnyException();
        verifyNoInteractions(dataSource);
    }

    @Test
    void nonDevProfileIsAcceptedWithMysqlDatasource() throws Exception {
        when(environment.acceptsProfiles(Profiles.of("dev"))).thenReturn(false);
        when(environment.acceptsProfiles(Profiles.of("test"))).thenReturn(false);
        when(environment.getActiveProfiles()).thenReturn(new String[] {"mysql"});
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn("jdbc:mysql://db.example.com:3306/autoscientist");

        assertThatCode(() -> guard.run(null)).doesNotThrowAnyException();
    }
}
