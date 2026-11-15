import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api } from '../services/api'

const STATUS_LABELS = {
  PENDING: 'Pending',
  UNDER_REVIEW: 'Under Review',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  DISBURSED: 'Disbursed',
  IN_ARREARS: 'In Arrears',
  CLOSED: 'Closed'
}

const STATUS_BADGES = {
  PENDING: 'warning',
  UNDER_REVIEW: 'info',
  APPROVED: 'info',
  REJECTED: 'danger',
  DISBURSED: 'success',
  IN_ARREARS: 'danger',
  CLOSED: 'default'
}

const METHOD_LABELS = {
  REDUCING_BALANCE: 'Reducing Balance',
  FLAT: 'Flat'
}

const ACTIVE_STATUSES = ['DISBURSED', 'IN_ARREARS']

const DEMAND_STAGE_LABELS = {
  FIRST_DEMAND: 'First Demand',
  SECOND_DEMAND: 'Second Demand',
  FINAL_DEMAND: 'Final Demand',
  GUARANTOR_DEMAND: 'Guarantor Demand',
  LEGAL_NOTICE: 'Legal Notice'
}

const DEMAND_STATUS_BADGES = {
  ISSUED: 'info',
  PARTIALLY_SETTLED: 'warning',
  SETTLED: 'success',
  CANCELLED: 'default',
  ESCALATED: 'danger'
}

function formatMoney(value) {
  return `UGX ${Number(value || 0).toLocaleString()}`
}

function formatDate(value) {
  return value ? new Date(value).toLocaleDateString() : '—'
}

function formatDateTime(value) {
  return value ? new Date(value).toLocaleString() : '—'
}

