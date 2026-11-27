package com.autoscientist.repository;

import com.autoscientist.entity.JobInfo;
import com.autoscientist.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobInfoRepository extends JpaRepository<JobInfo, UUID> {
    List<JobInfo> findByOwnerOrderByCreatedAtDesc(User owner);

    List<JobInfo> findByOwnerAndStatusInOrderByCreatedAtDesc(User owner, List<JobInfo.JobStatus> statuses);

    List<JobInfo> findByOwnerAndTypeInOrderByCreatedAtDesc(User owner, List<JobInfo.JobType> types);

    List<JobInfo> findByOwnerAndStatusInAndTypeInOrderByCreatedAtDesc(
            User owner, List<JobInfo.JobStatus> statuses, List<JobInfo.JobType> types);

    Optional<JobInfo> findByIdAndOwner(UUID id, User owner);

    List<JobInfo> findByDatasetIdAndOwner(Long datasetId, User owner);
}
