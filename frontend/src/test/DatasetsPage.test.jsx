import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import DatasetsPage from '../pages/DatasetsPage';
import { datasetService } from '../services/datasetService';

vi.mock('../services/datasetService', () => ({
  datasetService: {
    listDatasets: vi.fn(),
    deleteDataset: vi.fn(),
  },
}));

vi.mock('../components/ImportWizard', () => ({
  default: () => null,
}));

const dataset = (id, name) => ({
  id,
  name,
  originalFilename: `${name.toLowerCase()}.csv`,
  rowCount: 100,
  columnCount: 5,
  datasetType: 'TABULAR',
  qualityScore: 88,
  createdAt: '2026-03-01T12:00:00Z',
});

const renderPage = () =>
  render(
    <MemoryRouter>
      <DatasetsPage />
    </MemoryRouter>
  );

beforeEach(() => {
  vi.clearAllMocks();
  datasetService.listDatasets.mockResolvedValue({
    success: true,
    data: [dataset(1, 'Alpha'), dataset(2, 'Beta')],
  });
  datasetService.deleteDataset.mockResolvedValue({ success: true });
});

describe('DatasetsPage (RESP-01)', () => {
  it('renders a compact icon-only action group for mobile viewports (<768px) with accessible labels', async () => {
    const { container } = renderPage();
    await screen.findByText('Alpha');

    const mobileGroups = container.querySelectorAll('[data-testid="row-actions-mobile"]');
    expect(mobileGroups.length).toBe(2); // one per dataset row

    const mobile = within(mobileGroups[0]);
    const inspect = mobile.getByLabelText('Inspect dataset Alpha');
    expect(inspect).toHaveAttribute('href', '/datasets/1/overview');

    const associations = mobile.getByLabelText('Associations for dataset Alpha');
    expect(associations).toHaveAttribute('href', '/datasets/1/associations');

    expect(mobile.getByLabelText('Delete dataset Alpha')).toBeInTheDocument();

    // Mobile actions are icon-only (no visible text labels that would wrap).
    expect(inspect.textContent).toBe('');
    expect(associations.textContent).toBe('');
  });

  it('keeps the full labelled desktop actions for screens >=768px', async () => {
    const { container } = renderPage();
    await screen.findByText('Alpha');

    const desktop = within(container.querySelector('[data-testid="row-actions-desktop"]'));
    expect(desktop.getByRole('link', { name: /inspect/i })).toHaveAttribute(
      'href',
      '/datasets/1/overview'
    );
    expect(desktop.getByRole('link', { name: /associations/i })).toHaveAttribute(
      'href',
      '/datasets/1/associations'
    );
    expect(desktop.getByRole('button', { name: /delete dataset alpha/i })).toBeInTheDocument();
  });

  it('asks for confirmation before deleting and reloads on success', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    renderPage();
    await screen.findByText('Alpha');

    const mobile = within(document.querySelector('[data-testid="row-actions-mobile"]'));
    fireEvent.click(mobile.getByLabelText('Delete dataset Alpha'));

    expect(confirmSpy).toHaveBeenCalledWith(
      'Are you sure you want to delete dataset "Alpha"? This action cannot be undone.'
    );
    expect(datasetService.deleteDataset).toHaveBeenCalledWith(1);
    await vi.waitFor(() => {
      expect(datasetService.listDatasets).toHaveBeenCalledTimes(2); // initial + after delete
    });
    confirmSpy.mockRestore();
  });

  it('does not delete when the confirmation is declined', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);
    renderPage();
    await screen.findByText('Alpha');

    const mobileGroups = document.querySelectorAll('[data-testid="row-actions-mobile"]');
    const betaMobile = within(mobileGroups[1]);
    fireEvent.click(betaMobile.getByLabelText('Delete dataset Beta'));

    expect(datasetService.deleteDataset).not.toHaveBeenCalled();
    confirmSpy.mockRestore();
  });

  it('renders the empty state when no datasets exist', async () => {
    datasetService.listDatasets.mockResolvedValue({ success: true, data: [] });
    renderPage();

    expect(await screen.findByText('No Datasets Ingested Yet')).toBeInTheDocument();
  });
});
