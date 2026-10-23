package com.autoscientist.repository;

import com.autoscientist.entity.DataQualityReport;
import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DataQualityReportRepository extends JpaRepository<DataQualityReport, Long> {
    Optional<DataQualityReport> findByDataset(Dataset dataset);

    @Query("select q from DataQualityReport q where q.dataset.owner = :owner")
    List<DataQualityReport> findByOwner(@Param("owner") User owner);
}
