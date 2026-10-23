package com.autoscientist.repository;

import com.autoscientist.entity.Association;
import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssociationRepository extends JpaRepository<Association, Long> {
    List<Association> findByDatasetOrderByRankScoreDesc(Dataset dataset);
    List<Association> findByDatasetAndIsSignificantTrueOrderByRankScoreDesc(Dataset dataset);
    void deleteByDataset(Dataset dataset);

    @Query("select count(a) from Association a where a.dataset.owner = :owner")
    long countByOwner(@Param("owner") User owner);
}
