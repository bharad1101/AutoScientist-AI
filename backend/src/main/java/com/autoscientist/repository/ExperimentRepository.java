package com.autoscientist.repository;

import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.Experiment;
import com.autoscientist.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExperimentRepository extends JpaRepository<Experiment, Long> {
    List<Experiment> findByDatasetOrderByCreatedAtDesc(Dataset dataset);
    @EntityGraph(attributePaths = {"versions"})
    List<Experiment> findByUserOrderByCreatedAtDesc(User user);
    @EntityGraph(attributePaths = {"versions"})
    List<Experiment> findByDatasetAndUserOrderByCreatedAtDesc(Dataset dataset, User user);

    /**
     * Latest experiment for a dataset + target + problem type so repeated training
     * creates a new *version* of the same experiment instead of a new experiment row.
     */
    Experiment findFirstByDatasetAndUserAndTargetColumnAndProblemTypeOrderByCreatedAtDesc(
            Dataset dataset, User user, String targetColumn, String problemType);

    @Query("select count(e) from Experiment e where e.user = :owner")
    long countByOwner(@Param("owner") User owner);
}
