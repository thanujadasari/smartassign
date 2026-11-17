// Shared Recharts styling so every chart looks the same.
import { CHART } from '@/lib/labels';

export const axisProps = {
  stroke: CHART.axis,
  tick: { fill: CHART.axis, fontSize: 11.5 },
  tickLine: false,
  axisLine: false,
} as const;

export const gridProps = { stroke: CHART.grid, strokeDasharray: '3 3', vertical: false } as const;

export const tooltipProps = {
  cursor: { fill: 'rgba(15,31,58,0.04)' },
  contentStyle: {
    borderRadius: 8,
    border: '1px solid #e2e7ee',
    boxShadow: '0 10px 30px -10px rgba(15,27,45,0.25)',
    fontSize: 12.5,
    padding: '8px 10px',
  },
  labelStyle: { color: '#0f1b2d', fontWeight: 600, marginBottom: 4 },
} as const;

export const legendProps = {
  iconType: 'circle' as const,
  iconSize: 8,
  wrapperStyle: { fontSize: 12, color: '#5b6878', paddingTop: 8 },
};
