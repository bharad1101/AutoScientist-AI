package com.autoscientist.repository;

import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.Report;
import com.autoscientist.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    @EntityGraph(attributePaths = {"dataset", "researchQuestion"})
    List<Report> findByUserOrderByCreatedAtDesc(User user);
    @EntityGraph(attributePaths = {"dataset", "researchQuestion"})
    List<Report> findByDatasetOrderByCreatedAtDesc(Dataset dataset);
}
