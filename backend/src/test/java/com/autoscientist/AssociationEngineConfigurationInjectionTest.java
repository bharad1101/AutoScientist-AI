package com.autoscientist;

import com.autoscientist.analysis.association.AssociationEngine;
import com.autoscientist.analysis.quality.ColumnConsistencyValidator;
import com.autoscientist.analysis.quality.DataQualityEngine;
import com.autoscientist.config.ResourceBudgetProperties;
import com.autoscientist.entity.DatasetColumn;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:association_injection_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "resource-budget.max-columns=7"
})
@ActiveProfiles("test")
class AssociationEngineConfigurationInjectionTest {

    @Autowired
    private AssociationEngine associationEngine;

    @Autowired
    private ResourceBudgetProperties budgetProperties;

    @Autowired
    private DataQualityEngine dataQualityEngine;

    @Autowired
    private ColumnConsistencyValidator consistencyValidator;

    @Test
    void springInjectsConfiguredBudgetProperties() {
        assertEquals(7, budgetProperties.getMaxColumns());
        assertSame(budgetProperties, ReflectionTestUtils.getField(associationEngine, "budgetProperties"));
    }

    @Test
    void springInjectsConfiguredDependenciesIntoDataQualityEngine() {
        assertSame(consistencyValidator, ReflectionTestUtils.getField(dataQualityEngine, "consistencyValidator"));
    }

    @Test
    void configuredColumnBudgetIsEnforcedByTheSpringBean() throws Exception {
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> associationEngine.discoverAssociations(csv(8), null, columns(8)));
        assertTrue(rejected.getMessage().contains("maxColumns of 7"));

        assertDoesNotThrow(() -> associationEngine.discoverAssociations(csv(7), null, columns(7)));
    }

    private static List<DatasetColumn> columns(int count) {
        List<DatasetColumn> columns = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            columns.add(DatasetColumn.builder()
                    .columnIndex(i)
                    .columnName("c" + i)
                    .dataType("NUMERIC")
                    .uniqueCount(2)
                    .build());
        }
        return columns;
    }

    private static ByteArrayInputStream csv(int count) {
        StringBuilder header = new StringBuilder();
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                header.append(',');
                row.append(',');
            }
            header.append("c").append(i);
            row.append(i + 1);
        }
        String content = header + "\n" + row + "\n" + row + "\n";
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
