import React, { useState, useEffect, useRef } from 'react';
import { datasetService } from '../services/datasetService';
import { isInAnalysis, dataTypeBadgeClass } from '../utils/columns';

/**
 * Multi-step import wizard:
 *   1. Choose file (.csv / .xls / .xlsx) + name/description.
 *   2. Configure: sheet, header row, column exclusions, identifier overrides —
 *      live preview comes from the backend so preview and final import use the
 *      exact same code path.
 *   3. Commit (upload) and navigate to the new dataset.
 *
 * Nothing is persisted until step 3; the uploaded file itself is never modified.
 */
export default function ImportWizard({ onCancel, onImported }) {
  const [step, setStep] = useState(1);
  const [file, setFile] = useState(null);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [preview, setPreview] = useState(null);
  const [previewing, setPreviewing] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const [sheetName, setSheetName] = useState('');
  const [headerRow, setHeaderRow] = useState('');
  const [headerRowTouched, setHeaderRowTouched] = useState(false);
  const [excluded, setExcluded] = useState([]); // column names
  const [forceIdentifier, setForceIdentifier] = useState([]);
  const [forceNonIdentifier, setForceNonIdentifier] = useState([]);
  const fileInputRef = useRef(null);
  const debounceRef = useRef(null);

  const options = () => ({
    sheetName: sheetName || null,
    sheetIndex: null,
    headerRow: headerRow ? parseInt(headerRow, 10) : null,
    excludedColumns: excluded,
    forceIdentifier,
    forceNonIdentifier,
  });

  const runPreview = async (fileToPreview, opts) => {
    if (!fileToPreview) return;
    setPreviewing(true);
    setError('');
    try {
      const res = await datasetService.previewImport(fileToPreview, opts);
      setPreview(res?.data || null);
      if (
        res?.data?.headerRow &&
        !headerRowTouched &&
        !headerRow
      ) {
        setHeaderRow(String(res.data.headerRow));
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Could not preview this file.');
      setPreview(null);
    } finally {
      setPreviewing(false);
    }
  };

  const handleFileChange = (e) => {
    const selected = e.target.files?.[0];
    if (!selected) return;
    setFile(selected);
    if (!name) setName(selected.name.replace(/\.[^/.]+$/, ''));
    setSheetName('');
    setHeaderRow('');
    setHeaderRowTouched(false);
    setExcluded([]);
    setForceIdentifier([]);
    setForceNonIdentifier([]);
    setPreview(null);
    setStep(2);
    runPreview(selected, {});
  };

  // Any option change re-runs the preview (debounced for text inputs).
  const refreshPreview = (nextOpts, immediate = false) => {
    if (!file) return;
    if (debounceRef.current) clearTimeout(debounceRef.current);
    const call = () => runPreview(file, { ...options(), ...nextOpts });
    if (immediate) call();
    else debounceRef.current = setTimeout(call, 400);
  };

  useEffect(() => () => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
  }, []);

  const toggleInList = (list, setList, colName) => {
    const next = list.includes(colName)
      ? list.filter((c) => c !== colName)
      : [...list, colName];
    setList(next);
    return next;
  };

  const handleExcludeToggle = (colName) => {
    const nextExcluded = toggleInList(excluded, setExcluded, colName);
    // Re-including a column clears any identifier override interaction.
    refreshPreview({ excludedColumns: nextExcluded });
  };

  const handleForceIdentifier = (colName) => {
    const nextForceId = toggleInList(forceIdentifier, setForceIdentifier, colName);
    if (nextForceId.includes(colName)) {
      setForceNonIdentifier((prev) => prev.filter((c) => c !== colName));
    }
    refreshPreview({ forceIdentifier: nextForceId });
  };

  const handleForceInclude = (colName) => {
    const nextForceNonId = toggleInList(forceNonIdentifier, setForceNonIdentifier, colName);
    if (nextForceNonId.includes(colName)) {
      setForceIdentifier((prev) => prev.filter((c) => c !== colName));
    }
    refreshPreview({ forceNonIdentifier: nextForceNonId });
  };

  const submit = async (e) => {
    e.preventDefault();
    if (!file) {
      setError('Choose a file first.');
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      const res = await datasetService.uploadDataset(file, name, description, options());
      onImported?.(res?.data);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Import failed.');
      setSubmitting(false);
    }
  };

  const steps = [
    { n: 1, label: 'File' },
    { n: 2, label: 'Configure' },
    { n: 3, label: 'Import' },
  ];

  return (
    <>
      <div className="modal-header">
        <h5 className="modal-title fw-bold">
          <i className="bi bi-file-earmark-arrow-up text-primary me-2"></i>
          Import Dataset
        </h5>
        <button
          type="button"
          className="btn-close"
          onClick={onCancel}
          disabled={submitting || previewing}
        ></button>
      </div>

      {/* Step indicator */}
      <div className="px-4 pt-3">
        <ul className="nav nav-pills nav-fill small">
          {steps.map((s) => (
            <li className="nav-item" key={s.n}>
              <span
                className={`nav-link py-1 px-2 ${
                  step === s.n ? 'active fw-semibold' : step > s.n ? 'text-success' : 'text-muted'
                }`}
              >
                {step > s.n ? (
                  <i className="bi bi-check-circle-fill me-1"></i>
                ) : (
                  <span className="badge rounded-pill bg-light text-dark border me-1">{s.n}</span>
                )}
                {s.label}
              </span>
            </li>
          ))}
        </ul>
        <hr className="my-2" />
      </div>

      {error && (
        <div className="mx-4 mt-2 alert alert-danger py-2 small mb-0">
          <i className="bi bi-exclamation-triangle-fill me-2"></i>
          {error}
        </div>
      )}

      <div className="modal-body" style={{ maxHeight: '65vh', overflowY: 'auto' }}>
        {/* ---------------- Step 1: file ---------------- */}
        {step === 1 && (
          <div>
            <div className="mb-3">
              <label className="form-label fw-semibold small">Data File (.csv, .xls, .xlsx)*</label>
              <input
                ref={fileInputRef}
                type="file"
                className="form-control"
                accept=".csv,.xls,.xlsx"
                onChange={handleFileChange}
                disabled={submitting}
              />
              <div className="form-text small">
                Excel workbooks are converted to CSV at import; the original file is stored untouched.
                Formulas use the values last saved in Excel.
              </div>
            </div>
          </div>
        )}

        {/* ---------------- Step 2: configure + preview ---------------- */}
        {step === 2 && (
          <div>
            <div className="row g-3 mb-3">
              <div className="col-md-6">
                <label className="form-label fw-semibold small">Dataset Name</label>
                <input
                  type="text"
                  className="form-control form-control-sm"
                  placeholder="e.g. Clinical Trial Cohort A"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  disabled={submitting}
                />
              </div>
              <div className="col-md-6">
                <label className="form-label fw-semibold small">Worksheet</label>
                <select
                  className="form-select form-select-sm"
                  value={sheetName}
                  onChange={(e) => {
                    setSheetName(e.target.value);
                    refreshPreview({ sheetName: e.target.value }, true);
                  }}
                  disabled={submitting || previewing || !(preview?.sheets?.length > 0)}
                >
                  {!(preview?.sheets?.length > 0) && <option value="">(single sheet / CSV)</option>}
                  {(preview?.sheets || []).map((s) => (
                    <option key={s.index} value={s.name}>
                      {s.name} ({s.rowCount} rows × {s.columnCount} cols)
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div className="row g-3 mb-3">
              <div className="col-md-4">
                <label className="form-label fw-semibold small">Header Row</label>
                <input
                  type="number"
                  min="1"
                  className="form-control form-control-sm"
                  value={headerRow}
                  onChange={(e) => {
                    setHeaderRow(e.target.value);
                    setHeaderRowTouched(true);
                    refreshPreview({ headerRow: e.target.value ? parseInt(e.target.value, 10) : null });
                  }}
                  disabled={submitting || previewing}
                />
                <div className="form-text small">
                  {preview?.headerRowDetected && !headerRowTouched
                    ? `Auto-detected row ${preview.headerRow} — change if wrong.`
                    : '1-based row number of column names.'}
                </div>
              </div>
              <div className="col-md-8">
                <label className="form-label fw-semibold small">Research Description (optional)</label>
                <input
                  type="text"
                  className="form-control form-control-sm"
                  placeholder="Context: collection methodology, target variables, research scope..."
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  disabled={submitting}
                />
              </div>
            </div>

            {previewing && (
              <div className="text-center py-3">
                <span className="spinner-border spinner-border-sm me-2" role="status"></span>
                <span className="text-muted small">Parsing file…</span>
              </div>
            )}

            {preview && (
              <>
                {preview.warnings?.length > 0 && (
                  <div className="alert alert-warning py-2 small">
                    <i className="bi bi-exclamation-triangle me-1"></i>
                    <ul className="mb-0 ps-3">
                      {preview.warnings.map((w, i) => (
                        <li key={i}>{w}</li>
                      ))}
                    </ul>
                  </div>
                )}

                <div className="d-flex gap-3 small text-muted mb-2 flex-wrap">
                  <span>
                    <i className="bi bi-table me-1"></i>
                    {preview.rowCount?.toLocaleString()} rows × {preview.columnCount} columns
                  </span>
                  <span>
                    <i className="bi bi-file-earmark me-1"></i>
                    {preview.sourceFormat}
                    {preview.sheetName ? ` · ${preview.sheetName}` : ''}
                    {preview.headerRow ? ` · header row ${preview.headerRow}` : ''}
                  </span>
                </div>

                {/* Column configuration */}
                <div className="card border mb-3">
                  <div className="card-header bg-light py-2 small fw-semibold">
                    Columns — include in analysis?
                  </div>
                  <div className="card-body py-2" style={{ maxHeight: '190px', overflowY: 'auto' }}>
                    <div className="d-flex flex-wrap gap-2">
                      {(preview.columns || []).map((c) => {
                        const isExcluded = excluded.includes(c.columnName);
                        const isForcedId = forceIdentifier.includes(c.columnName);
                        const isForcedInclude = forceNonIdentifier.includes(c.columnName);
                        return (
                          <span
                            key={c.columnName}
                            className={`badge border d-inline-flex align-items-center gap-1 ${
                              isExcluded || isForcedId
                                ? 'bg-light text-muted border-secondary'
                                : isInAnalysis(c) || isForcedInclude
                                ? 'bg-light text-dark border-primary'
                                : 'bg-light text-dark border-secondary'
                            }`}
                            style={{ cursor: 'pointer' }}
                            title={
                              isExcluded
                                ? 'Excluded — click to include'
                                : 'Included — click to exclude'
                            }
                            onClick={() => !submitting && handleExcludeToggle(c.columnName)}
                          >
                            {c.columnName}
                            <span className={`badge ${dataTypeBadgeClass(c.dataType)} border-0 px-1`} style={{ fontSize: '0.62em' }}>
                              {c.dataType}
                            </span>
                            {!isExcluded && (
                              <span className="d-inline-flex">
                                <i
                                  className={`bi ${
                                    isForcedId ? 'bi-lock-fill text-danger' : 'bi-unlock text-muted'
                                  }`}
                                  title="Treat as identifier (excluded from analysis)"
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    if (!isForcedId) handleForceIdentifier(c.columnName);
                                  }}
                                ></i>
                                <i
                                  className={`bi ${
                                    isForcedInclude ? 'bi-check-circle-fill text-success' : 'bi-circle text-muted'
                                  } ms-1`}
                                  title="Explicitly include in analysis"
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    if (!isForcedInclude) handleForceInclude(c.columnName);
                                  }}
                                ></i>
                              </span>
                            )}
                          </span>
                        );
                      })}
                    </div>
                    <div className="form-text small mt-2">
                      <i className="bi bi-lock me-1"></i>identifier ·{' '}
                      <i className="bi bi-check-circle-fill text-success me-1"></i>explicitly included ·
                      click a column name to exclude/include it. Exclusions never modify the file.
                    </div>
                  </div>
                </div>

                {/* Preview table */}
                <div className="card border mb-2">
                  <div className="card-header bg-light py-2 small fw-semibold">
                    Data Preview {preview.rowCount > preview.previewRows?.length ? `(first ${preview.previewRows?.length} of ${preview.rowCount?.toLocaleString()} rows)` : ''}
                  </div>
                  <div className="table-responsive" style={{ maxHeight: '220px', overflowY: 'auto' }}>
                    <table className="table table-sm table-bordered mb-0">
                      <thead className="table-light sticky-top">
                        <tr>
                          {(preview.headers || []).map((h) => (
                            <th key={h} className="small text-nowrap">{h}</th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {(preview.previewRows || []).map((row, i) => (
                          <tr key={i}>
                            {row.map((cell, j) => (
                              <td key={j} className="small text-nowrap" style={{ maxWidth: '160px', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                                {cell}
                              </td>
                            ))}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              </>
            )}
          </div>
        )}

        {/* ---------------- Step 3: confirm ---------------- */}
        {step === 3 && (
          <div>
            <div className="list-group mb-3">
              <div className="list-group-item d-flex justify-content-between">
                <span className="text-muted">File</span>
                <span className="fw-semibold text-end">{file?.name}</span>
              </div>
              <div className="list-group-item d-flex justify-content-between">
                <span className="text-muted">Name</span>
                <span className="fw-semibold">{name || file?.name}</span>
              </div>
              {preview?.sourceFormat && (
                <div className="list-group-item d-flex justify-content-between">
                  <span className="text-muted">Format</span>
                  <span className="fw-semibold">
                    {preview.sourceFormat}
                    {preview.sheetName ? ` · sheet “${preview.sheetName}”` : ''}
                    {preview.headerRow ? ` · header row ${preview.headerRow}` : ''}
                  </span>
                </div>
              )}
              <div className="list-group-item d-flex justify-content-between">
                <span className="text-muted">Size</span>
                <span className="fw-semibold">
                  {preview?.rowCount?.toLocaleString()} rows × {preview?.columnCount} columns
                </span>
              </div>
              <div className="list-group-item d-flex justify-content-between">
                <span className="text-muted">Excluded from analysis</span>
                <span className="fw-semibold text-end">
                  {excluded.length ? excluded.join(', ') : 'none'}
                </span>
              </div>
            </div>
            <p className="small text-muted mb-0">
              The original file is stored unmodified. Analysis engines read the configured view
              defined above.
            </p>
          </div>
        )}
      </div>

      <div className="modal-footer">
        {step > 1 && (
          <button
            type="button"
            className="btn btn-outline-secondary"
            onClick={() => setStep(step - 1)}
            disabled={submitting || previewing}
          >
            <i className="bi bi-arrow-left me-1"></i> Back
          </button>
        )}
        <button
          type="button"
          className="btn btn-outline-secondary"
          onClick={onCancel}
          disabled={submitting}
        >
          Cancel
        </button>

        {step === 1 && (
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => file && setStep(2)}
            disabled={!file || previewing}
          >
            Next <i className="bi bi-arrow-right ms-1"></i>
          </button>
        )}

        {step === 2 && (
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => setStep(3)}
            disabled={previewing || !preview}
          >
            Review <i className="bi bi-arrow-right ms-1"></i>
          </button>
        )}

        {step === 3 && (
          <button
            type="button"
            className="btn btn-primary d-flex align-items-center gap-2"
            onClick={submit}
            disabled={submitting || !file}
          >
            {submitting ? (
              <>
                <span className="spinner-border spinner-border-sm" role="status"></span>
                <span>Importing…</span>
              </>
            ) : (
              <>
                <i className="bi bi-cloud-arrow-up-fill"></i>
                <span>Import Dataset</span>
              </>
            )}
          </button>
        )}
      </div>
    </>
  );
}
