package com.autoscientist.repository;

import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DatasetRepository extends JpaRepository<Dataset, Long> {
    @EntityGraph(attributePaths = {"columns", "qualityReport"})
    List<Dataset> findByOwnerOrderByCreatedAtDesc(User owner);
    @EntityGraph(attributePaths = {"columns", "qualityReport"})
    Optional<Dataset> findByIdAndOwner(Long id, User owner);
    boolean existsByIdAndOwner(Long id, User owner);
}
