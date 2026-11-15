import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api } from '../services/api'

const ASSET_TYPES = [
  { value: 'LAND_TITLE', label: 'Land Title' },
  { value: 'RESIDENTIAL_HOUSE', label: 'Residential House' },
  { value: 'COMMERCIAL_BUILDING', label: 'Commercial Building' },
  { value: 'MOTOR_VEHICLE', label: 'Motor Vehicle' },
  { value: 'EQUIPMENT', label: 'Equipment' },
  { value: 'OTHER', label: 'Other Asset' }
]

const DOCUMENT_TYPES = [
  { value: 'TITLE_DEED', label: 'Title Deed / Ownership Document' },
  { value: 'SALE_AGREEMENT', label: 'Sale Agreement' },
  { value: 'TRANSFER_FORM', label: 'Transfer Form' },
  { value: 'VALUATION_REPORT', label: 'Valuation Report' },
  { value: 'INSURANCE_CERTIFICATE', label: 'Insurance Certificate' },
  { value: 'OTHER', label: 'Other Supporting Document' }
]

const ASSET_TYPE_LABELS = Object.fromEntries(ASSET_TYPES.map(item => [item.value, item.label]))
const DOCUMENT_TYPE_LABELS = Object.fromEntries(DOCUMENT_TYPES.map(item => [item.value, item.label]))

const COLLATERAL_STATUS_LABELS = {
  HELD_IN_CUSTODY: 'Held in Custody',
  RELEASED: 'Released',
  UNDER_DEFAULT_AUCTION: 'Under Default / Auction'
}

const PAGE_SIZE = 8

function emptyDocumentRow() {
  return { documentType: 'TITLE_DEED', documentName: '', file: null }
}

function initialForm(memberId = '') {
  return {
    memberId: memberId ? String(memberId) : '',
    loanAccountId: '',
    assetType: 'LAND_TITLE',
    titleNumber: '',
    description: '',
    estimatedValue: '',
    forcedSaleValue: '',
    valuationDate: '',
    valuerName: '',
    registeredOwnerName: '',
    custodianLocation: ''
  }
}

function formatMoney(value) {
  return `UGX ${Number(value || 0).toLocaleString()}`
}

function formatDate(value) {
  if (!value) return '—'
  return new Date(value).toLocaleDateString()
}

function formatDateTime(value) {
  if (!value) return '—'
  return new Date(value).toLocaleString()
}

