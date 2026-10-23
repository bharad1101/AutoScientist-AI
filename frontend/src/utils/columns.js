/**
 * Shared column-selection rules for the frontend.
 *
 * These mirror the backend policy (com.autoscientist.util.ColumnSelection):
 *  - `excluded` columns (user excluded at import/reprofile time) never enter analysis;
 *  - `IDENTIFIER` columns are never selected implicitly — they must be explicitly
 *    included by the user before they may be used in statistics, charts or models.
 *
 * Never hard-code `dataType !== 'IDENTIFIER'` checks in pages: always go through these
 * helpers so policy changes stay in one place.
 */

export const isIdentifier = (col) => Boolean(col) && col.dataType === 'IDENTIFIER';

export const isExcluded = (col) => Boolean(col) && col.excluded === true;

export const isInAnalysis = (col) => !isIdentifier(col) && !isExcluded(col);

/** Columns that participate in analysis by default. */
export const analyticColumns = (columns) => (columns || []).filter(isInAnalysis);

/** Numeric, in-analysis columns (candidate variables for stats/charts). */
export const numericAnalyticColumns = (columns) =>
  analyticColumns(columns).filter((c) => c.dataType === 'NUMERIC');

/** Categorical/boolean, in-analysis columns. */
export const categoricalAnalyticColumns = (columns) =>
  analyticColumns(columns).filter(
    (c) => c.dataType === 'CATEGORICAL' || c.dataType === 'BOOLEAN'
  );

/** All columns including identifiers/excluded ones (for display purposes). */
export const allColumns = (columns) => columns || [];

/** Resolve a user-typed variable name against known columns (case-insensitive). */
export const resolveColumn = (columns, name) => {
  if (!name) return null;
  const wanted = String(name).trim().toLowerCase();
  return (columns || []).find((c) => (c.columnName || '').toLowerCase() === wanted) || null;
};

/** Human-readable reason a column is not usable in analysis, or null when it is. */
export const analysisBlockReason = (col) => {
  if (!col) return null;
  if (isExcluded(col)) return 'Column is excluded from analysis.';
  if (isIdentifier(col)) return 'Identifier column — include it explicitly to analyse it.';
  return null;
};

/** Badge class for a data type (used consistently across pages). */
export const dataTypeBadgeClass = (dataType) => {
  switch (dataType) {
    case 'NUMERIC':
      return 'bg-primary';
    case 'CATEGORICAL':
      return 'bg-success';
    case 'BOOLEAN':
      return 'bg-warning text-dark';
    case 'DATE_TIME':
      return 'bg-info';
    case 'IDENTIFIER':
      return 'bg-secondary';
    default:
      return 'bg-secondary';
  }
};
