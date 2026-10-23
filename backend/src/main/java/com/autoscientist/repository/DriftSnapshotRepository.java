package com.autoscientist.repository;

import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.DriftSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriftSnapshotRepository extends JpaRepository<DriftSnapshot, Long> {
    Optional<DriftSnapshot> findFirstByDatasetOrderByCreatedAtDesc(Dataset dataset);
    List<DriftSnapshot> findByDatasetOrderByCreatedAtDesc(Dataset dataset);
}