function formatFileSize(bytes) {
  if (!bytes) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function badgeClass(status) {
  const map = {
    HELD_IN_CUSTODY: 'warning',
    RELEASED: 'success',
    UNDER_DEFAULT_AUCTION: 'danger'
  }
  return `badge badge-${map[status] || 'default'}`
}

function Info({ label, value }) {
  return (
    <div className="info-row">
      <span>{label}</span>
      <strong>{value || '—'}</strong>
    </div>
  )
}

function Collateral() {
  const [searchParams, setSearchParams] = useSearchParams()
  const role = localStorage.getItem('role') || 'MEMBER'
  const canManageCollateral = ['ADMIN', 'RECOVERY_ADMIN', 'MANAGER', 'TELLER', 'LOAN_OFFICER'].includes(role)
  const [assets, setAssets] = useState([])
  const [members, setMembers] = useState([])
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)
  const [success, setSuccess] = useState(null)

  const [showForm, setShowForm] = useState(false)
  const [editingId, setEditingId] = useState(null)
  const [form, setForm] = useState(initialForm())
  const [documentRows, setDocumentRows] = useState([emptyDocumentRow()])
  const [loanAccounts, setLoanAccounts] = useState([])
  const [loadingLoans, setLoadingLoans] = useState(false)

  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [typeFilter, setTypeFilter] = useState('ALL')
  const [page, setPage] = useState(1)

  const [selectedId, setSelectedId] = useState(null)
  const [detail, setDetail] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailTab, setDetailTab] = useState('overview')

  const [releaseOpen, setReleaseOpen] = useState(false)
  const [releaseForm, setReleaseForm] = useState({
    releasedTo: '',
    releaseDate: '',
    authorizedBy: '',
    signature: '',
    notes: ''
  })

  const [defaultOpen, setDefaultOpen] = useState(false)
  const [defaultReason, setDefaultReason] = useState('')

  const [uploadDocType, setUploadDocType] = useState('TITLE_DEED')
  const [uploadDocName, setUploadDocName] = useState('')
  const [uploadFile, setUploadFile] = useState(null)
  const [uploadingDoc, setUploadingDoc] = useState(false)

  useEffect(() => {
    loadData()
  }, [])

  useEffect(() => {
    const viewId = searchParams.get('view')
    if (viewId && Number(viewId) !== selectedId) {
      openDetail(Number(viewId))
    }

    if (!showForm && searchParams.get('new') === '1' && canManageCollateral) {
      setShowForm(true)
      setEditingId(null)
      const memberId = searchParams.get('memberId')
      setForm(initialForm(memberId || ''))
      setDocumentRows([emptyDocumentRow()])
      setError(null)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams])

  useEffect(() => {
    if (!form.memberId) {
      setLoanAccounts([])
      return
    }
    loadLoanAccounts(form.memberId)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [form.memberId])

  async function loadData() {
    setLoading(true)
    try {
      const [assetData, memberData] = await Promise.all([
        api.getCollateral(),
        api.getMembers().catch(() => [])
      ])
      setAssets(assetData || [])
      setMembers(memberData || [])
      setError(null)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  async function loadLoanAccounts(memberId) {
    setLoadingLoans(true)
    try {
      const data = await api.getAccountsByMember(memberId)
      const loans = (data || []).filter(account => account.accountType === 'LOAN_DISBURSEMENT')
      setLoanAccounts(loans)
      setForm(prev => {
        if (prev.loanAccountId && !loans.some(account => String(account.accountId) === String(prev.loanAccountId))) {
          return { ...prev, loanAccountId: '' }
        }
        return prev
      })
    } catch (err) {
      setLoanAccounts([])
    } finally {
      setLoadingLoans(false)
    }
  }

  const filteredAssets = useMemo(() => {
    const query = search.trim().toLowerCase()
    return assets.filter(asset => {
      const member = members.find(item => item.memberId === asset.memberId)
      const haystack = [
        asset.assetCode,
        asset.titleNumber,
        asset.registeredOwnerName,
        asset.custodianLocation,
        asset.valuerName,
        asset.description,
        asset.loanAccountNumber,
        member ? `${member.firstName} ${member.lastName}` : '',
        member ? member.membershipNo : ''
      ].filter(Boolean).join(' ').toLowerCase()

      const matchesSearch = !query || haystack.includes(query)
      const matchesStatus = statusFilter === 'ALL' || asset.status === statusFilter
      const matchesType = typeFilter === 'ALL' || asset.assetType === typeFilter
      return matchesSearch && matchesStatus && matchesType
    })
  }, [assets, members, search, statusFilter, typeFilter])

  const totalPages = Math.max(1, Math.ceil(filteredAssets.length / PAGE_SIZE))
  const pageItems = filteredAssets.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE)

  useEffect(() => {
    setPage(1)
  }, [search, statusFilter, typeFilter])

  useEffect(() => {
    if (page > totalPages) setPage(totalPages)
  }, [page, totalPages])

  function updateField(field, value) {
    setForm(prev => ({ ...prev, [field]: value }))
  }

  function updateDocumentRow(index, field, value) {
    setDocumentRows(prev => prev.map((row, i) => i === index ? { ...row, [field]: value } : row))
  }

  function addDocumentRow() {
    setDocumentRows(prev => [...prev, emptyDocumentRow()])
  }

  function removeDocumentRow(index) {
    setDocumentRows(prev => prev.filter((_, i) => i !== index))
  }

  function openCreate() {
    closeDetail()
    setEditingId(null)
    setForm(initialForm())
    setDocumentRows([emptyDocumentRow()])
    setError(null)
    setShowForm(true)
  }

  function openEdit(asset) {
    setEditingId(asset.collateralId)
    setForm({
      memberId: String(asset.memberId),
      loanAccountId: asset.loanAccountId ? String(asset.loanAccountId) : '',
      assetType: asset.assetType,
      titleNumber: asset.titleNumber || '',
      description: asset.description || '',
      estimatedValue: asset.estimatedValue != null ? String(asset.estimatedValue) : '',
      forcedSaleValue: asset.forcedSaleValue != null ? String(asset.forcedSaleValue) : '',
      valuationDate: asset.valuationDate || '',
      valuerName: asset.valuerName || '',
      registeredOwnerName: asset.registeredOwnerName || '',
      custodianLocation: asset.custodianLocation || ''
    })
    setDocumentRows([])
    setError(null)
    setShowForm(true)
  }

  function closeForm() {
    setShowForm(false)
    setEditingId(null)
    setError(null)
    const next = new URLSearchParams(searchParams)
    next.delete('new')
    next.delete('memberId')
    setSearchParams(next, { replace: true })
  }

  function validateForm() {
    if (!form.memberId) return 'Member is required.'
    if (!form.titleNumber.trim()) return 'Title / registration number is required.'
    if (form.estimatedValue === '' || Number(form.estimatedValue) < 0) return 'Enter a valid estimated / market value.'
    if (form.forcedSaleValue !== '' && Number(form.forcedSaleValue) > Number(form.estimatedValue)) {
      return 'Forced sale value cannot exceed the estimated / market value.'
    }
    if (form.forcedSaleValue !== '' && Number(form.forcedSaleValue) < 0) return 'Forced sale value cannot be negative.'
    if (!editingId) {
      const oversized = documentRows.find(row => row.file && row.file.size > 10 * 1024 * 1024)
      if (oversized) return 'Each document must be 10MB or smaller.'
    }
    return null
  }

  async function handleSubmit(event) {
    event.preventDefault()
    const message = validateForm()
    if (message) {
      setError(message)
      return
    }

    setSubmitting(true)
    setError(null)

    const payload = {
      memberId: Number(form.memberId),
      loanAccountId: form.loanAccountId ? Number(form.loanAccountId) : null,
      assetType: form.assetType,
      titleNumber: form.titleNumber.trim(),
      description: form.description.trim(),
      estimatedValue: Number(form.estimatedValue),
      forcedSaleValue: form.forcedSaleValue === '' ? null : Number(form.forcedSaleValue),
      valuationDate: form.valuationDate || null,
      valuerName: form.valuerName.trim(),
      registeredOwnerName: form.registeredOwnerName.trim(),
      custodianLocation: form.custodianLocation.trim()
    }

    try {
      if (editingId) {
        await api.updateCollateral(editingId, payload)
        setSuccess('Collateral details updated.')
        closeForm()
        await loadData()
        if (selectedId === editingId) {
          await reloadDetail(editingId)
        }
      } else {
        const created = await api.createCollateral(payload)
        const warnings = []

        const rowsWithFiles = documentRows.filter(row => row.file)
        for (const row of rowsWithFiles) {
          try {
            await api.uploadCollateralDocument(created.collateralId, row.file, {
              documentType: row.documentType,
              documentName: row.documentName
            })
          } catch (uploadError) {
            warnings.push(row.file.name)
          }
        }

        setSuccess(
          warnings.length
            ? `Collateral registered, but some documents could not be uploaded: ${warnings.join(', ')}.`
            : 'Collateral registered successfully.'
        )
        closeForm()
        await loadData()
        await openDetail(created.collateralId)
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setSubmitting(false)
    }
  }

  async function openDetail(id) {
    setSelectedId(id)
    setDetail(null)
    setDetailTab('overview')
    setReleaseOpen(false)
    setDefaultOpen(false)
    setUploadFile(null)
    setUploadDocName('')
    setDetailLoading(true)
    try {
      const data = await api.getCollateralById(id)
      setDetail(data)
      const next = new URLSearchParams(searchParams)
      next.set('view', String(id))
      setSearchParams(next, { replace: true })
    } catch (err) {
      setError(err.message)
      setSelectedId(null)
    } finally {
      setDetailLoading(false)
    }
  }

  function closeDetail() {
    setSelectedId(null)
    setDetail(null)
    setReleaseOpen(false)
    setDefaultOpen(false)
    const next = new URLSearchParams(searchParams)
    next.delete('view')
    setSearchParams(next, { replace: true })
  }

  async function reloadDetail(id) {
    if (!id) return
    try {
      const data = await api.getCollateralById(id)
      setDetail(data)
      setAssets(prev => prev.map(asset => asset.collateralId === data.collateralId ? { ...asset, ...data } : asset))
    } catch (err) {
      setError(err.message)
    }
  }

  function openRelease() {
    if (!detail) return
    setReleaseForm({
      releasedTo: detail.memberName || '',
      releaseDate: new Date().toISOString().slice(0, 10),
      authorizedBy: '',
      signature: '',
      notes: ''
    })
    setReleaseOpen(true)
  }

  async function handleRelease(event) {
    event.preventDefault()
    if (!detail) return
    if (!releaseForm.authorizedBy.trim()) {
      setError('Authorizing manager is required.')
      return
    }
    if (!releaseForm.signature.trim()) {
      setError('Signature is required.')
      return
    }

    setSubmitting(true)
    setError(null)
    try {
      await api.releaseCollateral(detail.collateralId, {
        releasedTo: releaseForm.releasedTo.trim(),
        releaseDate: releaseForm.releaseDate || null,
        authorizedBy: releaseForm.authorizedBy.trim(),
        signature: releaseForm.signature.trim(),
        notes: releaseForm.notes.trim()
      })
      setReleaseOpen(false)
      setSuccess('Collateral released to member.')
      await reloadDetail(detail.collateralId)
      await loadData()
    } catch (err) {
      setError(err.message)
    } finally {
      setSubmitting(false)
    }
  }

  function openDefault() {
    setDefaultReason('')
    setDefaultOpen(true)
  }

  async function handleMarkDefault(event) {
    event.preventDefault()
    if (!detail) return
    if (!defaultReason.trim()) {
      setError('Reason for default / auction is required.')
      return
    }

    setSubmitting(true)
    setError(null)
    try {
      await api.markCollateralDefault(detail.collateralId, { reason: defaultReason.trim() })
      setDefaultOpen(false)
      setSuccess('Collateral marked under default / auction.')
      await reloadDetail(detail.collateralId)
      await loadData()
    } catch (err) {
      setError(err.message)
    } finally {
      setSubmitting(false)
    }
  }

  async function handleUploadDocument(event) {
    event.preventDefault()
    if (!detail || !uploadFile) return

    if (uploadFile.size > 10 * 1024 * 1024) {
      setError('Document must be 10MB or smaller.')
      return
    }

    setUploadingDoc(true)
    setError(null)
    try {
      await api.uploadCollateralDocument(detail.collateralId, uploadFile, {
        documentType: uploadDocType,
        documentName: uploadDocName
      })
      setUploadFile(null)
      setUploadDocName('')
      setSuccess('Document uploaded.')
      await reloadDetail(detail.collateralId)
    } catch (err) {
      setError(err.message)
    } finally {
      setUploadingDoc(false)
    }
  }

  async function handleDeleteDocument(document) {
    if (!detail) return
    if (!window.confirm(`Delete "${document.documentName}"?`)) return

    setSubmitting(true)
    setError(null)
    try {
      await api.deleteCollateralDocument(detail.collateralId, document.documentId)
      setSuccess('Document deleted.')
      await reloadDetail(detail.collateralId)
    } catch (err) {
      setError(err.message)
    } finally {
      setSubmitting(false)
    }
  }

  const heldCount = assets.filter(asset => asset.status !== 'RELEASED').length
  const heldValue = assets
    .filter(asset => asset.status !== 'RELEASED')
    .reduce((sum, asset) => sum + Number(asset.estimatedValue || 0), 0)
  const defaultCount = assets.filter(asset => asset.status === 'UNDER_DEFAULT_AUCTION').length

  return (
    <div>
      <div className="page-header dashboard-header">
        <div>
          <h2>Collateral & Securities</h2>
          <p className="page-subtitle">
            Asset register for property and securities held by the SACCO
          </p>
        </div>
        <div className="header-actions">
          <span className="count-chip">
            {assets.length} asset{assets.length === 1 ? '' : 's'}
          </span>
          {canManageCollateral && (
            <button className="btn btn-primary" onClick={openCreate}>
              + Register Collateral
            </button>
          )}
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}
      {success && <div className="success-message">{success}</div>}

      <div className="collateral-summary">
        <div className="collateral-summary-card">
          <span>Assets in Custody</span>
          <strong>{heldCount}</strong>
          <small>{formatMoney(heldValue)}</small>
        </div>
        <div className="collateral-summary-card">
          <span>Under Default / Auction</span>
          <strong>{defaultCount}</strong>
          <small>{formatMoney(assets.filter(a => a.status === 'UNDER_DEFAULT_AUCTION').reduce((s, a) => s + Number(a.estimatedValue || 0), 0))}</small>
        </div>
        <div className="collateral-summary-card">
          <span>Total Portfolio</span>
          <strong>{assets.length}</strong>
          <small>{formatMoney(assets.reduce((s, a) => s + Number(a.estimatedValue || 0), 0))}</small>
        </div>
      </div>

      <div className="card">
        <div className="card-heading">
          <div>
            <h3>Collateral List</h3>
            <p className="card-subtitle">
              {filteredAssets.length} asset{filteredAssets.length === 1 ? '' : 's'} found
            </p>
          </div>
        </div>

        <div className="toolbar">
          <input
            className="search-input"
            placeholder="Search asset code, title number, member, owner..."
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
          <select className="filter-select" value={statusFilter} onChange={e => setStatusFilter(e.target.value)}>
            <option value="ALL">All statuses</option>
            <option value="HELD_IN_CUSTODY">Held in Custody</option>
            <option value="RELEASED">Released</option>
            <option value="UNDER_DEFAULT_AUCTION">Under Default / Auction</option>
          </select>
          <select className="filter-select" value={typeFilter} onChange={e => setTypeFilter(e.target.value)}>
            <option value="ALL">All asset types</option>
            {ASSET_TYPES.map(type => (
              <option key={type.value} value={type.value}>{type.label}</option>
            ))}
          </select>
        </div>

        {loading ? (
          <div className="loading">Loading collateral...</div>
        ) : pageItems.length === 0 ? (
          <p className="empty-text">No collateral records match your search.</p>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Asset ID</th>
                  <th>Member Name</th>
                  <th>Asset Type</th>
                  <th>Estimated Value</th>
                  <th>Status</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {pageItems.map(asset => {
                  const member = members.find(item => item.memberId === asset.memberId)
                  return (
                    <tr key={asset.collateralId} className="clickable-row" onClick={() => openDetail(asset.collateralId)}>
                      <td>
                        <strong>{asset.assetCode}</strong>
                        <span className="cell-sub">{asset.titleNumber}</span>
                      </td>
                      <td>
                        <strong>{asset.memberName || (member ? `${member.firstName} ${member.lastName}` : 'Unknown member')}</strong>
                        <span className="cell-sub">
                          {asset.membershipNo || member?.membershipNo || '—'}
                          {asset.loanAccountNumber ? ` · ${asset.loanAccountNumber}` : ''}
                        </span>
                      </td>
                      <td>
                        {ASSET_TYPE_LABELS[asset.assetType] || asset.assetType}
                        {asset.custodianLocation && (
                          <span className="cell-sub">{asset.custodianLocation}</span>
                        )}
                      </td>
                      <td>
                        {formatMoney(asset.estimatedValue)}
                        {asset.forcedSaleValue != null && (
                          <span className="cell-sub">Force sale: {formatMoney(asset.forcedSaleValue)}</span>
                        )}
                      </td>
                      <td>
                        <span className={badgeClass(asset.status)}>
                          {COLLATERAL_STATUS_LABELS[asset.status] || asset.status}
                        </span>
                      </td>
                      <td>
                        <div className="doc-actions">
                          <button
                            type="button"
                            className="link-btn"
                            onClick={event => { event.stopPropagation(); openDetail(asset.collateralId) }}
                          >
                            View
                          </button>
                          {asset.status !== 'RELEASED' && (
                            <button
                              type="button"
                              className="link-btn"
                              onClick={event => { event.stopPropagation(); openEdit(asset) }}
                            >
                              Edit
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}

        <div className="pagination">
          <span>
            Showing {filteredAssets.length === 0 ? 0 : (page - 1) * PAGE_SIZE + 1}
            –{Math.min(page * PAGE_SIZE, filteredAssets.length)} of {filteredAssets.length}
          </span>
          <div className="pagination-controls">
            <button className="btn btn-secondary btn-sm" disabled={page === 1} onClick={() => setPage(current => current - 1)}>
              Previous
            </button>
            <span className="page-info">Page {page} of {totalPages}</span>
            <button className="btn btn-secondary btn-sm" disabled={page === totalPages} onClick={() => setPage(current => current + 1)}>
              Next
            </button>
          </div>
        </div>
      </div>

      {showForm && canManageCollateral && (
        <div className="modal-overlay collateral-form-overlay" onClick={closeForm}>
          <div className="modal collateral-form-modal" onClick={event => event.stopPropagation()}>
            <div className="modal-header">
              <h3>{editingId ? 'Edit Collateral' : 'Register Collateral'}</h3>
              <button type="button" className="modal-close" onClick={closeForm}>×</button>
            </div>
            <div className="modal-body">
              <form onSubmit={handleSubmit}>
                <h4 className="form-section-title">Asset Details</h4>
                <div className="grid-2">
                  <div className="form-group">
                    <label>Asset Type *</label>
                    <select value={form.assetType} onChange={e => updateField('assetType', e.target.value)}>
                      {ASSET_TYPES.map(type => (
                        <option key={type.value} value={type.value}>{type.label}</option>
                      ))}
                    </select>
                  </div>
                  <div className="form-group">
                    <label>Title / Registration Number *</label>
                    <input
                      value={form.titleNumber}
                      onChange={e => updateField('titleNumber', e.target.value)}
                      placeholder="e.g. Block 12 Plot 34 or UBA 123X"
                      required
                    />
                  </div>
                </div>

                <div className="form-group">
                  <label>Description</label>
                  <textarea
                    rows={2}
                    value={form.description}
                    onChange={e => updateField('description', e.target.value)}
                    placeholder="Optional description of the asset"
                  />
                </div>

                <h4 className="form-section-title">Valuation Details</h4>
                <div className="grid-3">
                  <div className="form-group">
                    <label>Estimated / Market Value (UGX) *</label>
                    <input
                      type="number"
                      min="0"
                      step="0.01"
                      value={form.estimatedValue}
                      onChange={e => updateField('estimatedValue', e.target.value)}
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label>Forced Sale Value (UGX)</label>
                    <input
                      type="number"
                      min="0"
                      step="0.01"
                      value={form.forcedSaleValue}
                      onChange={e => updateField('forcedSaleValue', e.target.value)}
                    />
                  </div>
                  <div className="form-group">
                    <label>Valuation Date</label>
                    <input
                      type="date"
                      value={form.valuationDate}
                      onChange={e => updateField('valuationDate', e.target.value)}
                    />
                  </div>
                </div>

                <div className="form-group">
                  <label>Valuer Name</label>
                  <input
                    value={form.valuerName}
                    onChange={e => updateField('valuerName', e.target.value)}
                    placeholder="Name of the registered valuer"
                  />
                </div>

                <h4 className="form-section-title">Ownership & Custody</h4>
                <div className="grid-2">
                  <div className="form-group">
                    <label>Registered Owner Name</label>
                    <input
                      value={form.registeredOwnerName}
                      onChange={e => updateField('registeredOwnerName', e.target.value)}
                      placeholder="As it appears on the legal document"
                    />
                  </div>
                  <div className="form-group">
                    <label>Current Custodian / Location</label>
                    <input
                      value={form.custodianLocation}
                      onChange={e => updateField('custodianLocation', e.target.value)}
                      placeholder="e.g. SACCO Main Safe - Locker 3"
                    />
                  </div>
                </div>

                <h4 className="form-section-title">Linked Member & Loan</h4>
                <div className="grid-2">
                  <div className="form-group">
                    <label>Member *</label>
                    <select value={form.memberId} onChange={e => updateField('memberId', e.target.value)} required>
                      <option value="">Select member...</option>
                      {members.map(member => (
                        <option key={member.memberId} value={member.memberId}>
                          {member.firstName} {member.lastName} ({member.membershipNo}) — {member.status}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div className="form-group">
                    <label>Loan Account (optional)</label>
                    <select
                      value={form.loanAccountId}
                      onChange={e => updateField('loanAccountId', e.target.value)}
                      disabled={!form.memberId || loadingLoans}
                    >
                      <option value="">Not linked to a loan yet</option>
                      {loanAccounts.map(account => (
                        <option key={account.accountId} value={account.accountId}>
                          {account.accountNumber} ({account.status})
                        </option>
                      ))}
                    </select>
                    {form.memberId && !loadingLoans && loanAccounts.length === 0 && (
                      <p className="field-hint">No loan disbursement accounts found for this member.</p>
                    )}
                    {loadingLoans && <p className="field-hint">Loading loan accounts...</p>}
                  </div>
                </div>

                {!editingId && (
                  <>
                    <h4 className="form-section-title">Documents to Attach</h4>
                    <p className="field-hint" style={{ marginBottom: 12 }}>
                      Optional but recommended: attach scans of the title deed, sale agreement, transfer form,
                      valuation report and any supporting documents (max 10MB each).
                    </p>

                    {documentRows.map((row, index) => (
                      <div className="document-row" key={index}>
                        <div className="form-group">
                          <label>Document Type</label>
                          <select
                            value={row.documentType}
                            onChange={e => updateDocumentRow(index, 'documentType', e.target.value)}
                          >
                            {DOCUMENT_TYPES.map(type => (
                              <option key={type.value} value={type.value}>{type.label}</option>
                            ))}
                          </select>
                        </div>
                        <div className="form-group">
                          <label>Document Label (optional)</label>
                          <input
                            value={row.documentName}
                            onChange={e => updateDocumentRow(index, 'documentName', e.target.value)}
                            placeholder="e.g. Original Title Deed"
                          />
                        </div>
                        <div className="form-group">
                          <label>File</label>
                          <input
                            type="file"
                            accept="image/*,.pdf,.doc,.docx"
                            onChange={e => updateDocumentRow(index, 'file', e.target.files?.[0] || null)}
                          />
                          {row.file && (
                            <p className="field-hint">
                              {row.file.name} ({formatFileSize(row.file.size)})
                            </p>
                          )}
                        </div>
                        <div className="form-group document-row-action">
                          {documentRows.length > 1 && (
                            <button type="button" className="btn btn-danger btn-sm" onClick={() => removeDocumentRow(index)}>
                              Remove
                            </button>
                          )}
                        </div>
                      </div>
                    ))}

                    <button type="button" className="btn btn-success btn-sm" onClick={addDocumentRow}>
                      + Add Another Document
                    </button>
                  </>
                )}

                {editingId && (
                  <p className="step-note">
                    Document attachments are managed from the asset view screen.
                  </p>
                )}

                <div className="form-actions">
                  <button type="button" className="btn btn-ghost" onClick={closeForm}>Cancel</button>
                  <button type="submit" className="btn btn-primary" disabled={submitting}>
                    {submitting ? 'Saving...' : editingId ? 'Save Changes' : 'Register Collateral'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}

      {selectedId && (
        <div className="modal-overlay" onClick={closeDetail}>
          <div className="modal collateral-detail-modal" onClick={event => event.stopPropagation()}>
            <div className="modal-header">
              <h3>Collateral Details</h3>
              <button type="button" className="modal-close" onClick={closeDetail}>×</button>
            </div>
            <div className="modal-body">
              {detailLoading ? (
                <div className="loading">Loading collateral...</div>
              ) : detail ? (
                <>
                  <div className="collateral-profile-header">
                    <div className="asset-avatar">
                      {(ASSET_TYPE_LABELS[detail.assetType] || detail.assetType).slice(0, 2).toUpperCase()}
                    </div>
                    <div className="profile-heading">
                      <h3>{detail.assetCode}</h3>
                      <p>
                        {detail.titleNumber} ·{' '}
                        <span className={badgeClass(detail.status)}>
                          {COLLATERAL_STATUS_LABELS[detail.status] || detail.status}
                        </span>
                      </p>
                      <div className="profile-stats">
                        <div>
                          <span>Estimated Value</span>
                          <strong>{formatMoney(detail.estimatedValue)}</strong>
                        </div>
                        <div>
                          <span>Force Sale Value</span>
                          <strong>{detail.forcedSaleValue != null ? formatMoney(detail.forcedSaleValue) : '—'}</strong>
                        </div>
                        <div>
                          <span>Member</span>
                          <strong>{detail.memberName || '—'}</strong>
                        </div>
                        <div>
                          <span>Documents</span>
                          <strong>{(detail.documents || []).length}</strong>
                        </div>
                      </div>
                    </div>
                  </div>

                  <div className="tabs">
                    {[
                      ['overview', 'Overview'],
                      ['documents', `Documents (${(detail.documents || []).length})`],
                      ['history', `History (${(detail.history || []).length})`]
                    ].map(([key, label]) => (
                      <button
                        key={key}
                        type="button"
                        className={`tab ${detailTab === key ? 'active' : ''}`}
                        onClick={() => setDetailTab(key)}
                      >
                        {label}
                      </button>
                    ))}
                  </div>

                  {detailTab === 'overview' && (
                    <div className="tab-panel">
                      <h4 className="section-title">Asset Information</h4>
                      <div className="info-grid">
                        <Info label="Asset Type" value={ASSET_TYPE_LABELS[detail.assetType] || detail.assetType} />
                        <Info label="Title / Registration No." value={detail.titleNumber} />
                        <Info label="Description" value={detail.description} />
                        <Info label="Registered Owner" value={detail.registeredOwnerName} />
                        <Info label="Custodian / Location" value={detail.custodianLocation} />
                        <Info label="Valuation Date" value={formatDate(detail.valuationDate)} />
                        <Info label="Valuer" value={detail.valuerName} />
                        <Info label="Registered At" value={formatDateTime(detail.registeredAt)} />
                        <Info label="Registered By" value={detail.registeredBy} />
                      </div>

                      <h4 className="section-title">Linkage</h4>
                      <div className="info-grid">
                        <Info label="Member" value={detail.memberName} />
                        <Info label="Membership No." value={detail.membershipNo} />
                        <Info label="Loan Account" value={detail.loanAccountNumber} />
                      </div>

                      {detail.status === 'RELEASED' && (
                        <>
                          <h4 className="section-title">Release Details</h4>
                          <div className="info-grid">
                            <Info label="Released To" value={detail.releasedTo} />
                            <Info label="Release Date" value={formatDate(detail.releaseDate)} />
                            <Info label="Authorized By" value={detail.releaseAuthorizedBy} />
                            <Info label="Authorized At" value={formatDateTime(detail.releaseAuthorizedAt)} />
                            <Info label="Signature" value={detail.releaseSignature} />
                            <Info label="Notes" value={detail.releaseNotes} />
                          </div>
                        </>
                      )}

                      {detail.status !== 'RELEASED' && canManageCollateral && (
                        <div className="action-row">
                          {detail.status === 'HELD_IN_CUSTODY' && (
                            <>
                              <button type="button" className="btn btn-success" onClick={openRelease}>
                                Authorize Release
                              </button>
                              <button type="button" className="btn btn-danger" onClick={openDefault}>
                                Mark Under Default / Auction
                              </button>
                            </>
                          )}
                          <button type="button" className="btn btn-secondary" onClick={() => openEdit(detail)}>
                            Edit Details
                          </button>
                        </div>
                      )}
                    </div>
                  )}

                  {detailTab === 'documents' && (
                    <div className="tab-panel">
                      {detail.status !== 'RELEASED' && (
                        <form className="add-doc-form" onSubmit={handleUploadDocument}>
                          <div className="grid-3">
                            <div className="form-group">
                              <label>Document Type</label>
                              <select value={uploadDocType} onChange={e => setUploadDocType(e.target.value)}>
                                {DOCUMENT_TYPES.map(type => (
                                  <option key={type.value} value={type.value}>{type.label}</option>
                                ))}
                              </select>
                            </div>
                            <div className="form-group">
                              <label>Document Label (optional)</label>
                              <input
                                value={uploadDocName}
                                onChange={e => setUploadDocName(e.target.value)}
                                placeholder="e.g. Original Title Deed"
                              />
                            </div>
                            <div className="form-group">
                              <label>File *</label>
                              <input
                                type="file"
                                accept="image/*,.pdf,.doc,.docx"
                                onChange={e => setUploadFile(e.target.files?.[0] || null)}
                                required
                              />
                              {uploadFile && (
                                <p className="field-hint">
                                  {uploadFile.name} ({formatFileSize(uploadFile.size)})
                                </p>
                              )}
                            </div>
                          </div>
                          <div className="add-doc-actions">
                            <button type="submit" className="btn btn-primary btn-sm" disabled={uploadingDoc || !uploadFile}>
                              {uploadingDoc ? 'Uploading...' : 'Upload Document'}
                            </button>
                          </div>
                        </form>
                      )}

                      {detail.status === 'RELEASED' && (
                        <p className="step-note">Released collateral records are read-only.</p>
                      )}

                      {(detail.documents || []).length === 0 ? (
                        <p className="empty-text">No documents attached.</p>
                      ) : (
                        <div className="doc-list">
                          {(detail.documents || []).map(document => (
                            <div className="doc-card" key={document.documentId}>
                              <div className="doc-card-header">
                                <strong>{DOCUMENT_TYPE_LABELS[document.documentType] || document.documentType}</strong>
                                <span className="badge badge-default">{formatFileSize(document.fileSize)}</span>
                              </div>
                              <div className="info-grid">
                                <Info label="Label" value={document.documentName} />
                                <Info label="Uploaded" value={formatDateTime(document.uploadedAt)} />
                              </div>
                              <div className="doc-actions">
                                {document.filePath && (
                                  <a
                                    className="btn btn-secondary btn-sm"
                                    href={api.fileUrl(document.filePath)}
                                    target="_blank"
                                    rel="noreferrer"
                                  >
                                    View attachment
                                  </a>
                                )}
                                {detail.status !== 'RELEASED' && (
                                  <button
                                    type="button"
                                    className="btn btn-danger btn-sm"
                                    disabled={submitting}
                                    onClick={() => handleDeleteDocument(document)}
                                  >
                                    Delete
                                  </button>
                                )}
                              </div>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  )}

                  {detailTab === 'history' && (
                    <div className="tab-panel">
                      {(detail.history || []).length === 0 ? (
                        <p className="empty-text">No history recorded.</p>
                      ) : (
                        <div className="history-list">
                          {(detail.history || []).map(entry => (
                            <div className="history-item" key={entry.historyId}>
                              <span className="history-dot" />
                              <div className="history-content">
                                <div className="history-action">
                                  {entry.action.replace(/_/g, ' ')}
                                  {entry.fromStatus && entry.toStatus && entry.fromStatus !== entry.toStatus && (
                                    <span className="history-transition">
                                      {' '}· {entry.fromStatus} → {entry.toStatus}
                                    </span>
                                  )}
                                </div>
                                <div className="activity-meta">
                                  {formatDateTime(entry.createdAt)}
                                  {entry.authorizedBy ? ` · Authorized by ${entry.authorizedBy}` : ''}
                                </div>
                                {entry.signature && (
                                  <div className="activity-reason">Signature: {entry.signature}</div>
                                )}
                                {entry.notes && (
                                  <div className="activity-reason">{entry.notes}</div>
                                )}
                              </div>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  )}
                </>
              ) : (
                <div className="error-message">Could not load collateral details.</div>
              )}
            </div>
          </div>
        </div>
      )}

      {releaseOpen && (
        <div className="modal-overlay" onClick={() => setReleaseOpen(false)}>
          <div className="modal collateral-release-modal" onClick={event => event.stopPropagation()}>
            <div className="modal-header">
              <h3>Authorize Release</h3>
              <button type="button" className="modal-close" onClick={() => setReleaseOpen(false)}>×</button>
            </div>
            <div className="modal-body">
              <p className="step-note">
                This action changes the asset status to RELEASED and records the sign-off in the audit trail.
              </p>
              <form onSubmit={handleRelease}>
                <div className="form-group">
                  <label>Released To</label>
                  <input
                    value={releaseForm.releasedTo}
                    onChange={e => setReleaseForm(prev => ({ ...prev, releasedTo: e.target.value }))}
                    placeholder="Member name"
                  />
                </div>
                <div className="form-group">
                  <label>Release Date</label>
                  <input
                    type="date"
                    value={releaseForm.releaseDate}
                    onChange={e => setReleaseForm(prev => ({ ...prev, releaseDate: e.target.value }))}
                  />
                </div>
                <div className="form-group">
                  <label>Authorized By (Manager) *</label>
                  <input
                    value={releaseForm.authorizedBy}
                    onChange={e => setReleaseForm(prev => ({ ...prev, authorizedBy: e.target.value }))}
                    placeholder="Manager name"
                    required
                  />
                </div>
                <div className="form-group">
                  <label>Signature (type full name) *</label>
                  <input
                    value={releaseForm.signature}
                    onChange={e => setReleaseForm(prev => ({ ...prev, signature: e.target.value }))}
                    placeholder="Type your full name as signature"
                    required
                  />
                </div>
                <div className="form-group">
                  <label>Notes</label>
                  <textarea
                    rows={2}
                    value={releaseForm.notes}
                    onChange={e => setReleaseForm(prev => ({ ...prev, notes: e.target.value }))}
                    placeholder="Reason for release (e.g. loan cleared in full)"
                  />
                </div>
                <div className="form-actions">
                  <button type="button" className="btn btn-ghost" onClick={() => setReleaseOpen(false)}>Cancel</button>
                  <button type="submit" className="btn btn-success" disabled={submitting}>
                    {submitting ? 'Releasing...' : 'Confirm Release'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}

      {defaultOpen && (
        <div className="modal-overlay" onClick={() => setDefaultOpen(false)}>
          <div className="modal collateral-release-modal" onClick={event => event.stopPropagation()}>
            <div className="modal-header">
              <h3>Mark Under Default / Auction</h3>
              <button type="button" className="modal-close" onClick={() => setDefaultOpen(false)}>×</button>
            </div>
            <div className="modal-body">
              <p className="step-note">
                Use this when the linked loan is in arrears and the asset is being held for recovery.
              </p>
              <form onSubmit={handleMarkDefault}>
                <div className="form-group">
                  <label>Reason *</label>
                  <textarea
                    rows={3}
                    value={defaultReason}
                    onChange={e => setDefaultReason(e.target.value)}
                    placeholder="e.g. Loan arrears for 90 days; recovery initiated"
                    required
                  />
                </div>
                <div className="form-actions">
                  <button type="button" className="btn btn-ghost" onClick={() => setDefaultOpen(false)}>Cancel</button>
                  <button type="submit" className="btn btn-danger" disabled={submitting}>
                    {submitting ? 'Saving...' : 'Confirm'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default Collateral
