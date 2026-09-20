import type { ReactNode } from 'react'
import { Card, Empty, Flex, Typography } from 'antd'
import {
  Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, Legend, Line, LineChart, Pie, PieChart,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts'
import { chartColours, palette } from '../theme'

/**
 * Charts for the portal.
 *
 * <p>Thin wrappers over recharts rather than raw usage at each call site, so
 * every chart shares one grid style, one tooltip and one colour order. A chart
 * that styles itself is a chart that will drift from the one beside it.
 *
 * <p>All of them are deliberately plain: no 3D, no gradients behind the data, no
 * axis starting anywhere but zero. These read as numbers someone has to act on,
 * not as a dashboard to be admired.
 */

interface TooltipEntry {
  name?: string | number
  value?: number | string
  color?: string
  payload?: Record<string, unknown>
}

function ChartTooltip({ active, payload, label, format }: {
  active?: boolean
  payload?: TooltipEntry[]
  label?: string | number
  format?: (value: number, name: string) => string
}) {
  if (!active || !payload?.length) {
    return null
  }
  return (
    <div className="chart-tooltip">
      {label != null && <div className="chart-tooltip__label">{label}</div>}
      {payload.map((entry, index) => {
        const name = String(entry.name ?? '')
        const raw = Number(entry.value ?? 0)
        return (
          <div className="chart-tooltip__row" key={`${name}-${index}`}>
            <span className="chart-tooltip__dot" style={{ background: entry.color }} />
            <span>{name}</span>
            <strong style={{ marginInlineStart: 'auto', paddingInlineStart: 12 }}>
              {format ? format(raw, name) : raw.toLocaleString()}
            </strong>
          </div>
        )
      })}
    </div>
  )
}

/** Card chrome shared by every chart: title, optional note, and an empty state. */
function ChartCard({ title, extra, note, empty, height = 260, children }: {
  title: string
  extra?: ReactNode
  note?: ReactNode
  empty: boolean
  height?: number
  children: ReactNode
}) {
  return (
    <Card
      className="chart-card"
      title={title}
      extra={extra}
      size="small"
      styles={{ body: { paddingTop: 16 } }}
    >
      {note && (
        <Typography.Text style={{ color: palette.inkMuted, fontSize: 12 }}>
          {note}
        </Typography.Text>
      )}
      <div style={{ height, marginTop: note ? 10 : 0 }}>
        {empty ? (
          <Flex align="center" justify="center" style={{ height: '100%' }}>
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Nothing to chart yet" />
          </Flex>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            {children as React.ReactElement}
          </ResponsiveContainer>
        )}
      </div>
    </Card>
  )
}

/** What every chart here consumes: already-shaped rows, not domain objects. */
export type ChartRow = Record<string, string | number>

const GRID = { stroke: palette.line, strokeDasharray: '3 3', vertical: false } as const
const AXIS = { tickLine: false, axisLine: false } as const

/** Money or counts over time. Filled, because a total over time is a quantity. */
export function TrendChart({
  title, data, xKey, series, format, note, extra, height,
}: {
  title: string
  data: ChartRow[]
  xKey: string
  /**
   * @param axis 'right' puts this series on its own scale. Use it whenever two
   *             series differ by an order of magnitude — sharing an axis flattens
   *             the smaller one onto the baseline, which is worse than omitting it.
   */
  series: Array<{ key: string; label: string; axis?: 'left' | 'right'; format?: (value: number) => string }>
  format?: (value: number) => string
  note?: ReactNode
  extra?: ReactNode
  height?: number
}) {
  const hasRight = series.some((entry) => entry.axis === 'right')
  const rightFormat = series.find((entry) => entry.axis === 'right')?.format

  return (
    <ChartCard title={title} note={note} extra={extra} empty={data.length === 0} height={height}>
      <AreaChart data={data} margin={{ top: 6, right: 8, bottom: 0, left: -12 }}>
        <defs>
          {series.map((entry, index) => (
            <linearGradient key={entry.key} id={`fill-${entry.key}`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={chartColours[index % chartColours.length]}
                    stopOpacity={0.22} />
              <stop offset="100%" stopColor={chartColours[index % chartColours.length]}
                    stopOpacity={0.01} />
            </linearGradient>
          ))}
        </defs>
        <CartesianGrid {...GRID} />
        <XAxis dataKey={xKey} {...AXIS} />
        <YAxis yAxisId="left" {...AXIS} width={64}
               tickFormatter={(value: number) => format ? format(value) : String(value)} />
        {hasRight && (
          <YAxis yAxisId="right" orientation="right" {...AXIS} width={56}
                 tickFormatter={(value: number) =>
                   rightFormat ? rightFormat(value) : String(value)} />
        )}
        <Tooltip content={<ChartTooltip format={(value, name) => {
          const entry = series.find((candidate) => candidate.label === name)
          const chosen = entry?.format ?? format
          return chosen ? chosen(value) : String(value)
        }} />} />
        {series.length > 1 && <Legend iconType="circle" iconSize={8} />}
        {series.map((entry, index) => (
          <Area
            key={entry.key}
            // Straight segments, not a curve: a spline between two daily figures
            // bulges through the days between them, drawing money that never moved.
            type="linear"
            dataKey={entry.key}
            name={entry.label}
            yAxisId={entry.axis === 'right' ? 'right' : 'left'}
            stroke={chartColours[index % chartColours.length]}
            strokeWidth={2}
            fill={`url(#fill-${entry.key})`}
            dot={false}
            activeDot={{ r: 4 }}
          />
        ))}
      </AreaChart>
    </ChartCard>
  )
}

/**
 * A rate over time. A line, not an area: a percentage is not a quantity to fill.
 *
 * <p>Days whose value is null are drawn as gaps rather than joined through zero.
 * A day with no revenue has no take rate; plotting it as 0% invents a collapse
 * that never happened.
 */
export function RateChart({
  title, data, xKey, valueKey, label, note, height,
}: {
  title: string
  data: Array<Record<string, string | number | null>>
  xKey: string
  valueKey: string
  label: string
  note?: ReactNode
  height?: number
}) {
  return (
    <ChartCard title={title} note={note} empty={data.length === 0} height={height}>
      <LineChart data={data} margin={{ top: 6, right: 8, bottom: 0, left: -16 }}>
        <CartesianGrid {...GRID} />
        <XAxis dataKey={xKey} {...AXIS} />
        <YAxis {...AXIS} width={48} tickFormatter={(value: number) => `${value}%`} />
        <Tooltip content={<ChartTooltip format={(value) => `${value.toFixed(1)}%`} />} />
        <Line
          type="linear"
          dataKey={valueKey}
          name={label}
          stroke={palette.accent}
          strokeWidth={2.5}
          // A gap for a day with nothing to divide by, rather than a dive to zero.
          connectNulls={false}
          dot={{ r: 2.5, fill: palette.accent, strokeWidth: 0 }}
          activeDot={{ r: 4 }}
        />
      </LineChart>
    </ChartCard>
  )
}

export interface Slice {
  name: string
  value: number
  colour?: string
}

/**
 * A donut, for how a whole divides up.
 *
 * <p>A donut rather than a pie so the total can sit in the middle — the number
 * people actually want beside the proportions. Slices are always sorted largest
 * first, because a ring in arbitrary order is a puzzle rather than a chart.
 */
export function BreakdownChart({ title, slices, total, totalLabel, note, height = 260 }: {
  title: string
  slices: Slice[]
  total?: ReactNode
  totalLabel?: string
  note?: ReactNode
  height?: number
}) {
  const ordered = [...slices].filter((slice) => slice.value > 0)
    .sort((left, right) => right.value - left.value)

  return (
    <ChartCard title={title} note={note} empty={ordered.length === 0} height={height}>
      <PieChart>
        <Pie
          data={ordered}
          dataKey="value"
          nameKey="name"
          innerRadius="58%"
          outerRadius="82%"
          // Gaps only make sense between slices. With a single slice recharts
          // subtracts the padding from the one arc it has, collapsing a full
          // ring into a sliver — which reads as "almost nothing" when the truth
          // is "all of it".
          paddingAngle={ordered.length > 1 ? 2 : 0}
          stroke="none"
        >
          {ordered.map((slice, index) => (
            <Cell key={slice.name}
                  fill={slice.colour ?? chartColours[index % chartColours.length]} />
          ))}
        </Pie>
        <Tooltip content={<ChartTooltip />} />
        <Legend iconType="circle" iconSize={8} verticalAlign="bottom" />
        {total != null && (
          <text
            x="50%"
            y="44%"
            textAnchor="middle"
            dominantBaseline="middle"
            style={{ fontSize: 22, fontWeight: 650, fill: palette.ink }}
          >
            {total}
          </text>
        )}
        {totalLabel && (
          <text
            x="50%"
            y="44%"
            dy={20}
            textAnchor="middle"
            dominantBaseline="middle"
            style={{ fontSize: 11, fill: palette.inkMuted }}
          >
            {totalLabel}
          </text>
        )}
      </PieChart>
    </ChartCard>
  )
}

/** Ranked categories. Horizontal, so long labels stay readable. */
export function RankChart({ title, data, format, note, height = 260 }: {
  title: string
  data: Array<{ name: string; value: number }>
  format?: (value: number) => string
  note?: ReactNode
  height?: number
}) {
  const ordered = [...data].sort((left, right) => right.value - left.value)

  return (
    <ChartCard title={title} note={note} empty={ordered.length === 0} height={height}>
      <BarChart data={ordered} layout="vertical" margin={{ top: 4, right: 16, bottom: 0, left: 8 }}>
        <CartesianGrid stroke={palette.line} strokeDasharray="3 3" horizontal={false} />
        <XAxis type="number" {...AXIS}
               tickFormatter={(value: number) => format ? format(value) : String(value)} />
        <YAxis type="category" dataKey="name" {...AXIS} width={130} />
        <Tooltip cursor={{ fill: palette.ground }}
                 content={<ChartTooltip format={(value) => format ? format(value) : String(value)} />} />
        <Bar dataKey="value" name={title} radius={[0, 6, 6, 0]} barSize={16}>
          {ordered.map((entry, index) => (
            <Cell key={entry.name} fill={chartColours[index % chartColours.length]} />
          ))}
        </Bar>
      </BarChart>
    </ChartCard>
  )
}
