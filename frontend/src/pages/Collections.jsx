import { useEffect, useMemo, useState } from 'react'
import { api } from '../services/api'

const STAGE_LABELS = {
  FIRST_DEMAND: 'First Demand',
  SECOND_DEMAND: 'Second Demand',
  FINAL_DEMAND: 'Final Demand',
  GUARANTOR_DEMAND: 'Guarantor Demand',
  LEGAL_NOTICE: 'Legal Notice'
}

const STAGE_BADGES = {
  FIRST_DEMAND: 'warning',
  SECOND_DEMAND: 'warning',
  FINAL_DEMAND: 'danger',
  GUARANTOR_DEMAND: 'danger',
  LEGAL_NOTICE: 'danger'
}

const DEMAND_STATUS_BADGES = {
  ISSUED: 'info',
  PARTIALLY_SETTLED: 'warning',
  SETTLED: 'success',
  CANCELLED: 'default',
  ESCALATED: 'danger'
}

const BUCKET_LABELS = {
  CURRENT: 'Current',
  '1-30': '1-30 days',
  '31-60': '31-60 days',
  '61-90': '61-90 days',
  '90+': '90+ days'
}

const ACTION_TYPES = [
  { value: 'PHONE_CALL', label: 'Phone call' },
  { value: 'SMS', label: 'SMS reminder' },
  { value: 'EMAIL', label: 'Email reminder' },
  { value: 'HOME_VISIT', label: 'Home visit' },
  { value: 'OFFICE_MEETING', label: 'Office meeting' },
  { value: 'GUARANTOR_CONTACT', label: 'Guarantor contact' },
  { value: 'PROMISE_TO_PAY', label: 'Promise to pay' },
  { value: 'LEGAL_REFERRAL', label: 'Legal referral' },
  { value: 'OTHER', label: 'Other' }
]

const ACTION_LABELS = {
  ...Object.fromEntries(ACTION_TYPES.map(item => [item.value, item.label])),
  DEMAND_ISSUED: 'Demand issued'
}

const OUTCOMES = [
  { value: 'CONTACTED', label: 'Contacted' },
  { value: 'NO_ANSWER', label: 'No answer' },
  { value: 'PROMISE_TO_PAY', label: 'Promise to pay' },
  { value: 'PAID', label: 'Paid' },
  { value: 'DISPUTED', label: 'Disputed' },
  { value: 'ESCALATED', label: 'Escalated' },
  { value: 'NOTE', label: 'Note only' }
]

const DELIVERY_METHODS = [
  { value: 'PRINT', label: 'Print / hand delivery' },
  { value: 'IN_PERSON', label: 'In person' },
  { value: 'EMAIL', label: 'Email' },
  { value: 'SMS', label: 'SMS' }
]

const DEMAND_STATUSES = ['ISSUED', 'PARTIALLY_SETTLED', 'SETTLED', 'CANCELLED', 'ESCALATED']

function formatMoney(value) {
  return `UGX ${Number(value || 0).toLocaleString()}`
}

function formatDate(value) {
  return value ? new Date(value).toLocaleDateString() : '-'
}

function formatDateTime(value) {
  return value ? new Date(value).toLocaleString() : '-'
}

