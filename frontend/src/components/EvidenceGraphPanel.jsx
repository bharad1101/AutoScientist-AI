import React, { useMemo, useState } from 'react';

const TYPE_ORDER = [
  'DATASET',
  'COLUMN',
  'ASSOCIATION',
  'QUESTION',
  'HYPOTHESIS',
  'TEST',
  'EXPERIMENT',
  'VERSION',
  'MODEL',
  'EXPLANATION',
  'ANALYSIS_RUN',
  'FINDING',
  'REPORT',
];

const TYPE_STYLE = {
  DATASET: { fill: '#dbeafe', stroke: '#2563eb', text: '#1e40af' },
  COLUMN: { fill: '#e0f2fe', stroke: '#0284c7', text: '#075985' },
  ASSOCIATION: { fill: '#ede9fe', stroke: '#7c3aed', text: '#5b21b6' },
  QUESTION: { fill: '#fef3c7', stroke: '#d97706', text: '#92400e' },
  HYPOTHESIS: { fill: '#fef9c3', stroke: '#ca8a04', text: '#854d0e' },
  TEST: { fill: '#ffedd5', stroke: '#ea580c', text: '#9a3412' },
  EXPERIMENT: { fill: '#dcfce7', stroke: '#16a34a', text: '#166534' },
  VERSION: { fill: '#d1fae5', stroke: '#059669', text: '#065f46' },
  MODEL: { fill: '#ccfbf1', stroke: '#0d9488', text: '#115e59' },
  EXPLANATION: { fill: '#f3e8ff', stroke: '#9333ea', text: '#6b21a8' },
  ANALYSIS_RUN: { fill: '#f1f5f9', stroke: '#64748b', text: '#334155' },
  FINDING: { fill: '#fce7f3', stroke: '#db2777', text: '#9d174d' },
  REPORT: { fill: '#fee2e2', stroke: '#dc2626', text: '#991b1b' },
};

const NODE_W = 156;
const NODE_H = 44;
const COL_GAP = 190;
const ROW_GAP = 64;

const truncate = (value, max = 23) => {
  const text = String(value ?? '');
  return text.length > max ? `${text.slice(0, max - 1)}…` : text;
};

function gradeTone(grade) {
  switch (grade) {
    case 'STRONG': return 'success';
    case 'GOOD': return 'primary';
    case 'FAIR': return 'warning text-dark';
    case 'WEAK': return 'danger';
    default: return 'secondary';
  }
}

function checkTone(status) {
  switch (status) {
    case 'PASS': return 'success';
    case 'WARN': return 'warning text-dark';
    case 'FAIL': return 'danger';
    default: return 'secondary';
  }
}

export function ReliabilityScorecard({ reliability }) {
  if (!reliability) return null;
  const checks = reliability.checks || [];
  const limitations = reliability.limitations || [];
  return (
    <div className="card shadow-sm border-0 mb-4">
      <div className="card-header bg-white py-3 d-flex justify-content-between align-items-center flex-wrap gap-2">
        <div>
          <h5 className="fw-bold mb-0">
            <i className="bi bi-shield-check text-primary me-2"></i>
            Evidence Reliability Scorecard
          </h5>
          <small className="text-muted">
            Weighted audit of sample size, missingness, multiple-testing control, split discipline and validation.
          </small>
        </div>
        <div className="d-flex align-items-center gap-2">
          <span className="fs-3 fw-bold">{reliability.score}<span className="fs-6 text-muted">/100</span></span>
          <span className={`badge bg-${gradeTone(reliability.grade)}`}>{reliability.grade}</span>
        </div>
      </div>
      <div className="card-body p-0">
        <div className="table-responsive">
          <table className="table table-sm align-middle mb-0">
            <thead className="table-light">
              <tr>
                <th>Check</th>
                <th>Status</th>
                <th>Finding</th>
                <th className="text-end">Weight</th>
              </tr>
            </thead>
            <tbody>
              {checks.map((c) => (
                <tr key={c.id}>
                  <td className="fw-semibold">{c.label}</td>
                  <td><span className={`badge bg-${checkTone(c.status)}`}>{c.status}</span></td>
                  <td className="small text-muted">{c.detail}</td>
                  <td className="text-end text-muted">{c.weight}</td>
                </tr>
              ))}
              {checks.length === 0 && (
                <tr><td colSpan={4} className="text-center text-muted py-3">No reliability checks were recorded.</td></tr>
              )}
            </tbody>
          </table>
        </div>
        {limitations.length > 0 && (
          <div className="border-top p-3 bg-light">
            <div className="small fw-semibold text-uppercase text-muted mb-1">Limitations</div>
            <ul className="small text-secondary mb-0 ps-3">
              {limitations.map((l, i) => <li key={i}>{l}</li>)}
            </ul>
          </div>
        )}
      </div>
    </div>
  );
}

