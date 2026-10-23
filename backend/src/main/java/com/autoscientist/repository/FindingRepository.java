package com.autoscientist.repository;

import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.Finding;
import com.autoscientist.entity.ResearchQuestion;
import com.autoscientist.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FindingRepository extends JpaRepository<Finding, Long> {
    @EntityGraph(attributePaths = {"evidenceNodes", "dataset", "researchQuestion", "hypothesis", "statisticalTest"})
    List<Finding> findByDatasetOrderByCreatedAtDesc(Dataset dataset);
    List<Finding> findByResearchQuestion(ResearchQuestion researchQuestion);

    @Query("select count(f) from Finding f where f.dataset.owner = :owner")
    long countByOwner(@Param("owner") User owner);

    @Query("select f from Finding f where f.dataset.owner = :owner order by f.createdAt desc")
    List<Finding> findRecentByOwner(@Param("owner") User owner, org.springframework.data.domain.Pageable pageable);

    /** Complete owner-scoped history (used for truthful activity accounting). */
    @Query("select f from Finding f where f.dataset.owner = :owner order by f.createdAt desc")
    List<Finding> findAllByOwner(@Param("owner") User owner);
}
