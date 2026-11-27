package com.autoscientist;

import com.autoscientist.exception.BadRequestException;
import com.autoscientist.exception.ResourceLimitExceededException;
import com.autoscientist.util.CsvTable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvTableTest {

    @Test
    void parsesOnceIntoColumnarValuesAndCountsDuplicates() {
        String csv = "id,group,value\n"
                + "1,A,10\n"
                + "2,B,20\n"
                + "1,A,10\n";

        CsvTable table = CsvTable.read(input(csv));

        assertEquals(List.of("id", "group", "value"), table.getHeaders());
        assertEquals(3, table.getRowCount());
        assertEquals(1, table.getDuplicateRowCount());
        assertEquals(List.of("1", "2", "1"), table.getColumn(0));
        assertEquals(List.of("A", "B", "A"), table.getColumn(1));
        assertEquals(List.of("10", "20", "10"), table.getColumn(2));
    }

    @Test
    void rejectsInconsistentRowsLikeTheValidator() {
        String csv = "a,b\n1,2\n3\n";

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> CsvTable.read(input(csv)));
        assertTrue(error.getMessage().contains("Inconsistent row length at row 2"));
    }

    @Test
    void rejectsHeaderOnlyFiles() {
        assertThrows(BadRequestException.class, () -> CsvTable.read(input("a,b\n")));
    }

    @Test
    void parsesQuotedFieldsContainingEmbeddedNewlines() {
        String csv = "name,note\n"
                + "A,\"hello\nworld\"\n"
                + "B,ok\n";

        CsvTable table = CsvTable.read(input(csv));

        assertEquals(2, table.getRowCount());
        assertEquals("hello\nworld", table.getColumn(1).get(0));
        assertEquals("ok", table.getColumn(1).get(1));
    }

    @Test
    void enforcesConfiguredRowLimit() {
        String csv = "a\n1\n2\n3\n";

        ResourceLimitExceededException error = assertThrows(ResourceLimitExceededException.class,
                () -> CsvTable.read(input(csv), 2, 100));
        assertTrue(error.getMessage().contains("row limit"));
    }

    @Test
    void enforcesConfiguredColumnLimit() {
        String csv = "a,b\n1,2\n";

        ResourceLimitExceededException error = assertThrows(ResourceLimitExceededException.class,
                () -> CsvTable.read(input(csv), 100, 1));
        assertTrue(error.getMessage().contains("column limit"));
    }

    @Test
    void enforcesConfiguredTotalCellLimit() {
        String csv = "a,b\n1,2\n3,4\n";

        ResourceLimitExceededException error = assertThrows(ResourceLimitExceededException.class,
                () -> CsvTable.read(input(csv), 100, 100, 3));
        assertTrue(error.getMessage().contains("total cell limit"));
    }

    private ByteArrayInputStream input(String csv) {
        return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
    }
}
