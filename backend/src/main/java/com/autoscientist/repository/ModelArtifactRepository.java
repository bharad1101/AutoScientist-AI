package com.autoscientist.repository;

import com.autoscientist.entity.ExperimentVersion;
import com.autoscientist.entity.ModelArtifact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModelArtifactRepository extends JpaRepository<ModelArtifact, Long> {
    Optional<ModelArtifact> findFirstByExperimentVersionAndModelNameOrderByIdDesc(
            ExperimentVersion experimentVersion, String modelName);

    List<ModelArtifact> findByExperimentVersion(ExperimentVersion experimentVersion);
}
