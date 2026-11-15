import { useEffect, useState } from 'react'
import { api } from '../services/api'

const TAX_CATEGORIES = [
  ['GLOBAL', 'Global (fallback for every category)'],
  ['SAVINGS', 'Savings'],
  ['TRANSFER', 'Transfers'],
  ['LOANS', 'Loans'],
  ['SHARES', 'Shares'],
  ['MOBILE_MONEY', 'Mobile Money'],
  ['IMPORT', 'Imports / Procurement']
]

const TRANSACTION_TYPES = [
  ['DEPOSIT', 'Deposit'],
  ['WITHDRAWAL', 'Withdrawal'],
  ['TRANSFER', 'Transfer'],
  ['LOAN_REPAYMENT', 'Loan Repayment'],
  ['SHARE_PURCHASE', 'Share Purchase']
]

const CHANNELS = [
  ['CASH', 'Cash (counter)'],
  ['BANK', 'Bank'],
  ['MOBILE_MONEY', 'Mobile Money'],
  ['CROSS_BANK', 'Cross-Bank Transfer'],
  ['INTERNAL', 'Internal Transfer']
]

function formatMoney(value) {
  return `UGX ${Number(value || 0).toLocaleString()}`
}

function formatPercent(value) {
  return `${Number(value || 0)}%`
}

function emptyTaxForm() {
  return {
    name: '',
    code: '',
    category: 'GLOBAL',
    ratePercent: '',
    appliesToFees: true,
    isActive: true,
    description: ''
  }
}

function emptyFeeForm() {
  return {
    name: '',
    transactionType: 'DEPOSIT',
    channel: 'MOBILE_MONEY',
    feeType: 'PERCENTAGE',
    feeValue: '',
    minAmount: '',
    maxFee: '',
    taxRateId: '',
    isActive: true,
    description: ''
  }
}