function today() {
  const date = new Date()
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function emptyApplicationForm() {
  return {
    memberId: '',
    productId: '',
    amount: '',
    termMonths: '',
    purpose: '',
    guarantors: []
  }
}

function emptyProductForm() {
  return {
    name: '',
    description: '',
    interestRate: '',
    interestMethod: 'REDUCING_BALANCE',
    maxTermMonths: 12,
    minAmount: 0,
    maxAmount: '',
    maxMultiplier: 3,
    penaltyRate: 2,
    requiresGuarantor: false
  }
}

function Loans() {
  const [searchParams] = useSearchParams()
  const role = localStorage.getItem('role') || 'MEMBER'
  const canReview = ['ADMIN', 'MANAGER', 'LOAN_OFFICER'].includes(role)
  const canApprove = ['ADMIN', 'MANAGER'].includes(role)
  const canDisburse = ['ADMIN', 'MANAGER'].includes(role)
  const canApply = ['ADMIN', 'MANAGER', 'LOAN_OFFICER', 'MEMBER'].includes(role)
  const canRepay = ['ADMIN', 'MANAGER', 'TELLER', 'LOAN_OFFICER'].includes(role)
  const canConfig = ['ADMIN', 'MANAGER'].includes(role)

  const [loans, setLoans] = useState([])
  const [products, setProducts] = useState([])
  const [members, setMembers] = useState([])
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)
  const [success, setSuccess] = useState(null)
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [showApplicationForm, setShowApplicationForm] = useState(searchParams.get('new') === '1')
  const [showProductForm, setShowProductForm] = useState(false)
  const [applicationForm, setApplicationForm] = useState(emptyApplicationForm())
  const [productForm, setProductForm] = useState(emptyProductForm())
  const [eligibility, setEligibility] = useState(null)
  const [eligibilityLoading, setEligibilityLoading] = useState(false)

  const [selectedLoan, setSelectedLoan] = useState(null)
  const [detailTab, setDetailTab] = useState('overview')
  const [schedule, setSchedule] = useState([])
  const [repayments, setRepayments] = useState([])
  const [demands, setDemands] = useState([])
  const [detailLoading, setDetailLoading] = useState(false)
  const [repaymentForm, setRepaymentForm] = useState({ amount: '', paymentDate: today(), notes: '' })
  const [repaymentSubmitting, setRepaymentSubmitting] = useState(false)

  useEffect(() => {
    loadData()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function loadData() {
    setLoading(true)
    try {
      const [loanData, productData, memberData] = await Promise.all([
        api.getLoans().catch(() => []),
        api.getActiveLoanProducts().catch(() => []),
        api.getMembers().catch(() => [])
      ])
      setLoans(loanData || [])
      setProducts(productData || [])
      setMembers(memberData || [])
      setError(null)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  const memberMap = useMemo(() => {
    const map = {}
    members.forEach(member => {
      map[member.memberId] = `${member.firstName} ${member.lastName} (${member.membershipNo})`
    })
    return map
  }, [members])

  const activeMembers = useMemo(
    () => members.filter(member => member.status === 'ACTIVE'),
    [members]
  )

  const selectedProduct = useMemo(
    () => products.find(product => String(product.productId) === String(applicationForm.productId)),
    [products, applicationForm.productId]
  )

  const summary = useMemo(() => {
    const active = loans.filter(loan => ACTIVE_STATUSES.includes(loan.status))
    const outstanding = active.reduce(
      (total, loan) => total + Number(loan.outstandingPrincipal || 0) + Number(loan.outstandingInterest || 0),
      0
    )
    const arrears = active.reduce((total, loan) => total + Number(loan.amountInArrears || 0), 0)
    const pending = loans.filter(loan => ['PENDING', 'UNDER_REVIEW', 'APPROVED'].includes(loan.status)).length
    return { activeCount: active.length, outstanding, arrears, pending }
  }, [loans])

  const filteredLoans = useMemo(() => {
    const query = search.trim().toLowerCase()
    return loans.filter(loan => {
      const haystack = [
        loan.loanNumber,
        loan.memberName,
        loan.membershipNo,
        loan.productName,
        loan.status
      ].filter(Boolean).join(' ').toLowerCase()
      return (!query || haystack.includes(query))
        && (statusFilter === 'ALL' || loan.status === statusFilter)
    })
  }, [loans, search, statusFilter])

  function flashSuccess(message) {
    setSuccess(message)
    window.setTimeout(() => setSuccess(null), 4000)
  }

  async function handleCreateProduct(event) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const payload = {
        ...productForm,
        interestRate: Number(productForm.interestRate),
        maxTermMonths: Number(productForm.maxTermMonths),
        minAmount: Number(productForm.minAmount || 0),
        maxAmount: productForm.maxAmount === '' ? null : Number(productForm.maxAmount),
        maxMultiplier: Number(productForm.maxMultiplier || 3),
        penaltyRate: Number(productForm.penaltyRate || 0)
      }
      const created = await api.createLoanProduct(payload)
      flashSuccess(`Loan product "${created.name}" created.`)
      setShowProductForm(false)
      setProductForm(emptyProductForm())
      await loadData()
    } catch (err) {
      setError(err.message)
    } finally {
      setSubmitting(false)
    }
  }

  function updateApplicationField(field, value) {
    setApplicationForm(prev => {
      const next = { ...prev, [field]: value }
      if (field === 'productId') {
        const product = products.find(item => String(item.productId) === String(value))
        if (product) {
          next.termMonths = String(product.maxTermMonths)
          next.guarantors = Boolean(product.requiresGuarantor)
            ? [{ memberId: '', guaranteedAmount: prev.amount || '' }]
            : []
        }
      }
      return next
    })
  }

  function addGuarantorRow() {
    setApplicationForm(prev => ({
      ...prev,
      guarantors: [...prev.guarantors, { memberId: '', guaranteedAmount: '' }]
    }))
  }

  function updateGuarantor(index, field, value) {
    setApplicationForm(prev => ({
      ...prev,
      guarantors: prev.guarantors.map((row, rowIndex) =>
        rowIndex === index ? { ...row, [field]: value } : row
      )
    }))
  }

  function removeGuarantor(index) {
    setApplicationForm(prev => ({
      ...prev,
      guarantors: prev.guarantors.filter((_, rowIndex) => rowIndex !== index)
    }))
  }

  async function checkEligibility() {
    if (!applicationForm.memberId || !applicationForm.productId) {
      setEligibility(null)
      return
    }
    setEligibilityLoading(true)
    try {
      const data = await api.checkLoanEligibility(
        applicationForm.memberId,
        applicationForm.productId,
        applicationForm.amount || undefined
      )
      setEligibility(data)
    } catch (err) {
      setEligibility(null)
      setError(err.message)
    } finally {
      setEligibilityLoading(false)
    }
  }

  useEffect(() => {
    if (showApplicationForm) {
      checkEligibility()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [applicationForm.memberId, applicationForm.productId, applicationForm.amount, showApplicationForm])

  async function handleApply(event) {
    event.preventDefault()
    setError(null)
    if (!applicationForm.memberId || !applicationForm.productId) {
      setError('Select a member and a loan product.')
      return
    }
    const guarantors = applicationForm.guarantors
      .filter(row => row.memberId && row.guaranteedAmount)
      .map(row => ({
        memberId: Number(row.memberId),
        guaranteedAmount: Number(row.guaranteedAmount)
      }))
    setSubmitting(true)
    try {
      const created = await api.applyLoan({
        memberId: Number(applicationForm.memberId),
        productId: Number(applicationForm.productId),
        amount: Number(applicationForm.amount),
        termMonths: Number(applicationForm.termMonths),
        purpose: applicationForm.purpose.trim() || null,
        guarantors
      })
      flashSuccess(`Loan application ${created.loanNumber} submitted for review.`)
      setShowApplicationForm(false)
      setApplicationForm(emptyApplicationForm())
      setEligibility(null)
      await loadData()
    } catch (err) {
      setError(err.message)
    } finally {
      setSubmitting(false)
    }
  }

  async function handleReview(loan) {
    setError(null)
    try {
      const updated = await api.reviewLoan(loan.loanId, {})
      flashSuccess(`Loan ${updated.loanNumber} moved to under review.`)
      await loadData()
      if (selectedLoan?.loanId === loan.loanId) {
        await openDetail(loan.loanId)
      }
    } catch (err) {
      setError(err.message)
    }
  }

  async function handleApprove(loan) {
    setError(null)
    try {
      const updated = await api.approveLoan(loan.loanId)
      flashSuccess(`Loan ${updated.loanNumber} approved.`)
      await loadData()
      if (selectedLoan?.loanId === loan.loanId) {
        await openDetail(loan.loanId)
      }
    } catch (err) {
      setError(err.message)
    }
  }

  async function handleReject(loan) {
    const reason = window.prompt(`Reason for rejecting loan ${loan.loanNumber}:`)
    if (!reason || !reason.trim()) return
    setError(null)
    try {
      await api.rejectLoan(loan.loanId, { reason: reason.trim() })
      flashSuccess(`Loan ${loan.loanNumber} rejected.`)
      await loadData()
      if (selectedLoan?.loanId === loan.loanId) {
        await openDetail(loan.loanId)
      }
    } catch (err) {
      setError(err.message)
    }
  }

  async function handleDisburse(loan) {
    setError(null)
    try {
      const updated = await api.disburseLoan(loan.loanId, {})
      flashSuccess(`Loan ${updated.loanNumber} disbursed. Repayment schedule generated.`)
      await loadData()
      if (selectedLoan?.loanId === loan.loanId) {
        await openDetail(loan.loanId)
      }
    } catch (err) {
      setError(err.message)
    }
  }

  async function openDetail(loanId) {
    setDetailLoading(true)
    setDetailTab('overview')
    setRepaymentForm({ amount: '', paymentDate: today(), notes: '' })
    try {
      const [loanData, scheduleData, repaymentData, demandData] = await Promise.all([
        api.getLoan(loanId),
        api.getLoanSchedule(loanId).catch(() => []),
        api.getLoanRepayments(loanId).catch(() => []),
        api.getLoanDemands(loanId).catch(() => [])
      ])
      setSelectedLoan(loanData)
      setSchedule(scheduleData || [])
      setRepayments(repaymentData || [])
      setDemands(demandData || [])
    } catch (err) {
      setError(err.message)
    } finally {
      setDetailLoading(false)
    }
  }

  function closeDetail() {
    setSelectedLoan(null)
    setSchedule([])
    setRepayments([])
    setDemands([])
  }

  async function handleRepayment(event) {
    event.preventDefault()
    if (!selectedLoan) return
    setRepaymentSubmitting(true)
    setError(null)
    try {
      const repayment = await api.repayLoan(selectedLoan.loanId, {
        amount: Number(repaymentForm.amount),
        paymentDate: repaymentForm.paymentDate || null,
        notes: repaymentForm.notes.trim() || null
      })
      flashSuccess(`Repayment ${repayment.reference} posted successfully.`)
      setRepaymentForm({ amount: '', paymentDate: today(), notes: '' })
      await loadData()
      await openDetail(selectedLoan.loanId)
    } catch (err) {
      setError(err.message)
    } finally {
      setRepaymentSubmitting(false)
    }
  }

  function renderActions(loan) {
    const actions = []
    if (loan.status === 'PENDING' && canReview) {
      actions.push(<button key="review" type="button" className="link-btn" onClick={() => handleReview(loan)}>Review</button>)
    }
    if (['PENDING', 'UNDER_REVIEW'].includes(loan.status) && canApprove) {
      actions.push(<button key="approve" type="button" className="link-btn" onClick={() => handleApprove(loan)}>Approve</button>)
    }
    if (loan.status === 'APPROVED' && canDisburse) {
      actions.push(<button key="disburse" type="button" className="link-btn" onClick={() => handleDisburse(loan)}>Disburse</button>)
    }
    if (!['REJECTED', 'CLOSED'].includes(loan.status) && canApprove) {
      actions.push(<button key="reject" type="button" className="link-btn danger-link" onClick={() => handleReject(loan)}>Reject</button>)
    }
    actions.push(<button key="view" type="button" className="link-btn" onClick={() => openDetail(loan.loanId)}>View</button>)
    return <div className="doc-actions">{actions}</div>
  }

  const pendingCount = loans.filter(loan => ['PENDING', 'UNDER_REVIEW'].includes(loan.status)).length

  return (
    <div>
      <div className="page-header dashboard-header">
        <div>
          <h2>Loans & Credit</h2>
          <p className="page-subtitle">Applications, approval workflow, disbursement, amortization and repayments</p>
        </div>
        <div className="header-actions">
          {pendingCount > 0 && <span className="count-chip">{pendingCount} awaiting action</span>}
          {canConfig && (
            <button className="btn btn-secondary" onClick={() => setShowProductForm(!showProductForm)}>
              {showProductForm ? 'Cancel Product' : '+ New Product'}
            </button>
          )}
          {canApply && (
            <button className="btn btn-primary" onClick={() => setShowApplicationForm(!showApplicationForm)}>
              {showApplicationForm ? 'Cancel Application' : '+ New Application'}
            </button>
          )}
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}
      {success && <div className="success-message">{success}</div>}

      <div className="collateral-summary loan-summary">
        <div className="collateral-summary-card">
          <span>Loan Portfolio Outstanding</span>
          <strong>{formatMoney(summary.outstanding)}</strong>
          <small>Principal + interest on active loans</small>
        </div>
        <div className="collateral-summary-card">
          <span>Active Loans</span>
          <strong>{summary.activeCount}</strong>
          <small>Disbursed and running</small>
        </div>
        <div className="collateral-summary-card">
          <span>Awaiting Action</span>
          <strong>{summary.pending}</strong>
          <small>Pending, under review or approved</small>
        </div>
        <div className="collateral-summary-card">
          <span>Arrears</span>
          <strong>{formatMoney(summary.arrears)}</strong>
          <small>Overdue installments across all loans</small>
        </div>
      </div>

      {showProductForm && (
        <div className="card">
          <div className="card-heading">
            <div>
              <h3>Create Loan Product</h3>
              <p className="card-subtitle">Define pricing, limits and eligibility multipliers</p>
            </div>
            <button type="button" className="modal-close" onClick={() => setShowProductForm(false)}>×</button>
          </div>
          <form onSubmit={handleCreateProduct}>
            <div className="grid-2">
              <div className="form-group">
                <label>Product Name *</label>
                <input
                  value={productForm.name}
                  onChange={e => setProductForm({ ...productForm, name: e.target.value })}
                  placeholder="e.g. Business Growth Loan"
                  required
                />
              </div>
              <div className="form-group">
                <label>Description</label>
                <input
                  value={productForm.description}
                  onChange={e => setProductForm({ ...productForm, description: e.target.value })}
                  placeholder="Optional description"
                />
              </div>
            </div>
            <div className="grid-2">
              <div className="form-group">
                <label>Annual Interest Rate (%) *</label>
                <input
                  type="number"
                  min="0"
                  step="0.001"
                  value={productForm.interestRate}
                  onChange={e => setProductForm({ ...productForm, interestRate: e.target.value })}
                  required
                />
              </div>
              <div className="form-group">
                <label>Interest Method</label>
                <select
                  value={productForm.interestMethod}
                  onChange={e => setProductForm({ ...productForm, interestMethod: e.target.value })}
                >
                  <option value="REDUCING_BALANCE">Reducing Balance</option>
                  <option value="FLAT">Flat</option>
                </select>
              </div>
            </div>
            <div className="grid-2">
              <div className="form-group">
                <label>Maximum Term (months) *</label>
                <input
                  type="number"
                  min="1"
                  value={productForm.maxTermMonths}
                  onChange={e => setProductForm({ ...productForm, maxTermMonths: e.target.value })}
                  required
                />
              </div>
              <div className="form-group">
                <label>Maximum Amount (UGX)</label>
                <input
                  type="number"
                  min="0"
                  value={productForm.maxAmount}
                  onChange={e => setProductForm({ ...productForm, maxAmount: e.target.value })}
                  placeholder="Leave blank for no cap"
                />
              </div>
            </div>
            <div className="grid-2">
              <div className="form-group">
                <label>Minimum Amount (UGX)</label>
                <input
                  type="number"
                  min="0"
                  value={productForm.minAmount}
                  onChange={e => setProductForm({ ...productForm, minAmount: e.target.value })}
                />
              </div>
              <div className="form-group">
                <label>Maximum Multiplier (× savings + shares)</label>
                <input
                  type="number"
                  min="0.01"
                  step="0.01"
                  value={productForm.maxMultiplier}
                  onChange={e => setProductForm({ ...productForm, maxMultiplier: e.target.value })}
                />
              </div>
            </div>
            <div className="grid-2">
              <div className="form-group">
                <label>Penalty Rate (% per month on overdue)</label>
                <input
                  type="number"
                  min="0"
                  step="0.001"
                  value={productForm.penaltyRate}
                  onChange={e => setProductForm({ ...productForm, penaltyRate: e.target.value })}
                />
              </div>
              <div className="form-group">
                <label>Guarantor Requirement</label>
                <select
                  value={productForm.requiresGuarantor ? 'YES' : 'NO'}
                  onChange={e => setProductForm({ ...productForm, requiresGuarantor: e.target.value === 'YES' })}
                >
                  <option value="NO">No guarantor required</option>
                  <option value="YES">At least one guarantor required</option>
                </select>
              </div>
            </div>
            <div className="form-actions">
              <button type="button" className="btn btn-ghost" onClick={() => setShowProductForm(false)}>Cancel</button>
              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting ? 'Creating...' : 'Create Product'}
              </button>
            </div>
          </form>
        </div>
      )}

      {showApplicationForm && (
        <div className="card">
          <div className="card-heading">
            <div>
              <h3>New Loan Application</h3>
              <p className="card-subtitle">Capture member, product, term and guarantors</p>
            </div>
            <button type="button" className="modal-close" onClick={() => setShowApplicationForm(false)}>×</button>
          </div>
          <form onSubmit={handleApply}>
            <div className="grid-2">
              <div className="form-group">
                <label>Member *</label>
                <select
                  value={applicationForm.memberId}
                  onChange={e => updateApplicationField('memberId', e.target.value)}
                  required
                >
                  <option value="">Select member...</option>
                  {activeMembers.map(member => (
                    <option key={member.memberId} value={member.memberId}>
                      {member.firstName} {member.lastName} ({member.membershipNo})
                    </option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label>Loan Product *</label>
                <select
                  value={applicationForm.productId}
                  onChange={e => updateApplicationField('productId', e.target.value)}
                  required
                >
                  <option value="">Select product...</option>
                  {products.map(product => (
                    <option key={product.productId} value={product.productId}>
                      {product.name} — {product.interestRate}% {METHOD_LABELS[product.interestMethod] || product.interestMethod}
                    </option>
                  ))}
                </select>
                {selectedProduct && (
                  <p className="field-hint">
                    Max {selectedProduct.maxTermMonths} months, up to {selectedProduct.maxMultiplier}× savings + shares.
                  </p>
                )}
              </div>
            </div>

            <div className="grid-2">
              <div className="form-group">
                <label>Amount (UGX) *</label>
                <input
                  type="number"
                  min="1"
                  value={applicationForm.amount}
                  onChange={e => updateApplicationField('amount', e.target.value)}
                  required
                />
              </div>
              <div className="form-group">
                <label>Term (months) *</label>
                <input
                  type="number"
                  min="1"
                  max={selectedProduct?.maxTermMonths || undefined}
                  value={applicationForm.termMonths}
                  onChange={e => updateApplicationField('termMonths', e.target.value)}
                  required
                />
              </div>
            </div>

            <div className="form-group">
              <label>Purpose</label>
              <input
                value={applicationForm.purpose}
                onChange={e => updateApplicationField('purpose', e.target.value)}
                placeholder="What will the loan be used for?"
              />
            </div>

            {eligibilityLoading && <div className="loading">Checking member eligibility...</div>}
            {eligibility && !eligibilityLoading && (
              <div className="approval-panel">
                <h4>Eligibility Check</h4>
                <div className="profile-stats">
                  <div><span>Savings</span><strong>{formatMoney(eligibility.savingsBalance)}</strong></div>
                  <div><span>Shares</span><strong>{formatMoney(eligibility.shareValue)}</strong></div>
                  <div><span>Maximum Eligible</span><strong>{formatMoney(eligibility.maximumEligible)}</strong></div>
                  <div>
                    <span>Status</span>
                    <strong>
                      <span className={`badge badge-${eligibility.eligible ? 'success' : 'danger'}`}>
                        {eligibility.eligible ? 'Eligible' : 'Not eligible'}
                      </span>
                    </strong>
                  </div>
                </div>
                {(eligibility.reasons || []).length > 0 && (
                  <ul className="eligibility-reasons">
                    {eligibility.reasons.map(reason => <li key={reason}>{reason}</li>)}
                  </ul>
                )}
              </div>
            )}

            <h4 className="section-title">Guarantors</h4>
            {applicationForm.guarantors.length === 0 ? (
              <p className="field-hint">
                {selectedProduct?.requiresGuarantor
                  ? 'This product requires at least one guarantor covering the loan amount.'
                  : 'No guarantors added. Add one if required by policy.'}
              </p>
            ) : (
              applicationForm.guarantors.map((row, index) => (
                <div className="guarantor-row" key={`guarantor-${index}`}>
                  <div className="form-group">
                    <label>Guarantor Member</label>
                    <select
                      value={row.memberId}
                      onChange={e => updateGuarantor(index, 'memberId', e.target.value)}
                    >
                      <option value="">Select member...</option>
                      {activeMembers
                        .filter(member => String(member.memberId) !== String(applicationForm.memberId))
                        .map(member => (
                          <option key={member.memberId} value={member.memberId}>
                            {member.firstName} {member.lastName} ({member.membershipNo})
                          </option>
                        ))}
                    </select>
                  </div>
                  <div className="form-group">
                    <label>Guaranteed Amount (UGX)</label>
                    <input
                      type="number"
                      min="1"
                      value={row.guaranteedAmount}
                      onChange={e => updateGuarantor(index, 'guaranteedAmount', e.target.value)}
                    />
                  </div>
                  <button type="button" className="link-btn danger-link" onClick={() => removeGuarantor(index)}>
                    Remove
                  </button>
                </div>
              ))
            )}
            <button type="button" className="btn btn-secondary" onClick={addGuarantorRow} style={{ marginTop: 10 }}>
              + Add Guarantor
            </button>

            <div className="form-actions">
              <button type="button" className="btn btn-ghost" onClick={() => setShowApplicationForm(false)}>Cancel</button>
              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting ? 'Submitting...' : 'Submit Application'}
              </button>
            </div>
          </form>
        </div>
      )}

      <div className="card">
        <div className="card-heading">
          <div>
            <h3>Loan Register</h3>
            <p className="card-subtitle">
              {filteredLoans.length} loan{filteredLoans.length === 1 ? '' : 's'} found
            </p>
          </div>
        </div>

        <div className="toolbar">
          <input
            className="search-input"
            placeholder="Search loan number, member, product..."
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
          <select className="filter-select" value={statusFilter} onChange={e => setStatusFilter(e.target.value)}>
            <option value="ALL">All statuses</option>
            {Object.entries(STATUS_LABELS).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </div>

        {loading ? (
          <div className="loading">Loading loans...</div>
        ) : filteredLoans.length === 0 ? (
          <p className="empty-text">
            {loans.length === 0
              ? 'No loan applications yet. Create an application to start the credit workflow.'
              : 'No loans match your search.'}
          </p>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Loan</th>
                  <th>Member</th>
                  <th>Product</th>
                  <th>Amount</th>
                  <th>Outstanding</th>
                  <th>Status</th>
                  <th>Next Due</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredLoans.map(loan => (
                  <tr key={loan.loanId}>
                    <td>
                      <strong>{loan.loanNumber}</strong>
                      <span className="cell-sub">{formatDate(loan.applicationDate)}</span>
                    </td>
                    <td>
                      {loan.memberName || memberMap[loan.memberId] || `Member #${loan.memberId}`}
                      {loan.membershipNo && <span className="cell-sub">{loan.membershipNo}</span>}
                    </td>
                    <td>
                      {loan.productName || `Product #${loan.productId}`}
                      <span className="cell-sub">{loan.interestRate}% · {METHOD_LABELS[loan.interestMethod] || loan.interestMethod}</span>
                    </td>
                    <td>
                      {formatMoney(loan.amount)}
                      <span className="cell-sub">{loan.termMonths} months</span>
                    </td>
                    <td>
                      {formatMoney(Number(loan.outstandingPrincipal || 0) + Number(loan.outstandingInterest || 0))}
                      {Number(loan.amountInArrears || 0) > 0 && (
                        <span className="cell-sub danger-text">Arrears {formatMoney(loan.amountInArrears)}</span>
                      )}
                    </td>
                    <td>
                      <span className={`badge badge-${STATUS_BADGES[loan.status] || 'default'}`}>
                        {STATUS_LABELS[loan.status] || loan.status}
                      </span>
                    </td>
                    <td>{formatDate(loan.nextDueDate)}</td>
                    <td>{renderActions(loan)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {selectedLoan && (
        <div className="modal-overlay" onClick={closeDetail}>
          <div className="modal loan-detail-modal" onClick={event => event.stopPropagation()}>
            <div className="modal-header">
              <div>
                <h3>{selectedLoan.loanNumber}</h3>
                <p className="card-subtitle">
                  {selectedLoan.memberName} · {selectedLoan.productName} · {formatMoney(selectedLoan.amount)}
                </p>
              </div>
              <button type="button" className="modal-close" onClick={closeDetail}>×</button>
            </div>
            <div className="modal-body">
              <div className="tabs">
                <button type="button" className={`tab ${detailTab === 'overview' ? 'active' : ''}`} onClick={() => setDetailTab('overview')}>Overview</button>
                <button type="button" className={`tab ${detailTab === 'schedule' ? 'active' : ''}`} onClick={() => setDetailTab('schedule')}>Schedule</button>
                <button type="button" className={`tab ${detailTab === 'repayments' ? 'active' : ''}`} onClick={() => setDetailTab('repayments')}>Repayments</button>
                <button type="button" className={`tab ${detailTab === 'demands' ? 'active' : ''}`} onClick={() => setDetailTab('demands')}>Demands ({demands.length})</button>
              </div>

              {detailLoading ? (
                <div className="loading">Loading loan details...</div>
              ) : (
                <>
                  {detailTab === 'overview' && (
                    <div className="tab-panel">
                      <div className="info-grid">
                        <div className="info-row"><span>Status</span><strong>
                          <span className={`badge badge-${STATUS_BADGES[selectedLoan.status] || 'default'}`}>
                            {STATUS_LABELS[selectedLoan.status] || selectedLoan.status}
                          </span>
                        </strong></div>
                        <div className="info-row"><span>Interest</span><strong>{selectedLoan.interestRate}% · {METHOD_LABELS[selectedLoan.interestMethod] || selectedLoan.interestMethod}</strong></div>
                        <div className="info-row"><span>Term</span><strong>{selectedLoan.termMonths} months</strong></div>
                        <div className="info-row"><span>Total Repayable</span><strong>{formatMoney(selectedLoan.totalRepayable)}</strong></div>
                        <div className="info-row"><span>Outstanding Principal</span><strong>{formatMoney(selectedLoan.outstandingPrincipal)}</strong></div>
                        <div className="info-row"><span>Outstanding Interest</span><strong>{formatMoney(selectedLoan.outstandingInterest)}</strong></div>
                        <div className="info-row"><span>Total Paid</span><strong>{formatMoney(selectedLoan.totalPaid)}</strong></div>
                        <div className="info-row"><span>Arrears</span><strong className={Number(selectedLoan.amountInArrears || 0) > 0 ? 'danger-text' : ''}>{formatMoney(selectedLoan.amountInArrears)}</strong></div>
                        <div className="info-row"><span>Penalties Due</span><strong>{formatMoney(selectedLoan.penaltyDue)}</strong></div>
                        <div className="info-row"><span>Next Due Date</span><strong>{formatDate(selectedLoan.nextDueDate)}</strong></div>
                        <div className="info-row"><span>Application Date</span><strong>{formatDate(selectedLoan.applicationDate)}</strong></div>
                        <div className="info-row"><span>Disbursed</span><strong>{formatDateTime(selectedLoan.disbursedAt)}</strong></div>
                        {selectedLoan.purpose && (
                          <div className="info-row"><span>Purpose</span><strong>{selectedLoan.purpose}</strong></div>
                        )}
                        {selectedLoan.rejectionReason && (
                          <div className="info-row"><span>Rejection Reason</span><strong className="danger-text">{selectedLoan.rejectionReason}</strong></div>
                        )}
                        {selectedLoan.eligibilityNotes && (
                          <div className="info-row"><span>Review Notes</span><strong>{selectedLoan.eligibilityNotes}</strong></div>
                        )}
                      </div>

                      {(selectedLoan.guarantors || []).length > 0 && (
                        <>
                          <h4 className="section-title">Guarantors</h4>
                          <div className="table-wrap">
                            <table>
                              <thead>
                                <tr><th>Member</th><th>Guaranteed Amount</th><th>Status</th></tr>
                              </thead>
                              <tbody>
                                {selectedLoan.guarantors.map(guarantor => (
                                  <tr key={guarantor.guarantorId}>
                                    <td>{guarantor.memberName || `Member #${guarantor.memberId}`}</td>
                                    <td>{formatMoney(guarantor.guaranteedAmount)}</td>
                                    <td><span className="badge badge-info">{guarantor.status}</span></td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        </>
                      )}

                      {canRepay && ACTIVE_STATUSES.includes(selectedLoan.status) && (
                        <form onSubmit={handleRepayment} className="approval-panel">
                          <h4>Post Repayment</h4>
                          <div className="grid-2">
                            <div className="form-group">
                              <label>Amount (UGX) *</label>
                              <input
                                type="number"
                                min="1"
                                value={repaymentForm.amount}
                                onChange={e => setRepaymentForm({ ...repaymentForm, amount: e.target.value })}
                                required
                              />
                            </div>
                            <div className="form-group">
                              <label>Payment Date</label>
                              <input
                                type="date"
                                value={repaymentForm.paymentDate}
                                onChange={e => setRepaymentForm({ ...repaymentForm, paymentDate: e.target.value })}
                              />
                            </div>
                          </div>
                          <div className="form-group">
                            <label>Notes</label>
                            <input
                              value={repaymentForm.notes}
                              onChange={e => setRepaymentForm({ ...repaymentForm, notes: e.target.value })}
                              placeholder="Optional narration"
                            />
                          </div>
                          <div className="form-actions">
                            <button type="submit" className="btn btn-success" disabled={repaymentSubmitting}>
                              {repaymentSubmitting ? 'Posting...' : 'Post Repayment'}
                            </button>
                          </div>
                        </form>
                      )}
                    </div>
                  )}

                  {detailTab === 'schedule' && (
                    <div className="tab-panel">
                      {schedule.length === 0 ? (
                        <p className="empty-text">
                          {selectedLoan.status === 'APPROVED'
                            ? 'The repayment schedule is generated when the loan is disbursed.'
                            : 'No schedule available for this loan yet.'}
                        </p>
                      ) : (
                        <div className="table-wrap">
                          <table>
                            <thead>
                              <tr>
                                <th>#</th>
                                <th>Due Date</th>
                                <th>Principal</th>
                                <th>Interest</th>
                                <th>Total Due</th>
                                <th>Paid</th>
                                <th>Balance</th>
                                <th>Penalty</th>
                                <th>Status</th>
                              </tr>
                            </thead>
                            <tbody>
                              {schedule.map(row => (
                                <tr key={row.scheduleId || row.installmentNo}>
                                  <td>{row.installmentNo}</td>
                                  <td>{formatDate(row.dueDate)}</td>
                                  <td>{formatMoney(row.principalDue)}</td>
                                  <td>{formatMoney(row.interestDue)}</td>
                                  <td>{formatMoney(row.totalDue)}</td>
                                  <td>{formatMoney(row.totalPaid)}</td>
                                  <td>{formatMoney(row.balance)}</td>
                                  <td>
                                    {formatMoney(Number(row.penaltyDue || 0) + Number(row.accruedPenalty || 0))}
                                    {row.daysOverdue > 0 && <span className="cell-sub danger-text">{row.daysOverdue} days late</span>}
                                  </td>
                                  <td>
                                    <span className={`badge badge-${row.status === 'PAID' ? 'success' : row.status === 'OVERDUE' ? 'danger' : row.status === 'PARTIALLY_PAID' ? 'warning' : 'default'}`}>
                                      {row.status}
                                    </span>
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      )}
                    </div>
                  )}

                  {detailTab === 'repayments' && (
                    <div className="tab-panel">
                      {repayments.length === 0 ? (
                        <p className="empty-text">No repayments have been posted to this loan yet.</p>
                      ) : (
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
                              {repayments.map(repayment => (
                                <tr key={repayment.repaymentId}>
                                  <td>{formatDate(repayment.paymentDate)}</td>
                                  <td><strong>{repayment.reference}</strong></td>
                                  <td>{formatMoney(repayment.amount)}</td>
                                  <td>{formatMoney(repayment.principalPortion)}</td>
                                  <td>{formatMoney(repayment.interestPortion)}</td>
                                  <td>{formatMoney(repayment.penaltyPortion)}</td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      )}
                    </div>
                  )}

                  {detailTab === 'demands' && (
                    <div className="tab-panel">
                      {demands.length === 0 ? (
                        <p className="empty-text">No demand notices have been issued for this loan.</p>
                      ) : (
                        <div className="table-wrap">
                          <table>
                            <thead>
                              <tr>
                                <th>Demand</th>
                                <th>Stage</th>
                                <th>Amount</th>
                                <th>Due date</th>
                                <th>Status</th>
                                <th>Issued</th>
                                <th></th>
                              </tr>
                            </thead>
                            <tbody>
                              {demands.map(demand => (
                                <tr key={demand.demandId}>
                                  <td><strong>{demand.demandNumber}</strong></td>
                                  <td>{DEMAND_STAGE_LABELS[demand.stage] || demand.stage}</td>
                                  <td>{formatMoney(demand.amountDemanded)}</td>
                                  <td>{formatDate(demand.dueDate)}</td>
                                  <td>
                                    <span className={`badge badge-${DEMAND_STATUS_BADGES[demand.status] || 'default'}`}>
                                      {demand.status.replaceAll('_', ' ')}
                                    </span>
                                  </td>
                                  <td>{formatDateTime(demand.issuedAt)}</td>
                                  <td>
                                    <button
                                      type="button"
                                      className="link-btn"
                                      onClick={() => api.downloadLoanDemandPdf(
                                        selectedLoan.loanId,
                                        demand.demandId,
                                        demand.demandNumber
                                      )}
                                    >
                                      Print PDF
                                    </button>
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      )}
                    </div>
                  )}
                </>
              )}

              <div className="form-actions">
                <button type="button" className="btn btn-secondary" onClick={closeDetail}>Close</button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default Loans
