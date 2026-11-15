import { useState } from 'react'

const MONTH_WIDTH = 112
const CHART_HEIGHT = 540
const MARGIN = { top: 30, right: 30, bottom: 66, left: 92 }

/**
 * Auto-scales the Y-axis to the data's magnitude so a member with 2M UGX of
 * savings gets a readable 0-2M chart, while a 400M portfolio still uses the
 * 500M+ / 50M-step scale. Tiers: <1M (100K steps), <10M (1M), <50M (5M),
 * <100M (10M), >=100M (50M steps, 500M+ ceiling).
 */
function autoScale(maxValue) {
  const value = Number(maxValue || 0)
  if (value <= 1_000_000) {
    const step = 100_000
    return { ceiling: Math.max(step, Math.ceil(value / step) * step), step, topTier: false }
  }
  if (value <= 10_000_000) {
    const step = 1_000_000
    return { ceiling: Math.max(step, Math.ceil(value / step) * step), step, topTier: false }
  }
  if (value <= 50_000_000) {
    const step = 5_000_000
    return { ceiling: Math.max(step, Math.ceil(value / step) * step), step, topTier: false }
  }
  if (value <= 100_000_000) {
    const step = 10_000_000
    return { ceiling: Math.max(step, Math.ceil(value / step) * step), step, topTier: false }
  }
  const step = 50_000_000
  return { ceiling: Math.max(500_000_000, Math.ceil(value / step) * step), step, topTier: true }
}

function compactMoney(value) {
  const number = Number(value || 0)
  const abs = Math.abs(number)
  if (abs >= 1_000_000_000) return `${trimScale(number / 1_000_000_000)}B`
  if (abs >= 1_000_000) return `${trimScale(number / 1_000_000)}M`
  if (abs >= 1_000) return `${trimScale(number / 1_000)}K`
  return String(Math.round(number))
}

function trimScale(value) {
  const abs = Math.abs(value)
  const digits = abs >= 10 ? 0 : 1
  return Number(value.toFixed(digits)).toString()
}

function fullMoney(value) {
  return `UGX ${Number(value || 0).toLocaleString()}`
}

function normalizePoint(point) {
  const close = Number(point.close ?? point.cumulative ?? 0)
  const net = Number(point.net ?? 0)
  const open = Number(point.open ?? close - net)
  const high = Number(point.high ?? Math.max(open, close))
  const low = Number(point.low ?? Math.min(open, close))
  return {
    ...point,
    open,
    high: Math.max(high, open, close),
    low: Math.min(low, open, close),
    close,
    net,
    deposits: Number(point.deposits || 0),
    withdrawals: Number(point.withdrawals || 0)
  }
}

