package com.autoscientist.repository;

import com.autoscientist.entity.ExperimentVersion;
import com.autoscientist.entity.ModelRun;
import com.autoscientist.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModelRunRepository extends JpaRepository<ModelRun, Long> {
    List<ModelRun> findByExperimentVersion(ExperimentVersion experimentVersion);

    /** Real count of persisted model runs owned by a user (no fabricated values). */
    @Query("select count(m) from ModelRun m where m.experimentVersion.experiment.user = :owner")
    long countByOwner(@Param("owner") User owner);

    @Query("select m from ModelRun m where m.experimentVersion.experiment.user = :owner and m.isBestModel = true order by m.createdAt desc")
    List<ModelRun> findBestModelsByOwner(@Param("owner") User owner, org.springframework.data.domain.Pageable pageable);
}
