package com.autoscientist.repository;

import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.ResearchQuestion;
import com.autoscientist.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResearchQuestionRepository extends JpaRepository<ResearchQuestion, Long> {
    @EntityGraph(attributePaths = {"hypotheses"})
    List<ResearchQuestion> findByDatasetOrderByCreatedAtDesc(Dataset dataset);
    List<ResearchQuestion> findByUserOrderByCreatedAtDesc(User user);
    List<ResearchQuestion> findByDatasetAndUserOrderByCreatedAtDesc(Dataset dataset, User user);
}