function localDate(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function today() {
  return localDate(new Date())
}

function plusDays(days) {
  const date = new Date()
  date.setDate(date.getDate() + days)
  return localDate(date)
}

function emptyDemandForm(row) {
  return {
    stage: row?.suggestedStage || 'FIRST_DEMAND',
    dueDate: plusDays(14),
    deliveryMethod: 'PRINT',
    notes: ''
  }
}

function emptyActionForm() {
  return {
    actionType: 'PHONE_CALL',
    outcome: 'CONTACTED',
    promisedAmount: '',
    promisedDate: '',
    notes: ''
  }
}

function badgeClass(value, map, fallback = 'default') {
  return `badge badge-${map[value] || fallback}`
}

function Collections() {
  const role = localStorage.getItem('role') || 'MEMBER'
  const canAct = ['ADMIN', 'RECOVERY_ADMIN', 'MANAGER', 'TELLER', 'LOAN_OFFICER'].includes(role)
  const canIssueDemand = ['ADMIN', 'RECOVERY_ADMIN', 'MANAGER', 'LOAN_OFFICER'].includes(role)

  const [summary, setSummary] = useState(null)
  const [loans, setLoans] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [success, setSuccess] = useState(null)
  const [search, setSearch] = useState('')
  const [bucket, setBucket] = useState('ALL')

  const [selected, setSelected] = useState(null)
  const [detail, setDetail] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailTab, setDetailTab] = useState('overview')

  const [showDemandModal, setShowDemandModal] = useState(false)
  const [demandForm, setDemandForm] = useState(emptyDemandForm(null))
  const [demandSubmitting, setDemandSubmitting] = useState(false)

  const [showActionModal, setShowActionModal] = useState(false)
  const [actionForm, setActionForm] = useState(emptyActionForm())
  const [actionSubmitting, setActionSubmitting] = useState(false)
  const [statusSubmitting, setStatusSubmitting] = useState(null)

  useEffect(() => {
    loadData()
  }, [])

  async function loadData() {
    setLoading(true)
    setError(null)
    try {
      const [summaryData, loanData] = await Promise.all([
        api.getCollectionSummary(),
        api.getCollectionLoans()
      ])
      setSummary(summaryData)
      setLoans(loanData || [])
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  function flash(message) {
    setSuccess(message)
    window.setTimeout(() => setSuccess(null), 5000)
  }

  const filteredLoans = useMemo(() => {
    const query = search.trim().toLowerCase()
    return loans.filter(loan => {
      const bucketMatch = bucket === 'ALL' || loan.agingBucket === bucket
      const searchMatch = !query || [
        loan.loanNumber,
        loan.memberName,
        loan.membershipNo,
        loan.phone
      ].some(value => (value || '').toLowerCase().includes(query))
      return bucketMatch && searchMatch
    })
  }, [loans, bucket, search])

  async function openDetail(loan) {
    setSelected(loan)
    setDetail(null)
    setDetailTab('overview')
    setDetailLoading(true)
    setError(null)
    try {
      const data = await api.getCollectionDetail(loan.loanId)
      setDetail(data)
    } catch (err) {
      setError(err.message)
    } finally {
      setDetailLoading(false)
    }
  }

  function closeDetail() {
    setSelected(null)
    setDetail(null)
    setShowDemandModal(false)
    setShowActionModal(false)
  }

  async function refreshDetail() {
    if (!selected) return
    const [detailData, summaryData, loanData] = await Promise.all([
      api.getCollectionDetail(selected.loanId),
      api.getCollectionSummary(),
      api.getCollectionLoans()
    ])
    setDetail(detailData)
    setSummary(summaryData)
    setLoans(loanData || [])
    const refreshed = (loanData || []).find(loan => loan.loanId === selected.loanId)
    if (refreshed) setSelected(refreshed)
  }

  function openDemandModal() {
    setDemandForm(emptyDemandForm(selected))
    setShowDemandModal(true)
  }

  function openActionModal() {
    setActionForm(emptyActionForm())
    setShowActionModal(true)
  }

  async function submitDemand(event) {
    event.preventDefault()
    if (!selected) return
    setDemandSubmitting(true)
    setError(null)
    try {
      const created = await api.issueLoanDemand(selected.loanId, {
        stage: demandForm.stage,
        dueDate: demandForm.dueDate || null,
        deliveryMethod: demandForm.deliveryMethod,
        notes: demandForm.notes.trim() || null
      })
      setShowDemandModal(false)
      flash(`Demand ${created.demandNumber} issued for ${formatMoney(created.amountDemanded)}.`)
      await refreshDetail()
    } catch (err) {
      setError(err.message)
    } finally {
      setDemandSubmitting(false)
    }
  }

  async function submitAction(event) {
    event.preventDefault()
    if (!selected) return
    setActionSubmitting(true)
    setError(null)
    try {
      await api.recordCollectionAction(selected.loanId, {
        actionType: actionForm.actionType,
        outcome: actionForm.outcome,
        promisedAmount: actionForm.promisedAmount ? Number(actionForm.promisedAmount) : null,
        promisedDate: actionForm.promisedDate || null,
        notes: actionForm.notes.trim() || null
      })
      setShowActionModal(false)
      setActionForm(emptyActionForm())
      flash('Collection action recorded.')
      await refreshDetail()
    } catch (err) {
      setError(err.message)
    } finally {
      setActionSubmitting(false)
    }
  }

  async function downloadDemand(demand) {
    try {
      await api.downloadDemandPdf(selected.loanId, demand.demandId, demand.demandNumber)
    } catch (err) {
      setError(err.message)
    }
  }

  async function updateDemandStatus(demand, status) {
    setStatusSubmitting(demand.demandId)
    setError(null)
    try {
      await api.updateLoanDemandStatus(demand.demandId, { status })
      flash(`Demand ${demand.demandNumber} marked ${status.replaceAll('_', ' ').toLowerCase()}.`)
      await refreshDetail()
    } catch (err) {
      setError(err.message)
    } finally {
      setStatusSubmitting(null)
    }
  }

  const buckets = summary?.buckets || []

  return (
    <div className="collections-page">
      <div className="page-header dashboard-header">
        <div>
          <h2>Loan Collections &amp; Demands</h2>
          <p className="page-subtitle">
            Arrears aging, formal demand notices, promises-to-pay and the contact trail.
          </p>
        </div>
        <div className="header-actions">
          <button className="btn btn-secondary btn-sm" onClick={loadData} disabled={loading}>
            {loading ? 'Refreshing...' : 'Refresh'}
          </button>
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}
      {success && <div className="success-message">{success}</div>}

      <div className="collection-kpi-grid">
        <div className="collection-kpi collection-kpi-danger">
          <span>Total arrears</span>
          <strong>{formatMoney(summary?.totalArrears)}</strong>
          <small>{summary?.delinquentLoans || 0} delinquent loans</small>
        </div>
        <div className="collection-kpi">
          <span>Portfolio at risk</span>
          <strong>{Number(summary?.parPercent || 0).toFixed(2)}%</strong>
          <small>Arrears / outstanding</small>
        </div>
        <div className="collection-kpi">
          <span>Total outstanding</span>
          <strong>{formatMoney(summary?.totalOutstanding)}</strong>
          <small>{summary?.activeLoans || 0} active loans</small>
        </div>
        <div className="collection-kpi collection-kpi-warning">
          <span>Promises due</span>
          <strong>{summary?.promisesDue || 0}</strong>
          <small>{summary?.promisesOverdue || 0} already overdue</small>
        </div>
        <div className="collection-kpi">
          <span>Open demands</span>
          <strong>{summary?.openDemands || 0}</strong>
          <small>{summary?.demandsThisMonth || 0} issued this month</small>
        </div>
        <div className="collection-kpi collection-kpi-success">
          <span>Recovered this month</span>
          <strong>{formatMoney(summary?.recoveryThisMonth)}</strong>
          <small>Repayments posted</small>
        </div>
      </div>

      <div className="collection-aging-grid">
        {buckets.map(item => (
          <button
            key={item.bucket}
            type="button"
            className={`collection-aging-card ${bucket === item.bucket ? 'active' : ''}`}
            onClick={() => setBucket(bucket === item.bucket ? 'ALL' : item.bucket)}
          >
            <span>{item.label}</span>
            <strong>{formatMoney(item.amount)}</strong>
            <small>{item.loanCount} loan{item.loanCount === 1 ? '' : 's'}</small>
          </button>
        ))}
      </div>

      <div className="card">
        <div className="collection-filter-bar">
          <input
            className="search-input"
            type="search"
            placeholder="Search loan, member, membership no. or phone"
            value={search}
            onChange={event => setSearch(event.target.value)}
          />
          <select
            className="filter-select"
            value={bucket}
            onChange={event => setBucket(event.target.value)}
          >
            <option value="ALL">All aging buckets</option>
            {Object.entries(BUCKET_LABELS).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
          <span className="count-chip">{filteredLoans.length} loans</span>
        </div>

        {loading ? (
          <p className="loading">Loading collections...</p>
        ) : filteredLoans.length === 0 ? (
          <p className="empty-text">No loans match this filter.</p>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Loan</th>
                  <th>Member</th>
                  <th>Outstanding</th>
                  <th>Arrears</th>
                  <th>Penalty</th>
                  <th>Overdue</th>
                  <th>Bucket</th>
                  <th>Last action</th>
                  <th>Demands</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredLoans.map(loan => (
                  <tr key={loan.loanId}>
                    <td>
                      <strong>{loan.loanNumber}</strong>
                      <span className="cell-sub">{loan.productName}</span>
                    </td>
                    <td>
                      {loan.memberName}
                      <span className="cell-sub">{loan.membershipNo} · {loan.phone || 'No phone'}</span>
                    </td>
                    <td>{formatMoney(loan.totalOutstanding)}</td>
                    <td className="danger-text">{formatMoney(loan.amountInArrears)}</td>
                    <td>{formatMoney(loan.penaltyOutstanding)}</td>
                    <td>
                      <strong>{loan.daysOverdue}</strong>
                      <span className="cell-sub">days</span>
                    </td>
                    <td>
                      <span className={`badge badge-${loan.agingBucket === 'CURRENT' ? 'success' : loan.agingBucket === '90+' ? 'danger' : 'warning'}`}>
                        {BUCKET_LABELS[loan.agingBucket] || loan.agingBucket}
                      </span>
                      {loan.suggestedStage && (
                        <span className="cell-sub">{STAGE_LABELS[loan.suggestedStage]}</span>
                      )}
                    </td>
                    <td>
                      {loan.lastActionType ? ACTION_LABELS[loan.lastActionType] || loan.lastActionType : 'No contact yet'}
                      {loan.lastActionAt && <span className="cell-sub">{formatDateTime(loan.lastActionAt)}</span>}
                      {loan.promiseStatus && (
                        <span className={`badge badge-${loan.promiseStatus === 'OVERDUE' ? 'danger' : loan.promiseStatus === 'DUE' ? 'warning' : 'info'}`}>
                          Promise {loan.promiseStatus.toLowerCase()}
                        </span>
                      )}
                    </td>
                    <td>
                      <strong>{loan.demandsIssued}</strong>
                      {loan.lastDemandStage && (
                        <span className="cell-sub">{STAGE_LABELS[loan.lastDemandStage]}</span>
                      )}
                    </td>
                    <td>
                      <div className="collection-row-actions">
                        <button className="btn btn-secondary btn-sm" onClick={() => openDetail(loan)}>
                          Open
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {selected && (
        <div className="modal-overlay" onClick={closeDetail}>
          <div className="modal modal-wide collection-detail-modal" onClick={event => event.stopPropagation()}>
            <div className="modal-header">
              <div>
                <h3>{selected.loanNumber} - {selected.memberName}</h3>
                <p className="modal-subtitle">
                  {selected.productName} · {selected.membershipNo} · {selected.phone || 'No phone'}
                </p>
              </div>
              <button className="modal-close" onClick={closeDetail}>x</button>
            </div>
            <div className="modal-body">
              <div className="collection-detail-chips">
                <span className="badge badge-danger">Arrears {formatMoney(selected.amountInArrears)}</span>
                <span className="badge badge-warning">Penalty {formatMoney(selected.penaltyOutstanding)}</span>
                <span className="badge badge-info">{selected.daysOverdue} days overdue</span>
                <span className={`badge badge-${selected.agingBucket === 'CURRENT' ? 'success' : selected.agingBucket === '90+' ? 'danger' : 'warning'}`}>
                  {BUCKET_LABELS[selected.agingBucket] || selected.agingBucket}
                </span>
                {selected.promiseStatus && (
                  <span className={`badge badge-${selected.promiseStatus === 'OVERDUE' ? 'danger' : 'warning'}`}>
                    Promise {selected.promiseStatus.toLowerCase()}
                  </span>
                )}
              </div>

              <div className="tabs">
                <button className={`tab ${detailTab === 'overview' ? 'active' : ''}`} onClick={() => setDetailTab('overview')}>Overview</button>
                <button className={`tab ${detailTab === 'demands' ? 'active' : ''}`} onClick={() => setDetailTab('demands')}>Demands ({detail?.demands?.length || 0})</button>
                <button className={`tab ${detailTab === 'actions' ? 'active' : ''}`} onClick={() => setDetailTab('actions')}>Actions ({detail?.actions?.length || 0})</button>
              </div>

              {detailLoading ? (
                <p className="loading">Loading loan details...</p>
              ) : detail && (
                <>
                  {detailTab === 'overview' && (
                    <div className="tab-panel">
                      <div className="collection-info-grid">
                        <div className="info-row"><span>Outstanding principal</span><strong>{formatMoney(selected.outstandingPrincipal)}</strong></div>
                        <div className="info-row"><span>Outstanding interest</span><strong>{formatMoney(selected.outstandingInterest)}</strong></div>
                        <div className="info-row"><span>Principal arrears</span><strong>{formatMoney(selected.principalArrears)}</strong></div>
                        <div className="info-row"><span>Interest arrears</span><strong>{formatMoney(selected.interestArrears)}</strong></div>
                        <div className="info-row"><span>Penalty outstanding</span><strong>{formatMoney(selected.penaltyOutstanding)}</strong></div>
                        <div className="info-row"><span>Total demanded</span><strong>{formatMoney(selected.amountDemanded)}</strong></div>
                        <div className="info-row"><span>Next due date</span><strong>{formatDate(selected.nextDueDate)}</strong></div>
                        <div className="info-row"><span>Disbursed</span><strong>{formatDate(selected.disbursedAt)}</strong></div>
                        <div className="info-row"><span>Phone</span><strong>{selected.phone || '-'}</strong></div>
                        <div className="info-row"><span>Email</span><strong>{selected.email || '-'}</strong></div>
                      </div>

                      <h4 className="collection-section-title">Overdue installments</h4>
                      {(detail.schedule || []).filter(row => row.daysOverdue > 0 && Number(row.balance) > 0).length === 0 ? (
                        <p className="empty-text">No overdue installments.</p>
                      ) : (
                        <div className="table-wrap">
                          <table>
                            <thead>
                              <tr>
                                <th>#</th>
                                <th>Due date</th>
                                <th>Principal</th>
                                <th>Interest</th>
                                <th>Penalty</th>
                                <th>Balance</th>
                                <th>Days</th>
                              </tr>
                            </thead>
                            <tbody>
                              {(detail.schedule || [])
                                .filter(row => row.daysOverdue > 0 && Number(row.balance) > 0)
                                .map(row => (
                                  <tr key={row.scheduleId || row.installmentNo}>
                                    <td>{row.installmentNo}</td>
                                    <td>{formatDate(row.dueDate)}</td>
                                    <td>{formatMoney(Number(row.principalDue) - Number(row.principalPaid))}</td>
                                    <td>{formatMoney(Number(row.interestDue) - Number(row.interestPaid))}</td>
                                    <td>{formatMoney(Number(row.penaltyDue) - Number(row.penaltyPaid) + Number(row.accruedPenalty))}</td>
                                    <td><strong>{formatMoney(row.balance)}</strong></td>
                                    <td>{row.daysOverdue}</td>
                                  </tr>
                                ))}
                            </tbody>
                          </table>
                        </div>
                      )}

                      {(detail.guarantors || []).length > 0 && (
                        <>
                          <h4 className="collection-section-title">Guarantors</h4>
                          <div className="collection-guarantor-list">
                            {detail.guarantors.map(guarantor => (
                              <div key={guarantor.guarantorId} className="collection-guarantor">
                                <strong>{guarantor.memberName}</strong>
                                <span>{formatMoney(guarantor.guaranteedAmount)}</span>
                                <span className="badge badge-info">{guarantor.status}</span>
                              </div>
                            ))}
                          </div>
                        </>
                      )}

                      {(detail.repayments || []).length > 0 && (
                        <>
                          <h4 className="collection-section-title">Recent repayments</h4>
                          <div className="table-wrap">
                            <table>
                              <thead>
                                <tr>
                                  <th>Date</th>
                                  <th>Reference</th>
                                  <th>Amount</th>
                                  <th>Principal</th>
                                  <th>Interest</th>
                                  <th>Penalty</th>
                                </tr>
                              </thead>
                              <tbody>
                                {detail.repayments.slice(0, 6).map(repayment => (
                                  <tr key={repayment.repaymentId}>
                                    <td>{formatDate(repayment.paymentDate)}</td>
                                    <td>{repayment.reference}</td>
                                    <td>{formatMoney(repayment.amount)}</td>
                                    <td>{formatMoney(repayment.principalPortion)}</td>
                                    <td>{formatMoney(repayment.interestPortion)}</td>
                                    <td>{formatMoney(repayment.penaltyPortion)}</td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        </>
                      )}
                    </div>
                  )}

                  {detailTab === 'demands' && (
                    <div className="tab-panel">
                      <div className="collection-tab-header">
                        <p className="page-subtitle">Formal notices issued against this loan. Each letter is sealed and can be reprinted.</p>
                        {canIssueDemand && (
                          <button className="btn btn-primary btn-sm" onClick={openDemandModal}>Issue demand</button>
                        )}
                      </div>
                      {(detail.demands || []).length === 0 ? (
                        <p className="empty-text">No demand notices have been issued.</p>
                      ) : (
                        <div className="table-wrap">
                          <table>
                            <thead>
                              <tr>
                                <th>Demand</th>
                                <th>Stage</th>
                                <th>Amount</th>
                                <th>Due</th>
                                <th>Status</th>
                                <th>Issued</th>
                                <th>Actions</th>
                              </tr>
                            </thead>
                            <tbody>
                              {(detail.demands || []).map(demand => (
                                <tr key={demand.demandId}>
                                  <td>
                                    <strong>{demand.demandNumber}</strong>
                                    <span className="cell-sub">Seal {demand.documentSeal?.slice(0, 12)}...</span>
                                  </td>
                                  <td>
                                    <span className={badgeClass(demand.stage, STAGE_BADGES)}>
                                      {STAGE_LABELS[demand.stage] || demand.stage}
                                    </span>
                                  </td>
                                  <td>{formatMoney(demand.amountDemanded)}</td>
                                  <td>{formatDate(demand.dueDate)}</td>
                                  <td>
                                    <span className={badgeClass(demand.status, DEMAND_STATUS_BADGES)}>
                                      {demand.status.replaceAll('_', ' ')}
                                    </span>
                                  </td>
                                  <td>
                                    {formatDateTime(demand.issuedAt)}
                                    <span className="cell-sub">by {demand.issuedByName || 'System'}</span>
                                  </td>
                                  <td>
                                    <div className="collection-row-actions">
                                      <button className="btn btn-secondary btn-sm" onClick={() => downloadDemand(demand)}>
                                        PDF
                                      </button>
                                      {canIssueDemand && (
                                        <select
                                          className="filter-select collection-status-select"
                                          value={demand.status}
                                          disabled={statusSubmitting === demand.demandId}
                                          onChange={event => updateDemandStatus(demand, event.target.value)}
                                        >
                                          {DEMAND_STATUSES.map(status => (
                                            <option key={status} value={status}>{status.replaceAll('_', ' ')}</option>
                                          ))}
                                        </select>
                                      )}
                                    </div>
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      )}
                    </div>
                  )}

                  {detailTab === 'actions' && (
                    <div className="tab-panel">
                      <div className="collection-tab-header">
                        <p className="page-subtitle">Every call, visit, promise and escalation is recorded here.</p>
                        {canAct && (
                          <button className="btn btn-primary btn-sm" onClick={openActionModal}>Log action</button>
                        )}
                      </div>
                      {(detail.actions || []).length === 0 ? (
                        <p className="empty-text">No collection actions recorded yet.</p>
                      ) : (
                        <div className="collection-timeline">
                          {(detail.actions || []).map(action => (
                            <div key={action.actionId} className="collection-timeline-item">
                              <div className="collection-timeline-dot" />
                              <div className="collection-timeline-body">
                                <div className="collection-timeline-head">
                                  <strong>{ACTION_LABELS[action.actionType] || action.actionType}</strong>
                                  <span className={`badge badge-${action.outcome === 'ESCALATED' ? 'danger' : action.outcome === 'PAID' ? 'success' : 'info'}`}>
                                    {action.outcome.replaceAll('_', ' ')}
                                  </span>
                                  <span className="cell-sub">{formatDateTime(action.createdAt)} · {action.actorName || 'System'}</span>
                                </div>
                                {action.notes && <p>{action.notes}</p>}
                                {action.promisedAmount && (
                                  <p className="collection-promise">
                                    Promise: {formatMoney(action.promisedAmount)} by {formatDate(action.promisedDate)}
                                  </p>
                                )}
                              </div>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  )}
                </>
              )}
            </div>
          </div>
        </div>
      )}

      {showDemandModal && selected && (
        <div className="modal-overlay" onClick={() => setShowDemandModal(false)}>
          <div className="modal" onClick={event => event.stopPropagation()}>
            <div className="modal-header">
              <h3>Issue demand notice - {selected.loanNumber}</h3>
              <button className="modal-close" onClick={() => setShowDemandModal(false)}>x</button>
            </div>
            <form className="modal-body" onSubmit={submitDemand}>
              <div className="collection-demand-summary">
                <div><span>Principal arrears</span><strong>{formatMoney(selected.principalArrears)}</strong></div>
                <div><span>Interest arrears</span><strong>{formatMoney(selected.interestArrears)}</strong></div>
                <div><span>Penalty</span><strong>{formatMoney(selected.penaltyOutstanding)}</strong></div>
                <div><span>Amount to demand</span><strong className="danger-text">{formatMoney(selected.amountDemanded)}</strong></div>
              </div>
              <div className="form-group">
                <label>Demand stage</label>
                <select value={demandForm.stage} onChange={event => setDemandForm({ ...demandForm, stage: event.target.value })}>
                  {Object.entries(STAGE_LABELS).map(([value, label]) => (
                    <option key={value} value={value}>{label}</option>
                  ))}
                </select>
              </div>
              <div className="grid-2">
                <div className="form-group">
                  <label>Payment deadline</label>
                  <input
                    type="date"
                    min={today()}
                    value={demandForm.dueDate}
                    onChange={event => setDemandForm({ ...demandForm, dueDate: event.target.value })}
                    required
                  />
                </div>
                <div className="form-group">
                  <label>Delivery method</label>
                  <select value={demandForm.deliveryMethod} onChange={event => setDemandForm({ ...demandForm, deliveryMethod: event.target.value })}>
                    {DELIVERY_METHODS.map(method => (
                      <option key={method.value} value={method.value}>{method.label}</option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="form-group">
                <label>Notes</label>
                <textarea
                  rows="3"
                  value={demandForm.notes}
                  onChange={event => setDemandForm({ ...demandForm, notes: event.target.value })}
                  placeholder="Optional instructions for the collections file"
                />
              </div>
              <p className="collection-form-note">
                The notice snapshots the current arrears and penalties. The member's guarantors and collateral
                are listed in the printable letter.
              </p>
              <div className="modal-actions">
                <button type="button" className="btn btn-secondary" onClick={() => setShowDemandModal(false)}>Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={demandSubmitting}>
                  {demandSubmitting ? 'Issuing...' : 'Issue demand'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {showActionModal && selected && (
        <div className="modal-overlay" onClick={() => setShowActionModal(false)}>
          <div className="modal" onClick={event => event.stopPropagation()}>
            <div className="modal-header">
              <h3>Log collection action - {selected.loanNumber}</h3>
              <button className="modal-close" onClick={() => setShowActionModal(false)}>x</button>
            </div>
            <form className="modal-body" onSubmit={submitAction}>
              <div className="grid-2">
                <div className="form-group">
                  <label>Action type</label>
                  <select value={actionForm.actionType} onChange={event => setActionForm({ ...actionForm, actionType: event.target.value })}>
                    {ACTION_TYPES.map(type => (
                      <option key={type.value} value={type.value}>{type.label}</option>
                    ))}
                  </select>
                </div>
                <div className="form-group">
                  <label>Outcome</label>
                  <select value={actionForm.outcome} onChange={event => setActionForm({ ...actionForm, outcome: event.target.value })}>
                    {OUTCOMES.map(outcome => (
                      <option key={outcome.value} value={outcome.value}>{outcome.label}</option>
                    ))}
                  </select>
                </div>
              </div>
              {actionForm.actionType === 'PROMISE_TO_PAY' && (
                <div className="grid-2">
                  <div className="form-group">
                    <label>Promised amount</label>
                    <input
                      type="number"
                      min="1"
                      step="0.01"
                      value={actionForm.promisedAmount}
                      onChange={event => setActionForm({ ...actionForm, promisedAmount: event.target.value })}
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label>Promised date</label>
                    <input
                      type="date"
                      min={today()}
                      value={actionForm.promisedDate}
                      onChange={event => setActionForm({ ...actionForm, promisedDate: event.target.value })}
                      required
                    />
                  </div>
                </div>
              )}
              <div className="form-group">
                <label>Notes</label>
                <textarea
                  rows="4"
                  value={actionForm.notes}
                  onChange={event => setActionForm({ ...actionForm, notes: event.target.value })}
                  placeholder="Who was contacted, what was agreed, next step"
                />
              </div>
              <div className="modal-actions">
                <button type="button" className="btn btn-secondary" onClick={() => setShowActionModal(false)}>Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={actionSubmitting}>
                  {actionSubmitting ? 'Saving...' : 'Record action'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}

export default Collections
