import { useEffect, useMemo, useState } from 'react'
import { api } from '../services/api'

const TYPE_OPTIONS = [
  ['', 'All transaction types'],
  ['DEPOSIT', 'Deposits'],
  ['WITHDRAWAL', 'Withdrawals'],
  ['TRANSFER', 'Transfers'],
  ['LOAN_REPAYMENT', 'Loan Repayments'],
  ['SHARE_', 'Share Transactions'],
  ['IMPORT', 'Imports / Procurement']
]

const CHANNEL_OPTIONS = [
  ['', 'All channels'],
  ['CASH', 'Cash'],
  ['BANK', 'Bank'],
  ['MOBILE_MONEY', 'Mobile Money'],
  ['CROSS_BANK', 'Cross-Bank'],
  ['INTERNAL', 'Internal'],
  ['SEA_FREIGHT', 'Sea Freight'],
  ['AIR_FREIGHT', 'Air Freight'],
  ['COURIER', 'Courier'],
  ['ROAD', 'Road Freight']
]

const ENTITY_OPTIONS = [
  ['', 'All entities'],
  ['MEMBER', 'Member'],
  ['USER', 'User'],
  ['ACCOUNT', 'Account'],
  ['SAVINGS_TRANSACTION', 'Savings Transaction'],
  ['SHARE_TRANSACTION', 'Share Transaction'],
  ['LOAN', 'Loan'],
  ['COLLATERAL', 'Collateral'],
  ['PRODUCT', 'Product'],
  ['PRODUCT_IMPORT', 'Product Import']
]

function formatMoney(value) {
  return `UGX ${Number(value || 0).toLocaleString()}`
}

function formatDateTime(value) {
  return value ? new Date(value).toLocaleString() : '-'
}

function emptyFilters() {
  return { fromDate: '', toDate: '', transactionType: '', channel: '' }
}

