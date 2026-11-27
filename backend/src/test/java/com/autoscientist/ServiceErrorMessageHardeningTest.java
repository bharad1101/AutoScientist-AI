package com.autoscientist;

import com.autoscientist.analysis.profiling.DatasetProfiler;
import com.autoscientist.config.ResourceBudgetProperties;
import com.autoscientist.exception.BadRequestException;
import com.autoscientist.service.ImportPreviewService;
import com.autoscientist.util.ExcelImportService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceErrorMessageHardeningTest {

    @Test
    void previewDoesNotReturnInternalExceptionDetailToTheClient() throws Exception {
        ImportPreviewService service = new ImportPreviewService(
                org.mockito.Mockito.mock(ExcelImportService.class),
                org.mockito.Mockito.mock(DatasetProfiler.class),
                new ResourceBudgetProperties());
        MockMultipartFile file = new MockMultipartFile(
                "file", "data.csv", "text/csv", new byte[0]) {
            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public byte[] getBytes() throws IOException {
                throw new IOException("secret=/tmp/internal/key at com.example.Hidden.load(Hidden.java:1)");
            }
        };

        assertThatThrownBy(() -> service.preview(file, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Could not read the file.")
                .satisfies(error -> assertThat(error.getMessage())
                        .doesNotContain("/tmp/internal/key", "Hidden.java", "com.example"));
    }
}