function FinanceSettings() {
  const role = localStorage.getItem('role') || ''
  const canManage = ['ADMIN', 'MANAGER'].includes(role)

  const [tab, setTab] = useState('taxes')
  const [taxRates, setTaxRates] = useState([])
  const [feeConfigs, setFeeConfigs] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [success, setSuccess] = useState(null)
  const [saving, setSaving] = useState(false)

  const [showTaxForm, setShowTaxForm] = useState(false)
  const [editingTaxId, setEditingTaxId] = useState(null)
  const [taxForm, setTaxForm] = useState(emptyTaxForm)

  const [showFeeForm, setShowFeeForm] = useState(false)
  const [editingFeeId, setEditingFeeId] = useState(null)
  const [feeForm, setFeeForm] = useState(emptyFeeForm)

  useEffect(() => {
    loadData()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function loadData() {
    setLoading(true)
    try {
      const [taxData, feeData] = await Promise.all([
        api.getTaxRates().catch(() => []),
        api.getFeeConfigs().catch(() => [])
      ])
      setTaxRates(taxData || [])
      setFeeConfigs(feeData || [])
      setError(null)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  function flashSuccess(message) {
    setSuccess(message)
    window.setTimeout(() => setSuccess(null), 4000)
  }

  function openTaxForm(rate) {
    setError(null)
    if (rate) {
      setEditingTaxId(rate.taxRateId)
      setTaxForm({
        name: rate.name || '',
        code: rate.code || '',
        category: rate.category || 'GLOBAL',
        ratePercent: rate.ratePercent ?? '',
        appliesToFees: rate.appliesToFees !== false,
        isActive: rate.isActive !== false,
        description: rate.description || ''
      })
    } else {
      setEditingTaxId(null)
      setTaxForm(emptyTaxForm())
    }
    setShowTaxForm(true)
  }

  async function submitTax(event) {
    event.preventDefault()
    setSaving(true)
    setError(null)
    try {
      const payload = {
        ...taxForm,
        ratePercent: Number(taxForm.ratePercent)
      }
      if (editingTaxId) {
        await api.updateTaxRate(editingTaxId, payload)
        flashSuccess('Tax rate updated.')
      } else {
        await api.createTaxRate(payload)
        flashSuccess('Tax rate created.')
      }
      setShowTaxForm(false)
      setEditingTaxId(null)
      await loadData()
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  async function toggleTax(rate) {
    setError(null)
    try {
      await api.toggleTaxRate(rate.taxRateId)
      await loadData()
    } catch (err) {
      setError(err.message)
    }
  }

  function openFeeForm(config) {
    setError(null)
    if (config) {
      setEditingFeeId(config.feeConfigId)
      setFeeForm({
        name: config.name || '',
        transactionType: config.transactionType || 'DEPOSIT',
        channel: config.channel || 'MOBILE_MONEY',
        feeType: config.feeType || 'PERCENTAGE',
        feeValue: config.feeValue ?? '',
        minAmount: config.minAmount ?? '',
        maxFee: config.maxFee ?? '',
        taxRateId: config.taxRateId ?? '',
        isActive: config.isActive !== false,
        description: config.description || ''
      })
    } else {
      setEditingFeeId(null)
      setFeeForm(emptyFeeForm())
    }
    setShowFeeForm(true)
  }

  async function submitFee(event) {
    event.preventDefault()
    setSaving(true)
    setError(null)
    try {
      const payload = {
        ...feeForm,
        feeValue: Number(feeForm.feeValue),
        minAmount: feeForm.minAmount === '' ? null : Number(feeForm.minAmount),
        maxFee: feeForm.maxFee === '' ? null : Number(feeForm.maxFee),
        taxRateId: feeForm.taxRateId === '' ? null : Number(feeForm.taxRateId)
      }
      if (editingFeeId) {
        await api.updateFeeConfig(editingFeeId, payload)
        flashSuccess('Fee configuration updated.')
      } else {
        await api.createFeeConfig(payload)
        flashSuccess('Fee configuration created.')
      }
      setShowFeeForm(false)
      setEditingFeeId(null)
      await loadData()
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  async function toggleFee(config) {
    setError(null)
    try {
      await api.toggleFeeConfig(config.feeConfigId)
      await loadData()
    } catch (err) {
      setError(err.message)
    }
  }

  if (loading) {
    return <div className="loading">Loading finance configuration...</div>
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Finance Settings</h2>
          <p className="page-subtitle">
            Configurable tax percentages and transaction charges applied automatically by the fee engine
          </p>
        </div>
        <div className="header-actions">
          <span className="count-chip">{taxRates.length} tax rates</span>
          <span className="count-chip">{feeConfigs.length} fee rules</span>
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}
      {success && <div className="success-message">{success}</div>}
      {!canManage && (
        <div className="info-banner">
          Your role has read-only access to finance configuration.
        </div>
      )}

      <div className="tabs">
        <button className={`tab ${tab === 'taxes' ? 'active' : ''}`} onClick={() => setTab('taxes')}>
          Tax Rates
        </button>
        <button className={`tab ${tab === 'fees' ? 'active' : ''}`} onClick={() => setTab('fees')}>
          Transaction Fees
        </button>
      </div>

      {tab === 'taxes' && (
        <div className="tab-panel">
          <div className="card">
            <div className="card-heading">
              <div>
                <h3>Tax Rate Catalogue</h3>
                <p className="card-subtitle">
                  Category rates apply automatically. A GLOBAL rate acts as the fallback when no category rate exists.
                </p>
              </div>
              {canManage && (
                <button className="btn btn-primary btn-sm" onClick={() => openTaxForm(null)}>
                  + New Tax Rate
                </button>
              )}
            </div>

            {showTaxForm && canManage && (
              <form className="config-form" onSubmit={submitTax}>
                <div className="grid-3">
                  <div className="form-group">
                    <label>Name *</label>
                    <input
                      value={taxForm.name}
                      onChange={e => setTaxForm({ ...taxForm, name: e.target.value })}
                      placeholder="VAT on Financial Fees"
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label>Code *</label>
                    <input
                      value={taxForm.code}
                      onChange={e => setTaxForm({ ...taxForm, code: e.target.value })}
                      placeholder="VAT_FEES"
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label>Category *</label>
                    <select
                      value={taxForm.category}
                      onChange={e => setTaxForm({ ...taxForm, category: e.target.value })}
                    >
                      {TAX_CATEGORIES.map(([value, label]) => (
                        <option key={value} value={value}>{label}</option>
                      ))}
                    </select>
                  </div>
                </div>
                <div className="grid-3">
                  <div className="form-group">
                    <label>Rate (%) *</label>
                    <input
                      type="number"
                      step="0.0001"
                      min="0"
                      value={taxForm.ratePercent}
                      onChange={e => setTaxForm({ ...taxForm, ratePercent: e.target.value })}
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label>Applies To</label>
                    <select
                      value={taxForm.appliesToFees ? 'fees' : 'transactions'}
                      onChange={e => setTaxForm({ ...taxForm, appliesToFees: e.target.value === 'fees' })}
                    >
                      <option value="fees">Transaction fees</option>
                      <option value="transactions">Transaction amounts</option>
                    </select>
                  </div>
                  <div className="form-group">
                    <label>Status</label>
                    <select
                      value={taxForm.isActive ? 'active' : 'inactive'}
                      onChange={e => setTaxForm({ ...taxForm, isActive: e.target.value === 'active' })}
                    >
                      <option value="active">Active</option>
                      <option value="inactive">Inactive</option>
                    </select>
                  </div>
                </div>
                <div className="form-group">
                  <label>Description</label>
                  <input
                    value={taxForm.description}
                    onChange={e => setTaxForm({ ...taxForm, description: e.target.value })}
                  />
                </div>
                <div className="form-actions">
                  <button type="button" className="btn btn-ghost" onClick={() => setShowTaxForm(false)}>
                    Cancel
                  </button>
                  <button type="submit" className="btn btn-primary" disabled={saving}>
                    {saving ? 'Saving...' : editingTaxId ? 'Update Tax Rate' : 'Create Tax Rate'}
                  </button>
                </div>
              </form>
            )}

            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Tax</th>
                    <th>Category</th>
                    <th>Rate</th>
                    <th>Applies To</th>
                    <th>Status</th>
                    {canManage && <th>Actions</th>}
                  </tr>
                </thead>
                <tbody>
                  {taxRates.length === 0 ? (
                    <tr>
                      <td colSpan={canManage ? 6 : 5} className="empty-text">No tax rates configured.</td>
                    </tr>
                  ) : taxRates.map(rate => (
                    <tr key={rate.taxRateId}>
                      <td>
                        <strong>{rate.name}</strong>
                        <div className="cell-subtext">{rate.code}</div>
                      </td>
                      <td>{rate.category}</td>
                      <td><strong>{formatPercent(rate.ratePercent)}</strong></td>
                      <td>{rate.appliesToFees ? 'Fees' : 'Transaction amount'}</td>
                      <td>
                        <span className={`badge ${rate.isActive ? 'badge-success' : 'badge-default'}`}>
                          {rate.isActive ? 'ACTIVE' : 'INACTIVE'}
                        </span>
                      </td>
                      {canManage && (
                        <td>
                          <div className="row-actions">
                            <button className="btn btn-secondary btn-sm" onClick={() => openTaxForm(rate)}>
                              Edit
                            </button>
                            <button className="btn btn-ghost btn-sm" onClick={() => toggleTax(rate)}>
                              {rate.isActive ? 'Disable' : 'Enable'}
                            </button>
                          </div>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {tab === 'fees' && (
        <div className="tab-panel">
          <div className="card">
            <div className="card-heading">
              <div>
                <h3>Transaction Fee Rules</h3>
                <p className="card-subtitle">
                  Cash and internal transfers remain fee-free unless a rule is added. Every posted transaction
                  is recorded in the compliance ledger.
                </p>
              </div>
              {canManage && (
                <button className="btn btn-primary btn-sm" onClick={() => openFeeForm(null)}>
                  + New Fee Rule
                </button>
              )}
            </div>

            {showFeeForm && canManage && (
              <form className="config-form" onSubmit={submitFee}>
                <div className="grid-3">
                  <div className="form-group">
                    <label>Name *</label>
                    <input
                      value={feeForm.name}
                      onChange={e => setFeeForm({ ...feeForm, name: e.target.value })}
                      placeholder="Mobile Money Collection Charge"
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label>Transaction Type *</label>
                    <select
                      value={feeForm.transactionType}
                      onChange={e => setFeeForm({ ...feeForm, transactionType: e.target.value })}
                    >
                      {TRANSACTION_TYPES.map(([value, label]) => (
                        <option key={value} value={value}>{label}</option>
                      ))}
                    </select>
                  </div>
                  <div className="form-group">
                    <label>Channel *</label>
                    <select
                      value={feeForm.channel}
                      onChange={e => setFeeForm({ ...feeForm, channel: e.target.value })}
                    >
                      {CHANNELS.map(([value, label]) => (
                        <option key={value} value={value}>{label}</option>
                      ))}
                    </select>
                  </div>
                </div>
                <div className="grid-3">
                  <div className="form-group">
                    <label>Fee Type *</label>
                    <select
                      value={feeForm.feeType}
                      onChange={e => setFeeForm({ ...feeForm, feeType: e.target.value })}
                    >
                      <option value="PERCENTAGE">Percentage of amount</option>
                      <option value="FIXED">Fixed amount</option>
                    </select>
                  </div>
                  <div className="form-group">
                    <label>{feeForm.feeType === 'PERCENTAGE' ? 'Fee (%) *' : 'Fee Amount (UGX) *'}</label>
                    <input
                      type="number"
                      step="0.0001"
                      min="0"
                      value={feeForm.feeValue}
                      onChange={e => setFeeForm({ ...feeForm, feeValue: e.target.value })}
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label>Tax Applied</label>
                    <select
                      value={feeForm.taxRateId}
                      onChange={e => setFeeForm({ ...feeForm, taxRateId: e.target.value })}
                    >
                      <option value="">Automatic (category / global)</option>
                      {taxRates.filter(rate => rate.isActive).map(rate => (
                        <option key={rate.taxRateId} value={rate.taxRateId}>
                          {rate.name} ({formatPercent(rate.ratePercent)})
                        </option>
                      ))}
                    </select>
                  </div>
                </div>
                <div className="grid-3">
                  <div className="form-group">
                    <label>Minimum Amount</label>
                    <input
                      type="number"
                      min="0"
                      value={feeForm.minAmount}
                      onChange={e => setFeeForm({ ...feeForm, minAmount: e.target.value })}
                      placeholder="No minimum"
                    />
                  </div>
                  <div className="form-group">
                    <label>Maximum Fee Cap</label>
                    <input
                      type="number"
                      min="0"
                      value={feeForm.maxFee}
                      onChange={e => setFeeForm({ ...feeForm, maxFee: e.target.value })}
                      placeholder="No cap"
                    />
                  </div>
                  <div className="form-group">
                    <label>Status</label>
                    <select
                      value={feeForm.isActive ? 'active' : 'inactive'}
                      onChange={e => setFeeForm({ ...feeForm, isActive: e.target.value === 'active' })}
                    >
                      <option value="active">Active</option>
                      <option value="inactive">Inactive</option>
                    </select>
                  </div>
                </div>
                <div className="form-group">
                  <label>Description</label>
                  <input
                    value={feeForm.description}
                    onChange={e => setFeeForm({ ...feeForm, description: e.target.value })}
                  />
                </div>
                <div className="form-actions">
                  <button type="button" className="btn btn-ghost" onClick={() => setShowFeeForm(false)}>
                    Cancel
                  </button>
                  <button type="submit" className="btn btn-primary" disabled={saving}>
                    {saving ? 'Saving...' : editingFeeId ? 'Update Fee Rule' : 'Create Fee Rule'}
                  </button>
                </div>
              </form>
            )}

            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Rule</th>
                    <th>Applies To</th>
                    <th>Charge</th>
                    <th>Tax</th>
                    <th>Status</th>
                    {canManage && <th>Actions</th>}
                  </tr>
                </thead>
                <tbody>
                  {feeConfigs.length === 0 ? (
                    <tr>
                      <td colSpan={canManage ? 6 : 5} className="empty-text">No fee rules configured.</td>
                    </tr>
                  ) : feeConfigs.map(config => (
                    <tr key={config.feeConfigId}>
                      <td>
                        <strong>{config.name}</strong>
                        <div className="cell-subtext">{config.description || 'Transaction charge'}</div>
                      </td>
                      <td>
                        <div>{config.transactionType}</div>
                        <div className="cell-subtext">{config.channel}</div>
                      </td>
                      <td>
                        <strong>
                          {config.feeType === 'PERCENTAGE'
                            ? formatPercent(config.feeValue)
                            : formatMoney(config.feeValue)}
                        </strong>
                        {config.maxFee ? <div className="cell-subtext">Cap {formatMoney(config.maxFee)}</div> : null}
                      </td>
                      <td>
                        {config.taxRateName
                          ? `${config.taxRateName} (${formatPercent(config.taxRatePercent)})`
                          : 'Automatic'}
                      </td>
                      <td>
                        <span className={`badge ${config.isActive ? 'badge-success' : 'badge-default'}`}>
                          {config.isActive ? 'ACTIVE' : 'INACTIVE'}
                        </span>
                      </td>
                      {canManage && (
                        <td>
                          <div className="row-actions">
                            <button className="btn btn-secondary btn-sm" onClick={() => openFeeForm(config)}>
                              Edit
                            </button>
                            <button className="btn btn-ghost btn-sm" onClick={() => toggleFee(config)}>
                              {config.isActive ? 'Disable' : 'Enable'}
                            </button>
                          </div>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default FinanceSettings
