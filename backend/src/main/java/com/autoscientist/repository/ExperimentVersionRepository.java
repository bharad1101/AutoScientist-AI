package com.autoscientist.repository;

import com.autoscientist.entity.Experiment;
import com.autoscientist.entity.ExperimentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExperimentVersionRepository extends JpaRepository<ExperimentVersion, Long> {
    List<ExperimentVersion> findByExperimentOrderByVersionNumberDesc(Experiment experiment);

    @Query("select count(v) from ExperimentVersion v where v.experiment.user = :owner")
    long countByOwner(@Param("owner") com.autoscientist.entity.User owner);
}
