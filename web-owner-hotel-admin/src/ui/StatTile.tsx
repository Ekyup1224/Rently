import type { ReactNode } from 'react'
import { Card, Flex, Typography } from 'antd'
import { ArrowDownOutlined, ArrowUpOutlined } from '@ant-design/icons'
import { palette } from '../theme'

export type TileTone = 'brand' | 'teal' | 'accent' | 'violet' | 'rose'

const TONES: Record<TileTone, { accent: string; soft: string }> = {
  brand: { accent: palette.brand, soft: palette.brandSoft },
  teal: { accent: palette.teal, soft: palette.tealSoft },
  accent: { accent: palette.accent, soft: palette.accentSoft },
  violet: { accent: palette.violet, soft: '#f1e9fe' },
  rose: { accent: palette.rose, soft: '#ffe8ed' },
}

/**
 * One number, with what it means and which way it is going.
 *
 * <p>The colour is not decoration: a row of tiles reads as a row of unrelated
 * numbers unless something groups them, and the left bar does that faster than
 * any label.
 *
 * @param delta percentage change, positive or negative. Omit when there is
 *              nothing to compare against — a made-up baseline is worse than
 *              no baseline.
 * @param deltaGood whether an increase is good news. Cancellations going up is
 *                  not the same kind of green as revenue going up.
 */
export function StatTile({ label, value, suffix, hint, icon, tone = 'brand',
                          delta, deltaGood = true, loading }: {
  label: string
  value: ReactNode
  suffix?: string
  hint?: ReactNode
  icon?: ReactNode
  tone?: TileTone
  delta?: number | null
  deltaGood?: boolean
  loading?: boolean
}) {
  const { accent, soft } = TONES[tone]
  const rising = (delta ?? 0) >= 0
  const good = rising === deltaGood

  return (
    <Card
      size="small"
      loading={loading}
      className="stat-tile"
      style={{ '--tile-accent': accent, '--tile-soft': soft } as React.CSSProperties}
      styles={{ body: { padding: '16px 18px 16px 20px' } }}
    >
      <Flex justify="space-between" align="flex-start" gap={12}>
        <div style={{ minWidth: 0 }}>
          <Typography.Text style={{ color: palette.inkMuted, fontSize: 13 }}>
            {label}
          </Typography.Text>
          <div style={{ fontSize: 25, fontWeight: 650, lineHeight: 1.25, marginTop: 2,
                        letterSpacing: '-0.02em' }}>
            {value}
            {suffix && (
              <span style={{ fontSize: 14, fontWeight: 500, color: palette.inkMuted }}>
                {' '}{suffix}
              </span>
            )}
          </div>
          {(hint || delta != null) && (
            <Flex gap={8} align="center" style={{ marginTop: 5 }}>
              {delta != null && (
                <span
                  className="stat-tile__delta"
                  style={{ color: good ? palette.success : palette.danger }}
                >
                  {rising ? <ArrowUpOutlined /> : <ArrowDownOutlined />}
                  {' '}{Math.abs(delta).toFixed(1)}%
                </span>
              )}
              {hint && (
                <Typography.Text style={{ color: palette.inkMuted, fontSize: 12 }}>
                  {hint}
                </Typography.Text>
              )}
            </Flex>
          )}
        </div>
        {icon && <div className="stat-tile__icon">{icon}</div>}
      </Flex>
    </Card>
  )
}