export default function EvidenceGraphPanel({ graph }) {
  const [selectedId, setSelectedId] = useState(null);

  const layout = useMemo(() => {
    if (!graph || !Array.isArray(graph.nodes) || graph.nodes.length === 0) {
      return { nodes: [], edges: [], width: 0, height: 0, types: [] };
    }
    const grouped = new Map();
    graph.nodes.forEach((node) => {
      const type = node.type || 'OTHER';
      if (!grouped.has(type)) grouped.set(type, []);
      grouped.get(type).push(node);
    });
    const types = [...grouped.keys()].sort((a, b) => {
      const ai = TYPE_ORDER.indexOf(a);
      const bi = TYPE_ORDER.indexOf(b);
      return (ai === -1 ? 99 : ai) - (bi === -1 ? 99 : bi);
    });
    const positions = new Map();
    let maxRows = 0;
    types.forEach((type, colIndex) => {
      const list = grouped.get(type);
      maxRows = Math.max(maxRows, list.length);
      list.forEach((node, rowIndex) => {
        positions.set(node.id, {
          x: colIndex * COL_GAP + 24,
          y: rowIndex * ROW_GAP + 20,
          node,
        });
      });
    });
    const edges = (graph.edges || []).filter((e) => positions.has(e.from) && positions.has(e.to));
    return {
      nodes: graph.nodes,
      edges,
      positions,
      width: types.length * COL_GAP + 40,
      height: maxRows * ROW_GAP + 40,
      types,
    };
  }, [graph]);

  if (!graph || !graph.nodes || graph.nodes.length === 0) {
    return (
      <div className="card shadow-sm border-0 mb-4">
        <div className="card-body p-4 text-center text-muted">
          <i className="bi bi-diagram-3 fs-2 d-block mb-2"></i>
          No evidence graph is available for this dataset yet. Run associations, hypothesis tests or AutoML first.
        </div>
      </div>
    );
  }

  const selected = selectedId ? layout.positions.get(selectedId)?.node : null;
  const connectedEdges = selectedId
    ? layout.edges.filter((e) => e.from === selectedId || e.to === selectedId)
    : [];
  const connectedIds = new Set(connectedEdges.flatMap((e) => [e.from, e.to]));

  return (
    <div className="card shadow-sm border-0 mb-4">
      <div className="card-header bg-white py-3 d-flex justify-content-between align-items-center flex-wrap gap-2">
        <div>
          <h5 className="fw-bold mb-0">
            <i className="bi bi-diagram-3 text-primary me-2"></i>
            Interactive Evidence Graph
          </h5>
          <small className="text-muted">
            Dataset → columns → associations → questions/hypotheses → tests → models → findings → report.
            Click any node to inspect its provenance.
          </small>
        </div>
        <span className="badge bg-light text-dark border">
          {graph.nodes.length} nodes · {layout.edges.length} edges
        </span>
      </div>
      <div className="card-body p-0">
        <div className="table-responsive" style={{ maxHeight: '520px', overflow: 'auto' }}>
          <svg
            width={Math.max(layout.width, 600)}
            height={Math.max(layout.height, 240)}
            role="img"
            aria-label="Evidence provenance graph"
            style={{ display: 'block', minWidth: '100%' }}
          >
            <defs>
              <marker id="evidence-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
                <path d="M0,0 L8,4 L0,8 Z" fill="#94a3b8" />
              </marker>
            </defs>
            {layout.edges.map((edge, i) => {
              const from = layout.positions.get(edge.from);
              const to = layout.positions.get(edge.to);
              if (!from || !to) return null;
              const highlighted = selectedId && (edge.from === selectedId || edge.to === selectedId);
              const x1 = from.x + NODE_W;
              const y1 = from.y + NODE_H / 2;
              const x2 = to.x;
              const y2 = to.y + NODE_H / 2;
              const midX = (x1 + x2) / 2;
              return (
                <path
                  key={`${edge.from}-${edge.to}-${i}`}
                  d={`M ${x1} ${y1} C ${midX} ${y1}, ${midX} ${y2}, ${x2} ${y2}`}
                  fill="none"
                  stroke={highlighted ? '#2563eb' : '#cbd5e1'}
                  strokeWidth={highlighted ? 2 : 1}
                  markerEnd="url(#evidence-arrow)"
                />
              );
            })}
            {layout.nodes.map((node) => {
              const pos = layout.positions.get(node.id);
              if (!pos) return null;
              const style = TYPE_STYLE[node.type] || { fill: '#f1f5f9', stroke: '#94a3b8', text: '#334155' };
              const dimmed = selectedId && selectedId !== node.id && !connectedIds.has(node.id);
              return (
                <g
                  key={node.id}
                  transform={`translate(${pos.x}, ${pos.y})`}
                  style={{ cursor: 'pointer', opacity: dimmed ? 0.45 : 1 }}
                  onClick={() => setSelectedId((current) => (current === node.id ? null : node.id))}
                >
                  <rect
                    width={NODE_W}
                    height={NODE_H}
                    rx={8}
                    fill={style.fill}
                    stroke={selectedId === node.id ? '#0f172a' : style.stroke}
                    strokeWidth={selectedId === node.id ? 2.5 : 1.2}
                  />
                  <text x={8} y={15} fontSize={8.5} fontWeight={700} fill={style.text} letterSpacing={0.4}>
                    {node.type}
                  </text>
                  <text x={8} y={32} fontSize={10} fill="#0f172a">
                    {truncate(node.label)}
                  </text>
                </g>
              );
            })}
          </svg>
        </div>
        {selected ? (
          <div className="border-top p-3 bg-light">
            <div className="d-flex justify-content-between align-items-start gap-2">
              <div>
                <span className="badge bg-dark mb-1">{selected.type}</span>
                <div className="fw-bold">{selected.label}</div>
                <div className="small text-muted">{selected.detail || 'No additional detail recorded.'}</div>
              </div>
              <button className="btn btn-sm btn-outline-secondary" onClick={() => setSelectedId(null)}>
                <i className="bi bi-x-lg"></i>
              </button>
            </div>
            {connectedEdges.length > 0 && (
              <div className="small text-muted mt-2">
                <strong>{connectedEdges.length}</strong> connected edge(s):{' '}
                {connectedEdges.slice(0, 6).map((e) => `${e.type}`).join(', ')}
                {connectedEdges.length > 6 ? '…' : ''}
              </div>
            )}
          </div>
        ) : (
          <div className="border-top px-3 py-2 small text-muted">
            <i className="bi bi-hand-index me-1"></i> Select a node to highlight its incoming and outgoing edges.
          </div>
        )}
      </div>
    </div>
  );
}
