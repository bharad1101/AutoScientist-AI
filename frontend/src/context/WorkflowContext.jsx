import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { datasetService } from '../services/datasetService';
import { useAuth } from './AuthContext';

export const WORKFLOW_STAGES = [
  {
    key: 'upload',
    stepNumber: 1,
    name: 'Upload Dataset',
    shortName: 'Upload',
    getPath: (id) => `/datasets`,
    icon: 'bi-cloud-arrow-up',
    description: 'Ingest raw CSV dataset into AutoScientist AI',
  },
  {
    key: 'overview',
    stepNumber: 2,
    name: 'Dataset Overview',
    shortName: 'Overview',
    getPath: (id) => `/datasets/${id}/overview`,
    icon: 'bi-table',
    description: 'Inspect columns, data types, and descriptive statistics',
  },
  {
    key: 'quality',
    stepNumber: 3,
    name: 'Data Quality',
    shortName: 'Quality',
    getPath: (id) => `/datasets/${id}/quality`,
    icon: 'bi-shield-check',
    description: 'Automated 100-point data quality and integrity audit',
  },
  {
    key: 'eda',
    stepNumber: 4,
    name: 'Automated EDA',
    shortName: 'EDA',
    getPath: (id) => `/datasets/${id}/eda`,
    icon: 'bi-graph-up',
    description: 'Exploratory data analysis with distributions and correlations',
  },
  {
    key: 'associations',
    stepNumber: 5,
    name: 'Associations',
    shortName: 'Associations',
    getPath: (id) => `/datasets/${id}/associations`,
    icon: 'bi-diagram-3',
    description: 'Statistically significant bivariate feature associations',
  },
  {
    key: 'research',
    stepNumber: 6,
    name: 'Research Questions',
    shortName: 'Questions',
    getPath: (id) => `/datasets/${id}/research`,
    icon: 'bi-question-circle',
    description: 'Scientific question formulation and formalization',
  },
  {
    key: 'hypotheses',
    stepNumber: 7,
    name: 'Hypotheses',
    shortName: 'Hypotheses',
    getPath: (id) => `/datasets/${id}/hypotheses`,
    icon: 'bi-lightbulb',
    description: 'Formulate null and alternative testable hypotheses',
  },
  {
    key: 'statistics',
    stepNumber: 8,
    name: 'Statistical Testing',
    shortName: 'Statistics',
    getPath: (id) => `/datasets/${id}/statistics`,
    icon: 'bi-calculator',
    description: 'Rigorous hypothesis testing and effect size estimation',
  },
  {
    key: 'ml',
    stepNumber: 9,
    name: 'Machine Learning',
    shortName: 'ML',
    getPath: (id) => `/datasets/${id}/ml`,
    icon: 'bi-cpu',
    description: 'Automated machine learning model training & benchmarking',
  },
  {
    key: 'visualizations',
    stepNumber: 10,
    name: 'Visualizations',
    shortName: 'Visuals',
    getPath: (id) => `/datasets/${id}/visualizations`,
    icon: 'bi-pie-chart',
    description: 'Scientific visualization gallery & interactive exploration',
  },
  {
    key: 'xai',
    stepNumber: 11,
    name: 'Explainability/XAI',
    shortName: 'XAI',
    getPath: (id) => `/datasets/${id}/xai`,
    icon: 'bi-eye',
    description: 'Permutation importance, algorithmic fairness & what-if scenarios',
  },
  {
    key: 'findings',
    stepNumber: 12,
    name: 'Findings',
    shortName: 'Findings',
    getPath: (id) => `/datasets/${id}/findings`,
    icon: 'bi-journal-check',
    description: 'Synthesized scientific discoveries with evidence audit trails',
  },
  {
    key: 'report',
    stepNumber: 13,
    name: 'Report',
    shortName: 'Report',
    getPath: (id) => `/datasets/${id}/report`,
    icon: 'bi-file-earmark-pdf',
    description: 'Publication-grade scientific report and PDF generation',
  },
];

