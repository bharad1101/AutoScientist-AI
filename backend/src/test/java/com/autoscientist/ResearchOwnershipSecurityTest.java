package com.autoscientist;

import com.autoscientist.analysis.findings.EvidenceEngine;
import com.autoscientist.analysis.findings.QuestionAnalysisEngine;
import com.autoscientist.analysis.statistics.StatisticalTestEngine;
import com.autoscientist.entity.Hypothesis;
import com.autoscientist.entity.ResearchQuestion;
import com.autoscientist.entity.User;
import com.autoscientist.exception.UnauthorizedException;
import com.autoscientist.repository.FindingRepository;
import com.autoscientist.repository.HypothesisRepository;
import com.autoscientist.repository.ResearchQuestionRepository;
import com.autoscientist.repository.StatisticalTestRepository;
import com.autoscientist.service.AnalysisRunService;
import com.autoscientist.service.DatasetService;
import com.autoscientist.service.ResearchService;
import com.autoscientist.util.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResearchOwnershipSecurityTest {

    @Mock
    private ResearchQuestionRepository researchQuestionRepository;
    @Mock
    private HypothesisRepository hypothesisRepository;
    @Mock
    private StatisticalTestRepository statisticalTestRepository;
    @Mock
    private FindingRepository findingRepository;
    @Mock
    private DatasetService datasetService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private QuestionAnalysisEngine questionAnalysisEngine;
    @Mock
    private StatisticalTestEngine statisticalTestEngine;
    @Mock
    private EvidenceEngine evidenceEngine;
    @Mock
    private AnalysisRunService analysisRunService;

    @InjectMocks
    private ResearchService researchService;

    @Test
    void hypothesisFromAnotherResearchQuestionIsRejected() {
        User owner = User.builder().id(1L).email("owner@example.org").build();
        ResearchQuestion question = ResearchQuestion.builder().id(10L).user(owner).build();
        ResearchQuestion otherQuestion = ResearchQuestion.builder().id(11L).user(owner).build();
        Hypothesis foreignHypothesis = Hypothesis.builder()
                .id(20L)
                .researchQuestion(otherQuestion)
                .suggestedTest("PEARSON")
                .var1("height")
                .build();

        when(researchQuestionRepository.findById(10L)).thenReturn(Optional.of(question));
        when(hypothesisRepository.findById(20L)).thenReturn(Optional.of(foreignHypothesis));

        assertThatThrownBy(() -> researchService.executeHypothesisTest(10L, 20L, owner))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("hypothesis");

        verifyNoInteractions(statisticalTestRepository, findingRepository, statisticalTestEngine,
                evidenceEngine, analysisRunService);
    }

    @Test
    void hypothesisWithoutAResearchQuestionIsRejected() {
        User owner = User.builder().id(1L).email("owner@example.org").build();
        ResearchQuestion question = ResearchQuestion.builder().id(10L).user(owner).build();
        Hypothesis unlinkedHypothesis = Hypothesis.builder()
                .id(21L)
                .suggestedTest("PEARSON")
                .var1("height")
                .build();

        when(researchQuestionRepository.findById(10L)).thenReturn(Optional.of(question));
        when(hypothesisRepository.findById(21L)).thenReturn(Optional.of(unlinkedHypothesis));

        assertThatThrownBy(() -> researchService.executeHypothesisTest(10L, 21L, owner))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("hypothesis");

        verifyNoInteractions(statisticalTestRepository, findingRepository, statisticalTestEngine,
                evidenceEngine, analysisRunService);
    }
}
