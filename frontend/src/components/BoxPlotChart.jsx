import React, { useMemo, useRef } from 'react';
import { Bar } from 'react-chartjs-2';
import {
  Chart as ChartJS,
  BarController,
  CategoryScale,
  LinearScale,
  BarElement,
  Tooltip,
  Legend,
} from 'chart.js';

ChartJS.register(BarController, CategoryScale, LinearScale, BarElement, Tooltip, Legend);

/**
 * Draws a real box-and-whisker plot from an empirical five-number summary
 * (min, Q1, median, Q3, max) plus the detected outliers.
 *
 * The plugin is registered locally on the chart instance so it cannot leak into
 * other charts on the page.
 */
const boxPlotPlugin = {
  id: 'boxPlotRenderer',
  afterDatasetsDraw(chart, args, pluginOptions) {
    const values = pluginOptions?.values || [];
    if (!values.length) return;

    const { ctx } = chart;
    const xScale = chart.scales.x;
    const yScale = chart.scales.y;
    if (!xScale || !yScale) return;

    const slot = xScale.width / Math.max(1, values.length);
    const boxWidth = Math.max(8, Math.min(70, slot * 0.55));
    const lineColor = 'rgba(37, 99, 235, 0.95)';
    const fillColor = 'rgba(37, 99, 235, 0.18)';
    const outlierColor = 'rgba(239, 68, 68, 0.9)';

    ctx.save();
    values.forEach((v, i) => {
      const x = xScale.getPixelForValue(i);
      const half = boxWidth / 2;

      const yMax = yScale.getPixelForValue(v.max);
      const yMin = yScale.getPixelForValue(v.min);
      const yQ3 = yScale.getPixelForValue(v.q3);
      const yQ1 = yScale.getPixelForValue(v.q1);
      const yMedian = yScale.getPixelForValue(v.median);

      // Whisker stems
      ctx.strokeStyle = lineColor;
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(x, yMax);
      ctx.lineTo(x, yQ3);
      ctx.moveTo(x, yQ1);
      ctx.lineTo(x, yMin);
      ctx.stroke();

      // Whisker caps
      ctx.beginPath();
      ctx.moveTo(x - half / 2, yMax);
      ctx.lineTo(x + half / 2, yMax);
      ctx.moveTo(x - half / 2, yMin);
      ctx.lineTo(x + half / 2, yMin);
      ctx.stroke();

      // Interquartile box
      ctx.fillStyle = fillColor;
      ctx.strokeStyle = lineColor;
      ctx.lineWidth = 1.5;
      ctx.fillRect(x - half, yQ3, boxWidth, yQ1 - yQ3);
      ctx.strokeRect(x - half, yQ3, boxWidth, yQ1 - yQ3);

      // Median line
      ctx.strokeStyle = 'rgba(15, 23, 42, 0.9)';
      ctx.lineWidth = 2.5;
      ctx.beginPath();
      ctx.moveTo(x - half, yMedian);
      ctx.lineTo(x + half, yMedian);
      ctx.stroke();

      // Outliers
      ctx.fillStyle = outlierColor;
      (v.outliers || []).forEach((o) => {
        const y = yScale.getPixelForValue(o);
        if (!Number.isFinite(y)) return;
        ctx.beginPath();
        ctx.arc(x, y, 3, 0, Math.PI * 2);
        ctx.fill();
      });
    });
    ctx.restore();
  },
};

export default function BoxPlotChart({ data, height = 340, valueLabel = 'Value' }) {
  const chartRef = useRef(null);

  const chartData = useMemo(
    () => ({
      labels: data.map((d) => d.label),
      datasets: [
        {
          label: valueLabel,
          // The boxes are painted by the plugin; this dataset only reserves the
          // category axis positions and provides the legend entry.
          data: data.map(() => null),
          backgroundColor: 'rgba(37, 99, 235, 0.18)',
          borderColor: 'rgba(37, 99, 235, 0.95)',
          borderWidth: 1.5,
        },
      ],
    }),
    [data, valueLabel]
  );

  const options = useMemo(() => {
    const allValues = data.flatMap((d) => [d.min, d.q1, d.median, d.q3, d.max, ...(d.outliers || [])]);
    const finite = allValues.filter((v) => typeof v === 'number' && Number.isFinite(v));
    const min = finite.length ? Math.min(...finite) : 0;
    const max = finite.length ? Math.max(...finite) : 1;
    const pad = (max - min) * 0.08 || 1;
    return {
      responsive: true,
      maintainAspectRatio: false,
      animation: { duration: 200 },
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: {
            title: (items) => items[0]?.label || '',
            label: () => '',
            afterBody: (items) => {
              const d = data[items[0]?.dataIndex];
              if (!d) return [];
              return [
                `Max: ${Number(d.max).toFixed(2)}`,
                `Q3: ${Number(d.q3).toFixed(2)}`,
                `Median: ${Number(d.median).toFixed(2)}`,
                `Q1: ${Number(d.q1).toFixed(2)}`,
                `Min: ${Number(d.min).toFixed(2)}`,
                `IQR: ${Number(d.iqr).toFixed(2)}`,
                `Outliers: ${(d.outliers || []).length}`,
              ];
            },
          },
        },
        boxPlotRenderer: { values: data },
      },
      scales: {
        x: {
          title: { display: true, text: 'Variable' },
          ticks: { autoSkip: false, maxRotation: 45, minRotation: 0 },
        },
        y: {
          title: { display: true, text: valueLabel },
          suggestedMin: min - pad,
          suggestedMax: max + pad,
        },
      },
    };
  }, [data, valueLabel]);

  return (
    <div style={{ height }}>
      <Bar ref={chartRef} data={chartData} options={options} plugins={[boxPlotPlugin]} />
    </div>
  );
}