const WorkflowContext = createContext(null);

export const WorkflowProvider = ({ children }) => {
  let authContext = null;
  try {
    authContext = useAuth();
  } catch (e) {
    authContext = null;
  }
  const isAuthenticated = authContext ? authContext.isAuthenticated : false;
  const authLoading = authContext ? authContext.loading : false;

  const [userDatasets, setUserDatasets] = useState([]);
  const [datasetsLoading, setDatasetsLoading] = useState(true);

  const [datasetId, setDatasetId] = useState(() => {
    return localStorage.getItem('autoscientist_dataset_id') || null;
  });
  const [dataset, setDataset] = useState(null);
  const [workflowStatus, setWorkflowStatus] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const clearDataset = useCallback(() => {
    setDatasetId(null);
    setDataset(null);
    setWorkflowStatus(null);
    localStorage.removeItem('autoscientist_dataset_id');
  }, []);

  const refreshUserDatasets = useCallback(async () => {
    if (!isAuthenticated) {
      setUserDatasets([]);
      setDatasetsLoading(false);
      return [];
    }
    try {
      setDatasetsLoading(true);
      const res = await datasetService.listDatasets();
      const list = Array.isArray(res?.data) ? res.data : [];
      setUserDatasets(list);
      if (list.length === 0) {
        clearDataset();
      } else {
        setDatasetId((prevId) => {
          if (!prevId || !list.some((d) => String(d.id) === String(prevId))) {
            const nextId = list[0].id;
            localStorage.setItem('autoscientist_dataset_id', nextId.toString());
            return nextId;
          }
          return prevId;
        });
      }
      return list;
    } catch (err) {
      console.error('Failed to load user datasets:', err);
      setUserDatasets([]);
      return [];
    } finally {
      setDatasetsLoading(false);
    }
  }, [isAuthenticated, clearDataset]);

  useEffect(() => {
    if (!authLoading) {
      if (isAuthenticated) {
        refreshUserDatasets();
      } else {
        setUserDatasets([]);
        setDatasetsLoading(false);
        clearDataset();
      }
    }
  }, [isAuthenticated, authLoading, refreshUserDatasets, clearDataset]);

  const hasDatasets = userDatasets.length > 0;

  const fetchWorkflowData = useCallback(async (id) => {
    if (!id || !isAuthenticated) return;
    try {
      setLoading(true);
      setError(null);
      const [dsRes, wfRes] = await Promise.all([
        datasetService.getDataset(id),
        datasetService.getWorkflowStatus(id).catch(() => null),
      ]);

      if (dsRes && dsRes.data) {
        setDataset(dsRes.data);
      }
      if (wfRes && wfRes.data) {
        setWorkflowStatus(wfRes.data);
      }
    } catch (err) {
      const status = err?.response?.status;
      if (status === 404 || status === 401 || status === 403) {
        // Stale persisted id (dataset deleted or no longer accessible):
        // forget it instead of leaving the app permanently stuck on an error.
        clearDataset();
        setError('The selected dataset is no longer available. Select another dataset to continue.');
      } else {
        console.error('Failed to load dataset workflow status:', err);
        setError('Failed to fetch dataset workflow status.');
      }
    } finally {
      setLoading(false);
    }
  }, [isAuthenticated, clearDataset]);

  const selectDataset = useCallback((id) => {
    if (!id) return;
    setDatasetId(id);
    try {
      localStorage.setItem('autoscientist_dataset_id', id.toString());
    } catch {
      // ignore
    }
    if (isAuthenticated) {
      fetchWorkflowData(id);
    }
  }, [isAuthenticated, fetchWorkflowData]);

  const refreshWorkflowStatus = useCallback(() => {
    if (datasetId && isAuthenticated) {
      fetchWorkflowData(datasetId);
    }
  }, [datasetId, isAuthenticated, fetchWorkflowData]);

  useEffect(() => {
    if (datasetId && isAuthenticated && !authLoading) {
      fetchWorkflowData(datasetId);
    }
  }, [datasetId, isAuthenticated, authLoading, fetchWorkflowData]);

  const isStageCompleted = useCallback((stageKey) => {
    if (!workflowStatus || !workflowStatus.stages) return false;
    const stage = workflowStatus.stages[stageKey];
    return stage ? Boolean(stage.completed) : false;
  }, [workflowStatus]);

  const getStageStatus = useCallback((stageKey) => {
    if (!workflowStatus || !workflowStatus.stages) return null;
    return workflowStatus.stages[stageKey] || null;
  }, [workflowStatus]);

  const getPrerequisiteInfo = useCallback((stageKey) => {
    if (!workflowStatus || !workflowStatus.stages) {
      return { met: true, message: null, prerequisiteStage: null };
    }
    const stage = workflowStatus.stages[stageKey];
    if (!stage) return { met: true, message: null, prerequisiteStage: null };
    return {
      met: stage.prerequisitesMet !== false,
      message: stage.prerequisiteMessage || null,
      prerequisiteStage: stage.prerequisiteStage || null,
    };
  }, [workflowStatus]);

  const getPreviousStage = useCallback((currentKey) => {
    const idx = WORKFLOW_STAGES.findIndex((s) => s.key === currentKey);
    if (idx <= 0) return null;
    const prev = WORKFLOW_STAGES[idx - 1];
    return {
      ...prev,
      path: prev.getPath(datasetId),
    };
  }, [datasetId]);

  const getNextStage = useCallback((currentKey) => {
    const idx = WORKFLOW_STAGES.findIndex((s) => s.key === currentKey);
    if (idx < 0 || idx >= WORKFLOW_STAGES.length - 1) return null;
    const next = WORKFLOW_STAGES[idx + 1];
    return {
      ...next,
      path: next.getPath(datasetId),
    };
  }, [datasetId]);

  const getRecommendedNextStep = useCallback((currentKey) => {
    if (!datasetId) return null;

    switch (currentKey) {
      case 'overview':
        return {
          title: 'Verify Data Quality',
          description: 'Dataset features and schema profiled. Inspect data quality metrics next.',
          actionText: 'Audit Data Quality',
          targetPath: `/datasets/${datasetId}/quality`,
          icon: 'bi-shield-check',
        };
      case 'quality':
        return {
          title: 'Exploratory Data Analysis',
          description: 'Data quality assessment complete. Explore automated distributions and correlations next.',
          actionText: 'Explore Automated EDA',
          targetPath: `/datasets/${datasetId}/eda`,
          icon: 'bi-graph-up',
        };
      case 'eda':
        return {
          title: 'Investigate Feature Associations',
          description: 'Your dataset has been explored. Investigate detected associations next.',
          actionText: 'View Associations',
          targetPath: `/datasets/${datasetId}/associations`,
          icon: 'bi-diagram-3-fill',
        };
      case 'associations':
        return {
          title: 'Formulate Research Question',
          description: 'Potential associations were detected. Create a research question to investigate them.',
          actionText: 'Create Research Question',
          targetPath: `/datasets/${datasetId}/research`,
          icon: 'bi-question-circle-fill',
        };
      case 'research':
        return {
          title: 'Formulate Testable Hypotheses',
          description: 'Research question formulated. Formalize testable null and alternative hypotheses next.',
          actionText: 'Formulate Hypotheses',
          targetPath: `/datasets/${datasetId}/hypotheses`,
          icon: 'bi-lightbulb-fill',
        };
      case 'hypotheses':
        return {
          title: 'Execute Statistical Tests',
          description: 'Testable hypotheses generated. Execute statistical testing to evaluate empirical evidence.',
          actionText: 'Run Statistical Tests',
          targetPath: `/datasets/${datasetId}/statistics`,
          icon: 'bi-calculator-fill',
        };
      case 'statistics':
        return {
          title: 'Train Predictive Models',
          description: 'Statistical testing is complete. Continue to predictive modeling.',
          actionText: 'Build Model',
          targetPath: `/datasets/${datasetId}/ml`,
          icon: 'bi-cpu-fill',
        };
      case 'ml':
        if (!isStageCompleted('ml')) {
          return {
            title: 'Train Candidate Models',
            description: 'Configure target variable and problem type to benchmark algorithms.',
            actionText: 'Run AutoML Benchmark',
            targetPath: `/datasets/${datasetId}/ml`,
            icon: 'bi-cpu-fill',
          };
        }
        return {
          title: 'Explain Trained Model',
          description: 'Model training is complete. Inspect model explainability, feature ranking, and fairness next.',
          actionText: 'Explain Model →',
          targetPath: `/datasets/${datasetId}/xai`,
          icon: 'bi-eye-fill',
        };
      case 'visualizations':
        if (!isStageCompleted('ml')) {
          return {
            title: 'Train a Model First',
            description: 'Scientific visualizations explored. Predictive modeling is the next logical step.',
            actionText: 'Go to Machine Learning →',
            targetPath: `/datasets/${datasetId}/ml`,
            icon: 'bi-cpu-fill',
          };
        }
        return {
          title: 'Explain Trained Model',
          description: 'Scientific visualizations explored. Model training is complete. Explain the selected model next.',
          actionText: 'Explain Model →',
          targetPath: `/datasets/${datasetId}/xai`,
          icon: 'bi-eye-fill',
        };
      case 'xai':
        if (!isStageCompleted('ml')) {
          return {
            title: 'Train a Model First',
            description: 'XAI requires a successfully trained model before explanations can be generated.',
            actionText: 'Go to Machine Learning →',
            targetPath: `/datasets/${datasetId}/ml`,
            icon: 'bi-cpu-fill',
          };
        }
        return {
          title: 'Synthesize Scientific Findings',
          description: 'Model explanation is complete. Review and synthesize the resulting findings.',
          actionText: 'Review Findings →',
          targetPath: `/datasets/${datasetId}/findings`,
          icon: 'bi-journal-check',
        };
      case 'findings':
        return {
          title: 'Generate Publication Report',
          description: 'Your analysis is ready to document.',
          actionText: 'Generate Report',
          targetPath: `/datasets/${datasetId}/report`,
          icon: 'bi-file-earmark-pdf-fill',
        };
      case 'report':
        return {
          title: 'Scientific Discovery Pipeline Complete',
          description: 'Scientific discovery pipeline complete. Download your publication-grade PDF report.',
          actionText: 'Download PDF Report',
          targetPath: `/datasets/${datasetId}/report`,
          icon: 'bi-download',
          isPdfDownload: true,
        };
      default:
        return null;
    }
  }, [datasetId]);

  return (
    <WorkflowContext.Provider
      value={{
        datasetId,
        dataset,
        userDatasets,
        hasDatasets,
        datasetsLoading,
        refreshUserDatasets,
        workflowStatus,
        loading,
        error,
        selectDataset,
        clearDataset,
        refreshWorkflowStatus,
        isStageCompleted,
        getStageStatus,
        getPrerequisiteInfo,
        getPreviousStage,
        getNextStage,
        getRecommendedNextStep,
      }}
    >
      {children}
    </WorkflowContext.Provider>
  );
};

export const useWorkflow = () => {
  const context = useContext(WorkflowContext);
  if (!context) {
    return {
      datasetId: null,
      dataset: null,
      userDatasets: [],
      hasDatasets: false,
      datasetsLoading: false,
      refreshUserDatasets: async () => [],
      workflowStatus: null,
      loading: false,
      error: null,
      selectDataset: () => {},
      clearDataset: () => {},
      refreshWorkflowStatus: () => {},
      isStageCompleted: () => false,
      getStageStatus: () => null,
      getPrerequisiteInfo: () => ({ met: true, message: null, prerequisiteStage: null }),
      getPreviousStage: () => null,
      getNextStage: () => null,
      getRecommendedNextStep: () => null,
    };
  }
  return context;
};