function CandlestickChart({ points = [], dark = false }) {
  const [hoveredIndex, setHoveredIndex] = useState(null)

  const data = points.map(normalizePoint)
  const highest = data.reduce((max, point) => Math.max(max, point.high), 0)
  const { ceiling: axisMax, step: yStep, topTier } = autoScale(highest)
  const ticks = []
  for (let value = 0; value <= axisMax; value += yStep) {
    ticks.push(value)
  }

  const plotHeight = CHART_HEIGHT - MARGIN.top - MARGIN.bottom
  const plotWidth = Math.max(760, data.length * MONTH_WIDTH)
  const chartWidth = plotWidth + MARGIN.left + MARGIN.right

  const textColor = dark ? '#cbd5e1' : '#475569'
  const mutedColor = dark ? '#94a3b8' : '#64748b'
  const gridColor = dark ? 'rgba(148,163,184,0.16)' : 'rgba(100,116,139,0.16)'
  const axisColor = dark ? '#475569' : '#cbd5e1'
  const gainFill = dark ? '#10b981' : '#059669'
  const gainStroke = dark ? '#6ee7b7' : '#047857'
  const lossFill = dark ? '#f59e0b' : '#b45309'
  const lossStroke = dark ? '#fcd34d' : '#7c2d12'

  const yFor = value => MARGIN.top + ((axisMax - value) / axisMax) * plotHeight
  const xFor = index => MARGIN.left + index * MONTH_WIDTH + MONTH_WIDTH / 2

  const active = data.length === 0
    ? null
    : data[Math.min(hoveredIndex ?? data.length - 1, data.length - 1)]

  return (
    <div className="candlestick-widget">
      <div className="candlestick-topline">
        <div className="candlestick-legend">
          <span><i className="candle-swatch candle-gain" /> Monthly gain</span>
          <span><i className="candle-swatch candle-loss" /> Monthly deficit</span>
          <span className="candlestick-scale-note">Y-axis 0–{compactMoney(axisMax)}{topTier ? '+' : ''} · {compactMoney(yStep)} steps</span>
        </div>
        {active && (
          <div className="candlestick-readout">
            <strong>{active.month}</strong>
            <span>O {compactMoney(active.open)}</span>
            <span>H {compactMoney(active.high)}</span>
            <span>L {compactMoney(active.low)}</span>
            <span>C {compactMoney(active.close)}</span>
            <span className={active.net >= 0 ? 'readout-gain' : 'readout-loss'}>
              Net {active.net >= 0 ? '+' : '−'}{compactMoney(Math.abs(active.net))}
            </span>
          </div>
        )}
      </div>

      <div className="candlestick-scroll" tabIndex={0} aria-label="Monthly savings OHLC candlestick chart, scroll horizontally for more months">
        <svg
          className="candlestick-svg"
          width={chartWidth}
          height={CHART_HEIGHT}
          viewBox={`0 0 ${chartWidth} ${CHART_HEIGHT}`}
          role="img"
          aria-label="Monthly savings opening, high, low and closing balances"
        >
          <rect
            x={MARGIN.left}
            y={MARGIN.top}
            width={plotWidth}
            height={plotHeight}
            fill="transparent"
            stroke={axisColor}
          />

          {ticks.map((value, index) => {
            const y = yFor(value)
            const isTop = index === ticks.length - 1
            const label = `${compactMoney(value)}${isTop && topTier ? '+' : ''}`
            return (
              <g key={value}>
                <line
                  x1={MARGIN.left}
                  x2={MARGIN.left + plotWidth}
                  y1={y}
                  y2={y}
                  stroke={gridColor}
                  strokeWidth={value === 0 ? 1.4 : 1}
                  strokeDasharray={value === 0 ? undefined : '3 5'}
                />
                <text
                  x={MARGIN.left - 12}
                  y={y + 4}
                  textAnchor="end"
                  fill={mutedColor}
                  fontSize="11.5"
                  fontFamily="ui-monospace, SFMono-Regular, Menlo, monospace"
                >
                  {label}
                </text>
              </g>
            )
          })}

          <text
            x={20}
            y={MARGIN.top + plotHeight / 2}
            transform={`rotate(-90 20 ${MARGIN.top + plotHeight / 2})`}
            textAnchor="middle"
            fill={mutedColor}
            fontSize="11"
            fontWeight="700"
            letterSpacing="0.08em"
          >
            UGX BALANCE
          </text>

          {data.length === 0 && (
            <text
              x={MARGIN.left + plotWidth / 2}
              y={MARGIN.top + plotHeight / 2}
              textAnchor="middle"
              fill={mutedColor}
              fontSize="14"
            >
              No completed savings transactions in the visible period.
            </text>
          )}

          {data.length > 1 && (
            <polyline
              points={data.map((point, index) => `${xFor(index)},${yFor(point.close)}`).join(' ')}
              fill="none"
              stroke={mutedColor}
              strokeWidth="1.4"
              strokeDasharray="5 5"
              opacity="0.55"
            />
          )}

          {data.map((point, index) => {
            const x = xFor(index)
            const gain = point.net >= 0
            const fill = gain ? gainFill : lossFill
            const stroke = gain ? gainStroke : lossStroke
            const bodyTop = yFor(Math.max(point.open, point.close))
            const bodyBottom = yFor(Math.min(point.open, point.close))
            const bodyHeight = Math.max(3, bodyBottom - bodyTop)
            const bodyWidth = 42
            return (
              <g
                key={`${point.month}-${index}`}
                onMouseEnter={() => setHoveredIndex(index)}
                onMouseLeave={() => setHoveredIndex(null)}
                style={{ cursor: 'crosshair' }}
              >
                <title>
                  {`${point.month}\nOpen: ${fullMoney(point.open)}\nHigh: ${fullMoney(point.high)}\nLow: ${fullMoney(point.low)}\nClose: ${fullMoney(point.close)}\nNet: ${fullMoney(point.net)}\nDeposits: ${fullMoney(point.deposits)}\nWithdrawals: ${fullMoney(point.withdrawals)}`}
                </title>
                <line
                  x1={x}
                  x2={x}
                  y1={yFor(point.high)}
                  y2={yFor(point.low)}
                  stroke={stroke}
                  strokeWidth="2.2"
                  strokeLinecap="round"
                />
                <rect
                  x={x - bodyWidth / 2}
                  y={bodyTop}
                  width={bodyWidth}
                  height={bodyHeight}
                  rx="2.5"
                  fill={fill}
                  stroke={stroke}
                  strokeWidth="1.2"
                  opacity={hoveredIndex === null || hoveredIndex === index ? 0.96 : 0.68}
                />
                <line
                  x1={x - bodyWidth / 2}
                  x2={x + bodyWidth / 2}
                  y1={yFor(point.open)}
                  y2={yFor(point.open)}
                  stroke={stroke}
                  strokeWidth="1"
                  opacity="0.65"
                />
                <text
                  x={x}
                  y={CHART_HEIGHT - 34}
                  textAnchor="middle"
                  fill={textColor}
                  fontSize="11.5"
                  fontWeight={hoveredIndex === index ? '700' : '500'}
                >
                  {point.month}
                </text>
                <text
                  x={x}
                  y={CHART_HEIGHT - 16}
                  textAnchor="middle"
                  fill={gain ? gainStroke : lossStroke}
                  fontSize="10"
                  fontFamily="ui-monospace, SFMono-Regular, Menlo, monospace"
                >
                  {gain ? '▲' : '▼'} {compactMoney(Math.abs(point.net))}
                </text>
              </g>
            )
          })}
        </svg>
      </div>
      <p className="candlestick-hint">
        Each candle shows the opening balance, peak, withdrawal trough and closing balance for a month.
        Scroll horizontally to review the full timeline.
      </p>
    </div>
  )
}

export default CandlestickChart
