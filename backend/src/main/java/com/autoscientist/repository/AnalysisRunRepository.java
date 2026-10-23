package com.autoscientist.repository;

import com.autoscientist.entity.AnalysisRun;
import com.autoscientist.entity.Dataset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisRunRepository extends JpaRepository<AnalysisRun, Long> {
    Optional<AnalysisRun> findByDatasetIdAndOperationAndParamsHash(Long datasetId, String operation, String paramsHash);
    List<AnalysisRun> findByDatasetIdOrderByUpdatedAtDesc(Long datasetId);
    List<AnalysisRun> findByDatasetAndOperationInOrderByUpdatedAtDesc(Dataset dataset, List<String> operations);
    boolean existsByDatasetIdAndOperation(Long datasetId, String operation);
    void deleteByDatasetId(Long datasetId);
}
