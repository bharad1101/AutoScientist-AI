import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import BoxPlotChart from '../components/BoxPlotChart';

vi.mock('react-chartjs-2', () => ({
  Bar: React.forwardRef((props, ref) => {
    React.useImperativeHandle(ref, () => ({
      plugins: [],
      update: vi.fn(),
    }));
    return (
      <div
        data-testid="mock-box-plot-bar"
        data-labels={JSON.stringify(props.data?.labels)}
        data-y-title={props.options?.scales?.y?.title?.text}
        data-plugin-values={JSON.stringify(props.options?.plugins?.boxPlotRenderer?.values)}
      />
    );
  }),
}));

describe('BoxPlotChart', () => {
  const sampleData = [
    {
      label: 'age',
      min: 18,
      q1: 25,
      median: 35,
      q3: 50,
      max: 75,
      iqr: 25,
      outliers: [85, 92],
    },
    {
      label: 'income',
      min: 10000,
      q1: 30000,
      median: 55000,
      q3: 85000,
      max: 150000,
      iqr: 55000,
      outliers: [],
    },
  ];

  it('renders correctly with 5-number summary data and custom height', () => {
    const { container } = render(
      <BoxPlotChart data={sampleData} height={400} valueLabel="Measurement Unit" />
    );

    const barEl = screen.getByTestId('mock-box-plot-bar');
    expect(barEl).toBeInTheDocument();

    const labels = JSON.parse(barEl.getAttribute('data-labels'));
    expect(labels).toEqual(['age', 'income']);

    expect(barEl.getAttribute('data-y-title')).toBe('Measurement Unit');

    const pluginValues = JSON.parse(barEl.getAttribute('data-plugin-values'));
    expect(pluginValues).toHaveLength(2);
    expect(pluginValues[0].median).toBe(35);
    expect(pluginValues[0].outliers).toEqual([85, 92]);
    expect(pluginValues[1].median).toBe(55000);

    const wrapper = container.firstChild;
    expect(wrapper).toHaveStyle({ height: '400px' });
  });

  it('handles empty data gracefully with default height and fallback label', () => {
    const { container } = render(<BoxPlotChart data={[]} />);

    const barEl = screen.getByTestId('mock-box-plot-bar');
    expect(barEl).toBeInTheDocument();

    const labels = JSON.parse(barEl.getAttribute('data-labels'));
    expect(labels).toEqual([]);
    expect(barEl.getAttribute('data-y-title')).toBe('Value');

    const wrapper = container.firstChild;
    expect(wrapper).toHaveStyle({ height: '340px' });
  });
});
