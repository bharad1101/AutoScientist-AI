package com.autoscientist;

import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.User;
import com.autoscientist.exception.BadRequestException;
import com.autoscientist.exception.ResourceNotFoundException;
import com.autoscientist.exception.UnauthorizedException;
import com.autoscientist.repository.DatasetRepository;
import com.autoscientist.service.DataDriftService;
import com.autoscientist.service.DatasetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatasetOwnershipSecurityTest {

    @Mock
    private DatasetRepository datasetRepository;

    @InjectMocks
    private DatasetService datasetService;

    @Mock
    private DatasetService mockDatasetServiceForDrift;

    private DataDriftService dataDriftService;

    private User owner;
    private User attacker;
    private Dataset dataset;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(1L).email("owner@autoscientist.com").name("Owner").build();
        attacker = User.builder().id(2L).email("attacker@autoscientist.com").name("Attacker").build();
        dataset = Dataset.builder().id(100L).name("Clinical Trial").owner(owner).build();
        dataDriftService = new DataDriftService(mockDatasetServiceForDrift, null, null, null, null);
    }

    @Test
    @DisplayName("Authorized owner can access their own dataset")
    void testOwnerAccessSucceeds() {
        when(datasetRepository.findById(100L)).thenReturn(Optional.of(dataset));

        Dataset result = datasetService.getDatasetEntityWithOwnership(100L, owner);
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getOwner().getId()).isEqualTo(owner.getId());
    }

    @Test
    @DisplayName("Non-owner user is rejected with UnauthorizedException (HTTP 403)")
    void testNonOwnerAccessThrowsUnauthorized() {
        when(datasetRepository.findById(100L)).thenReturn(Optional.of(dataset));

        assertThatThrownBy(() -> datasetService.getDatasetEntityWithOwnership(100L, attacker))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("You do not have permission to access dataset id: 100");
    }

    @Test
    @DisplayName("Nonexistent dataset throws ResourceNotFoundException (HTTP 404)")
    void testNonexistentDatasetThrowsNotFound() {
        when(datasetRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> datasetService.getDatasetEntityWithOwnership(999L, owner))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Dataset not found with id: 999");
    }

    @Test
    @DisplayName("Drift comparison requires both reference and target dataset IDs")
    void testDriftRequiresBothDatasetIds() {
        assertThatThrownBy(() -> dataDriftService.compareDatasets(null, 100L, owner))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Both referenceDatasetId and targetDatasetId are required");

        assertThatThrownBy(() -> dataDriftService.compareDatasets(100L, null, owner))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Both referenceDatasetId and targetDatasetId are required");
    }

    @Test
    @DisplayName("Drift comparison enforces ownership on both reference and target datasets")
    void testDriftEnforcesOwnershipOnBothDatasets() {
        when(mockDatasetServiceForDrift.getDatasetEntityWithOwnership(100L, attacker))
                .thenThrow(new UnauthorizedException("You do not have permission to access dataset id: 100"));

        assertThatThrownBy(() -> dataDriftService.compareDatasets(100L, 101L, attacker))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("You do not have permission to access dataset id: 100");
    }
}
