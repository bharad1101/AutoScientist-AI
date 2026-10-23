import React from 'react';
import { Link } from 'react-router-dom';
import { useWorkflow, WORKFLOW_STAGES } from '../context/WorkflowContext';

const stageMeta = (key) => WORKFLOW_STAGES.find((s) => s.key === key) || null;

/**
 * Shows the single best next action for the current stage.
 *
 * Source of truth is the backend's prerequisite-aware recommendation
 * (`workflowStatus.currentRecommendedStage`) — the hardcoded chain below is only
 * a fallback for when workflow status has not loaded yet (or for the final PDF
 * download action).
 */
const RecommendedNextStep = ({ stageKey, onCustomAction }) => {
  const {
    datasetId,
    getRecommendedNextStep,
    isStageCompleted,
    getStageStatus,
    workflowStatus,
  } = useWorkflow();

  if (!datasetId) return null;

  const completed = isStageCompleted(stageKey);
  const recommendedKey = workflowStatus?.currentRecommendedStage || null;
  const currentStatus = getStageStatus(stageKey);
  const fallback = getRecommendedNextStep(stageKey);

  let step = null;

  if (!completed && recommendedKey && recommendedKey !== stageKey) {
    // Backend says a different stage is the prerequisite-safe next action.
    const meta = stageMeta(recommendedKey);
    const status = getStageStatus(recommendedKey);
    if (meta) {
      step = {
        title: `Pipeline recommends: ${meta.name}`,
        description:
          status?.summary ||
          'The workflow engine selected this as the next stage whose prerequisites are met.',
        actionText: `Go to ${meta.shortName}`,
        targetPath: meta.getPath(datasetId),
        icon: meta.icon,
      };
    }
  } else if (!completed && recommendedKey === stageKey) {
    // This stage is next but unfinished: nudge completion with honest status text.
    const meta = stageMeta(stageKey);
    if (meta) {
      step = {
        title: `Complete: ${meta.name}`,
        description:
          currentStatus?.summary || meta.description || 'This stage has not finished running yet.',
        actionText: 'Stay on this stage',
        targetPath: meta.getPath(datasetId),
        icon: meta.icon,
      };
    }
  }

  // Fallback chain (works even if workflow status never loaded).
  if (!step) step = fallback;
  if (!step) return null;

  const highlight = completed || (recommendedKey === stageKey && !completed);

  return (
    <div
      className={`card border-0 shadow-sm mb-4 ${
        highlight
          ? 'bg-primary bg-opacity-10 border border-primary border-opacity-25'
          : 'bg-light border'
      }`}
    >
      <div className="card-body p-4">
        <div className="d-flex flex-column flex-md-row align-items-md-center justify-content-between gap-3">
          <div className="d-flex align-items-center gap-3">
            <div
              className={`rounded-circle d-flex align-items-center justify-content-center p-3 fs-3 ${
                highlight ? 'bg-primary text-white shadow-sm' : 'bg-secondary bg-opacity-10 text-secondary'
              }`}
              style={{ width: '56px', height: '56px' }}
            >
              <i className={`bi ${step.icon || 'bi-arrow-right-circle'}`}></i>
            </div>
            <div>
              <div className="d-flex align-items-center gap-2 mb-1">
                <span className={`badge ${highlight ? 'bg-primary' : 'bg-secondary'} text-uppercase small`}>
                  Recommended Next Step
                </span>
                {completed && (
                  <span className="badge bg-success-subtle text-success border border-success-subtle small">
                    <i className="bi bi-check-circle-fill me-1"></i> Current Stage Done
                  </span>
                )}
                {!completed && currentStatus && (
                  <span className="badge bg-light text-dark border small">
                    {currentStatus.count != null ? `${currentStatus.count} recorded` : 'in progress'}
                  </span>
                )}
              </div>
              <h5 className="fw-bold mb-1 text-dark">{step.title}</h5>
              <p className="text-secondary mb-0 small">{step.description}</p>
            </div>
          </div>

          <div className="text-nowrap mt-2 mt-md-0">
            {step.isPdfDownload && onCustomAction ? (
              <button
                onClick={onCustomAction}
                className="btn btn-primary fw-semibold px-4 py-2 shadow-sm d-flex align-items-center gap-2"
              >
                <i className="bi bi-file-earmark-pdf-fill"></i>
                <span>{step.actionText}</span>
              </button>
            ) : (
              <Link
                to={step.targetPath}
                className="btn btn-primary fw-semibold px-4 py-2 shadow-sm d-flex align-items-center gap-2"
              >
                <span>{step.actionText}</span>
                <i className="bi bi-arrow-right"></i>
              </Link>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default RecommendedNextStep;