function Compliance() {
  const [tab, setTab] = useState('transactions')
  const [filters, setFilters] = useState(emptyFilters)
  const [appliedFilters, setAppliedFilters] = useState(emptyFilters)
  const [sheet, setSheet] = useState(null)
  const [auditLog, setAuditLog] = useState([])
  const [auditFilters, setAuditFilters] = useState({ action: '', entityType: '', limit: '100' })
  const [appliedAuditFilters, setAppliedAuditFilters] = useState({ action: '', entityType: '', limit: '100' })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [exporting, setExporting] = useState(null)

  useEffect(() => {
    loadCompliance()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [appliedFilters])

  useEffect(() => {
    if (tab === 'audit') {
      loadAuditLog()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab, appliedAuditFilters])

  async function loadCompliance() {
    setLoading(true)
    try {
      const sheetData = await api.getComplianceTransactions(appliedFilters)
      setSheet(sheetData)
      setError(null)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  async function loadAuditLog() {
    setLoading(true)
    try {
      const data = await api.getComplianceAuditLog(appliedAuditFilters)
      setAuditLog(data || [])
      setError(null)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  async function handleExport(scope, format) {
    const key = `${scope}-${format}`
    setExporting(key)
    setError(null)
    try {
      if (scope === 'transactions') {
        await api.exportComplianceTransactions(appliedFilters, format)
      } else {
        await api.exportComplianceAuditLog(appliedAuditFilters, format)
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setExporting(null)
    }
  }

  const exportScope = tab === 'audit' ? 'audit' : 'transactions'
  const exportFormats = [
    ['csv', 'Export CSV'],
    ['xlsx', 'Export Excel'],
    ['pdf', 'Sealed PDF']
  ]

  const rows = sheet?.rows || []

  const typeSummary = useMemo(() => {
    const summary = {}
    rows.forEach(row => {
      const key = row.source || 'OTHER'
      summary[key] = (summary[key] || 0) + Number(row.grossAmount || 0)
    })
    return summary
  }, [rows])

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Compliance & Audit</h2>
          <p className="page-subtitle">
            Read-only financial audit workspace: transaction spreadsheets and the audit trail
          </p>
        </div>
        <div className="header-actions">
          <span className="count-chip">Auditor restricted</span>
          <div className="export-button-group">
            {exportFormats.map(([format, label]) => (
              <button
                key={format}
                type="button"
                className="btn btn-ghost btn-sm"
                onClick={() => handleExport(exportScope, format)}
                disabled={exporting !== null}
              >
                {exporting === `${exportScope}-${format}` ? 'Preparing…' : label}
              </button>
            ))}
          </div>
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}

      <div className="tabs">
        <button className={`tab ${tab === 'transactions' ? 'active' : ''}`} onClick={() => setTab('transactions')}>
          Transaction Sheet
        </button>
        <button className={`tab ${tab === 'audit' ? 'active' : ''}`} onClick={() => setTab('audit')}>
          Audit Log
        </button>
      </div>

      {tab === 'transactions' && (
        <div className="card">
          <div className="card-heading">
            <div>
              <h3>Reporting Period</h3>
              <p className="card-subtitle">Leave dates blank to include the full ledger history.</p>
            </div>
          </div>
          <form
            className="filter-bar"
            onSubmit={event => {
              event.preventDefault()
              setAppliedFilters({ ...filters })
            }}
          >
            <div className="form-group">
              <label>From Date</label>
              <input
                type="date"
                value={filters.fromDate}
                onChange={e => setFilters({ ...filters, fromDate: e.target.value })}
              />
            </div>
            <div className="form-group">
              <label>To Date</label>
              <input
                type="date"
                value={filters.toDate}
                onChange={e => setFilters({ ...filters, toDate: e.target.value })}
              />
            </div>
            <div className="form-group">
              <label>Transaction Type</label>
              <select
                value={filters.transactionType}
                onChange={e => setFilters({ ...filters, transactionType: e.target.value })}
              >
                {TYPE_OPTIONS.map(([value, label]) => (
                  <option key={value || 'all'} value={value}>{label}</option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label>Channel</label>
              <select
                value={filters.channel}
                onChange={e => setFilters({ ...filters, channel: e.target.value })}
              >
                {CHANNEL_OPTIONS.map(([value, label]) => (
                  <option key={value || 'all'} value={value}>{label}</option>
                ))}
              </select>
            </div>
            <div className="filter-actions">
              <button type="submit" className="btn btn-primary btn-sm" disabled={loading}>
                {loading ? 'Loading...' : 'Apply Filters'}
              </button>
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                onClick={() => {
                  setFilters(emptyFilters())
                  setAppliedFilters(emptyFilters())
                }}
              >
                Reset
              </button>
            </div>
          </form>
        </div>
      )}

      {loading && <div className="loading">Loading compliance data...</div>}

      {!loading && tab === 'transactions' && (
        <>
          <div className="mini-stat-row">
            <div className="mini-stat">
              <span>Gross Amount</span>
              <strong>{formatMoney(sheet?.totalGross)}</strong>
            </div>
            <div className="mini-stat">
              <span>Net Amount</span>
              <strong>{formatMoney(sheet?.totalNet)}</strong>
            </div>
            <div className="mini-stat">
              <span>Transactions</span>
              <strong>{sheet?.count || 0}</strong>
            </div>
          </div>

          <div className="card">
            <div className="card-heading">
              <div>
                <h3>Detailed Transaction Spreadsheet</h3>
                <p className="card-subtitle">
                  Savings transactions, loan repayments, share transactions and import procurement in one ledger.
                </p>
              </div>
            </div>
            <div className="table-wrap">
              <table className="sheet-table">
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Reference</th>
                    <th>Source</th>
                    <th>Type</th>
                    <th>Channel</th>
                    <th>Member</th>
                    <th>Amount</th>
                    <th>Net Amount</th>
                    <th>Description</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.length === 0 ? (
                    <tr>
                      <td colSpan={9} className="empty-text">No transactions match the selected filters.</td>
                    </tr>
                  ) : rows.map((row, index) => (
                    <tr key={`${row.reference}-${index}`}>
                      <td>{row.date || '-'}</td>
                      <td className="mono-cell">{row.reference}</td>
                      <td><span className="badge badge-info">{row.source}</span></td>
                      <td>{row.transactionType}</td>
                      <td>{row.channel}</td>
                      <td>{row.memberName || '-'}</td>
                      <td className="numeric-cell">{formatMoney(row.grossAmount)}</td>
                      <td className="numeric-cell"><strong>{formatMoney(row.netAmount)}</strong></td>
                      <td className="description-cell">{row.description || '-'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {Object.keys(typeSummary).length > 0 && (
              <div className="source-summary">
                {Object.entries(typeSummary).map(([source, total]) => (
                  <span key={source} className="source-pill">
                    {source}: {formatMoney(total)}
                  </span>
                ))}
              </div>
            )}
          </div>
        </>
      )}

      {!loading && tab === 'audit' && (
        <div className="card">
          <div className="card-heading">
            <div>
              <h3>Financial Audit Trail</h3>
              <p className="card-subtitle">
                Immutable record of every sensitive action. General staff cannot edit or delete these entries.
              </p>
            </div>
          </div>
          <form
            className="filter-bar"
            onSubmit={event => {
              event.preventDefault()
              setAppliedAuditFilters({ ...auditFilters })
            }}
          >
            <div className="form-group">
              <label>Action Contains</label>
              <input
                value={auditFilters.action}
                onChange={e => setAuditFilters({ ...auditFilters, action: e.target.value })}
                placeholder="e.g. SAVINGS or PASSWORD"
              />
            </div>
            <div className="form-group">
              <label>Entity Type</label>
              <select
                value={auditFilters.entityType}
                onChange={e => setAuditFilters({ ...auditFilters, entityType: e.target.value })}
              >
                {ENTITY_OPTIONS.map(([value, label]) => (
                  <option key={value || 'all'} value={value}>{label}</option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label>Limit</label>
              <select
                value={auditFilters.limit}
                onChange={e => setAuditFilters({ ...auditFilters, limit: e.target.value })}
              >
                <option value="50">50 entries</option>
                <option value="100">100 entries</option>
                <option value="250">250 entries</option>
                <option value="500">500 entries</option>
              </select>
            </div>
            <div className="filter-actions">
              <button type="submit" className="btn btn-primary btn-sm">Apply Filters</button>
            </div>
          </form>

          <div className="table-wrap">
            <table className="sheet-table">
              <thead>
                <tr>
                  <th>Timestamp</th>
                  <th>Actor</th>
                  <th>Action</th>
                  <th>Entity</th>
                  <th>Entity ID</th>
                  <th>Reason / Details</th>
                  <th>Correlation ID</th>
                </tr>
              </thead>
              <tbody>
                {auditLog.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="empty-text">No audit events match the selected filters.</td>
                  </tr>
                ) : auditLog.map(event => (
                  <tr key={event.eventId}>
                    <td>{formatDateTime(event.timestamp)}</td>
                    <td>{event.actorId ?? 'system'}</td>
                    <td><span className="badge badge-warning">{event.action}</span></td>
                    <td>{event.entityType}</td>
                    <td>{event.entityId}</td>
                    <td className="description-cell">{event.reason || '-'}</td>
                    <td className="mono-cell small-cell">{event.correlationId || '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  )
}

export default Compliance
