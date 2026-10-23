import React from 'react';
import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import EvidenceGraphPanel, { ReliabilityScorecard } from '../components/EvidenceGraphPanel';

describe('EvidenceGraphPanel', () => {
  const sampleGraph = {
    nodes: [
      { id: 'ds-1', type: 'DATASET', label: 'Clinical Study', detail: '1000 rows, 12 columns' },
      { id: 'col-1', type: 'COLUMN', label: 'age', detail: 'Numeric feature' },
      { id: 'col-2', type: 'COLUMN', label: 'target', detail: 'Binary diagnosis' },
      { id: 'assoc-1', type: 'ASSOCIATION', label: 'age -> target', detail: 'Pearson r=0.42' },
      { id: 'q-1', type: 'QUESTION', label: 'Is age associated with target?', detail: 'Investigate risk' },
      { id: 'hyp-1', type: 'HYPOTHESIS', label: 'Higher age increases target risk', detail: 'One-tailed' },
      { id: 'test-1', type: 'TEST', label: "Student's t-test", detail: 'p < 0.001' },
      { id: 'exp-1', type: 'EXPERIMENT', label: 'Classification Experiment', detail: 'AutoML run' },
      { id: 'ver-1', type: 'VERSION', label: 'v1.0', detail: 'Experiment version 1' },
      { id: 'mod-1', type: 'MODEL', label: 'Random Forest', detail: 'AUC 0.89' },
      { id: 'xai-1', type: 'EXPLANATION', label: 'SHAP importance', detail: 'age is top feature' },
      { id: 'run-1', type: 'ANALYSIS_RUN', label: 'Run #42', detail: 'Automated pipeline' },
      { id: 'find-1', type: 'FINDING', label: 'Age is a significant predictor', detail: 'Evidence confirmed' },
      { id: 'rep-1', type: 'REPORT', label: 'Final Summary Report', detail: 'Published 2026-03' },
    ],
    edges: [
      { from: 'ds-1', to: 'col-1', type: 'CONTAINS' },
      { from: 'ds-1', to: 'col-2', type: 'CONTAINS' },
      { from: 'col-1', to: 'assoc-1', type: 'CORRELATED' },
      { from: 'assoc-1', to: 'q-1', type: 'INSPIRES' },
      { from: 'q-1', to: 'hyp-1', type: 'FORMULATES' },
      { from: 'hyp-1', to: 'test-1', type: 'TESTED_BY' },
      { from: 'test-1', to: 'find-1', type: 'SUPPORTS' },
      { from: 'exp-1', to: 'ver-1', type: 'HAS_VERSION' },
      { from: 'ver-1', to: 'mod-1', type: 'TRAINS' },
      { from: 'mod-1', to: 'xai-1', type: 'EXPLAINED_BY' },
      { from: 'find-1', to: 'rep-1', type: 'INCLUDED_IN' },
    ],
  };

  it('renders empty fallback when graph is null or has no nodes', () => {
    const { rerender } = render(<EvidenceGraphPanel graph={null} />);
    expect(screen.getByText(/No evidence graph is available for this dataset yet/i)).toBeInTheDocument();

    rerender(<EvidenceGraphPanel graph={{ nodes: [] }} />);
    expect(screen.getByText(/No evidence graph is available for this dataset yet/i)).toBeInTheDocument();
  });

  it('renders SVG provenance graph with all 14 node types and edges count', () => {
    render(<EvidenceGraphPanel graph={sampleGraph} />);

    expect(screen.getByRole('heading', { name: /Interactive Evidence Graph/i })).toBeInTheDocument();
    expect(screen.getByText(/14 nodes · 11 edges/i)).toBeInTheDocument();

    const svg = screen.getByRole('img', { name: /Evidence provenance graph/i });
    expect(svg).toBeInTheDocument();

    // Verify type labels are rendered inside SVG text elements
    expect(svg.textContent).toContain('DATASET');
    expect(svg.textContent).toContain('COLUMN');
    expect(svg.textContent).toContain('ASSOCIATION');
    expect(svg.textContent).toContain('QUESTION');
    expect(svg.textContent).toContain('HYPOTHESIS');
    expect(svg.textContent).toContain('TEST');
    expect(svg.textContent).toContain('EXPERIMENT');
    expect(svg.textContent).toContain('VERSION');
    expect(svg.textContent).toContain('MODEL');
    expect(svg.textContent).toContain('EXPLANATION');
    expect(svg.textContent).toContain('ANALYSIS_RUN');
    expect(svg.textContent).toContain('FINDING');
    expect(svg.textContent).toContain('REPORT');
  });

  it('allows clicking a node to display details and deselects on second click or close button', () => {
    const { container } = render(<EvidenceGraphPanel graph={sampleGraph} />);

    // Initially shows hint to select a node
    expect(screen.getByText(/Select a node to highlight its incoming and outgoing edges/i)).toBeInTheDocument();

    // Find the first SVG group node (Clinical Study)
    const nodeGroups = container.querySelectorAll('svg g[style*="cursor: pointer"]');
    expect(nodeGroups.length).toBe(14);

    // Click on the first node
    fireEvent.click(nodeGroups[0]);

    // Detail panel should appear
    expect(screen.getAllByText('Clinical Study').length).toBe(2);
    expect(screen.getByText('1000 rows, 12 columns')).toBeInTheDocument();
    expect(screen.getByText(/connected edge\(s\):/i)).toBeInTheDocument();

    // Click close button
    const closeBtn = screen.getByRole('button');
    fireEvent.click(closeBtn);

    // Should return to default hint
    expect(screen.getByText(/Select a node to highlight its incoming and outgoing edges/i)).toBeInTheDocument();
  });
});

describe('ReliabilityScorecard', () => {
  it('returns null when reliability is undefined or null', () => {
    const { container } = render(<ReliabilityScorecard reliability={null} />);
    expect(container.firstChild).toBeNull();
  });

  it('renders reliability scorecard with weighted checks, score, grade, and limitations', () => {
    const reliability = {
      score: 85,
      grade: 'STRONG',
      checks: [
        { id: 'c1', label: 'Sample Size Adequacy', status: 'PASS', detail: 'N=1200 rows', weight: 20 },
        { id: 'c2', label: 'Missing Data Rate', status: 'WARN', detail: '8% missingness in feature X', weight: 20 },
        { id: 'c3', label: 'Train/Test Split Discipline', status: 'PASS', detail: 'Holdout test isolated', weight: 15 },
        { id: 'c4', label: 'Multiple Hypothesis Correction', status: 'FAIL', detail: 'Uncorrected p-values', weight: 20 },
      ],
      limitations: [
        'Observational data cannot establish causality.',
        'Feature X missingness exceeds 5%.',
      ],
    };

    render(<ReliabilityScorecard reliability={reliability} />);

    expect(screen.getByText('85')).toBeInTheDocument();
    expect(screen.getByText('STRONG')).toBeInTheDocument();
    expect(screen.getByText('Sample Size Adequacy')).toBeInTheDocument();
    expect(screen.getAllByText('PASS').length).toBe(2);
    expect(screen.getByText('Missing Data Rate')).toBeInTheDocument();
    expect(screen.getByText('WARN')).toBeInTheDocument();
    expect(screen.getByText('Multiple Hypothesis Correction')).toBeInTheDocument();
    expect(screen.getByText('FAIL')).toBeInTheDocument();

    // Check limitations
    expect(screen.getByText(/Observational data cannot establish causality/i)).toBeInTheDocument();
    expect(screen.getByText(/Feature X missingness exceeds 5%/i)).toBeInTheDocument();
  });
});
