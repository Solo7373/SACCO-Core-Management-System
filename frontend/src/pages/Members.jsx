import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { api } from '../services/api'

const DOCUMENT_TYPES = [
  { value: 'NATIONAL_ID', label: 'National ID (NIN)' },
  { value: 'PASSPORT', label: 'Passport' },
  { value: 'REFUGEE_ID', label: 'Refugee ID Card' },
  { value: 'OTHER', label: 'Other Identity Document' }
]

const ASSET_TYPE_LABELS = {
  LAND_TITLE: 'Land Title',
  RESIDENTIAL_HOUSE: 'Residential House',
  COMMERCIAL_BUILDING: 'Commercial Building',
  MOTOR_VEHICLE: 'Motor Vehicle',
  EQUIPMENT: 'Equipment',
  OTHER: 'Other Asset'
}

const COLLATERAL_STATUS_LABELS = {
  HELD_IN_CUSTODY: 'Held in Custody',
  RELEASED: 'Released',
  UNDER_DEFAULT_AUCTION: 'Under Default / Auction'
}

const PAGE_SIZE = 8

function emptyDocument() {
  return {
    documentType: 'NATIONAL_ID',
    documentNumber: '',
    issuer: '',
    issueDate: '',
    expiryDate: '',
    file: null
  }
}

function initialForm() {
  return {
    firstName: '',
    lastName: '',
    dateOfBirth: '',
    gender: '',
    maritalStatus: '',
    phone: '',
    email: '',
    address: '',
    nextOfKinName: '',
    nextOfKinRelationship: '',
    nextOfKinPhone: '',
    nextOfKinAddress: '',
    documents: [emptyDocument()]
  }
}

function profileToEditForm(member) {
  return {
    firstName: member.firstName || '',
    lastName: member.lastName || '',
    dateOfBirth: member.dateOfBirth || '',
    gender: member.gender || '',
    maritalStatus: member.maritalStatus || '',
    phone: member.phone || '',
    email: member.email || '',
    address: member.address || '',
    nextOfKinName: member.nextOfKinName || '',
    nextOfKinRelationship: member.nextOfKinRelationship || '',
    nextOfKinPhone: member.nextOfKinPhone || '',
    nextOfKinAddress: member.nextOfKinAddress || ''
  }
}

function formatDate(value) {
  if (!value) return '—'
  return new Date(value).toLocaleDateString()
}

function formatMoney(value) {
  return `UGX ${Number(value || 0).toLocaleString()}`
}

function formatFileSize(bytes) {
  if (!bytes) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function badgeClass(status) {
  const map = {
    ACTIVE: 'success',
    COMPLETED: 'success',
    VERIFIED: 'success',
    RELEASED: 'success',
    PENDING: 'warning',
    HELD_IN_CUSTODY: 'warning',
    FROZEN: 'danger',
    SUSPENDED: 'danger',
    REJECTED: 'danger',
    UNDER_DEFAULT_AUCTION: 'danger',
    CANCELLED: 'default',
    DORMANT: 'default',
    CLOSED: 'default'
  }
  return `badge badge-${map[status] || 'default'}`
}

function documentLabel(type) {
  return DOCUMENT_TYPES.find(item => item.value === type)?.label || type
}

function Info({ label, value }) {
  return (
    <div className="info-row">
      <span>{label}</span>
      <strong>{value || '—'}</strong>
    </div>
  )
}

function Members() {
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()

  const [members, setMembers] = useState([])
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)
  const [success, setSuccess] = useState(null)

  const [showForm, setShowForm] = useState(searchParams.get('new') === '1')
  const [step, setStep] = useState(1)
  const [form, setForm] = useState(initialForm)
  const [photoFile, setPhotoFile] = useState(null)
  const [photoPreview, setPhotoPreview] = useState(null)

  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [page, setPage] = useState(1)

  const [selectedMemberId, setSelectedMemberId] = useState(null)
  const [profile, setProfile] = useState(null)
  const [profileLoading, setProfileLoading] = useState(false)
  const [profileTab, setProfileTab] = useState('overview')
  const [profileAction, setProfileAction] = useState(null)
  const [showAddDoc, setShowAddDoc] = useState(false)
  const [newDoc, setNewDoc] = useState(emptyDocument)

  const role = localStorage.getItem('role') || ''
  const canRegisterMember = ['ADMIN', 'RECOVERY_ADMIN', 'MANAGER', 'TELLER', 'LOAN_OFFICER'].includes(role)
  const canEditMember = ['ADMIN', 'RECOVERY_ADMIN', 'MANAGER', 'AUDITOR'].includes(role)
  const canArchiveMember = ['ADMIN', 'RECOVERY_ADMIN', 'MANAGER', 'AUDITOR'].includes(role)
  const canRestoreMember = ['ADMIN', 'RECOVERY_ADMIN', 'MANAGER', 'AUDITOR'].includes(role)
  const canManageCredentials = ['ADMIN', 'RECOVERY_ADMIN', 'MANAGER'].includes(role)
  const [viewMode, setViewMode] = useState('active')
  const [archivedMembers, setArchivedMembers] = useState([])
  const [archiveTarget, setArchiveTarget] = useState(null)
  const [archiveReason, setArchiveReason] = useState('')
  const [archiveAction, setArchiveAction] = useState(null)
  const [editForm, setEditForm] = useState(null)
  const [memberUser, setMemberUser] = useState(null)
  const [userForm, setUserForm] = useState({ username: '', password: '', email: '', isActive: true })

  useEffect(() => {
    loadMembers()
  }, [])

  useEffect(() => {
    if (searchParams.get('new') === '1') {
      setShowForm(true)
      setStep(1)
    }
  }, [searchParams])

  useEffect(() => {
    if (!success) return
    const timer = setTimeout(() => setSuccess(null), 5000)
    return () => clearTimeout(timer)
  }, [success])

  const filteredMembers = useMemo(() => {
    const source = viewMode === 'archived' ? archivedMembers : members
    const query = search.trim().toLowerCase()
    return source.filter(member => {
      const haystack = [
        member.firstName,
        member.lastName,
        member.membershipNo,
        member.phone,
        member.email
      ].filter(Boolean).join(' ').toLowerCase()

      const matchesSearch = !query || haystack.includes(query)
      const matchesStatus = viewMode === 'archived'
        || statusFilter === 'ALL'
        || member.status === statusFilter
      return matchesSearch && matchesStatus
    })
  }, [members, archivedMembers, viewMode, search, statusFilter])

  const totalPages = Math.max(1, Math.ceil(filteredMembers.length / PAGE_SIZE))
  const pageItems = filteredMembers.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE)

  useEffect(() => {
    setPage(1)
  }, [search, statusFilter, viewMode])

  useEffect(() => {
    if (page > totalPages) setPage(totalPages)
  }, [page, totalPages])

  async function loadMembers() {
    setLoading(true)
    try {
      const data = await api.getMembers()
      setMembers(data || [])
      if (canRestoreMember) {
        try {
          const archived = await api.getArchivedMembers()
          setArchivedMembers(archived || [])
        } catch (archivedError) {
          setArchivedMembers([])
        }
      }
      setError(null)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  function updateField(field, value) {
    setForm(prev => ({ ...prev, [field]: value }))
  }

  function updateDocument(index, field, value) {
    setForm(prev => ({
      ...prev,
      documents: prev.documents.map((doc, i) => i === index ? { ...doc, [field]: value } : doc)
    }))
  }

  function addDocument() {
    setForm(prev => ({ ...prev, documents: [...prev.documents, emptyDocument()] }))
  }

  function removeDocument(index) {
    setForm(prev => ({
      ...prev,
      documents: prev.documents.filter((_, i) => i !== index)
    }))
  }

  function handlePhotoChange(event) {
    const file = event.target.files?.[0]
    if (!file) return
    if (file.size > 10 * 1024 * 1024) {
      setError('Passport photo must be 10MB or smaller.')
      return
    }
    setError(null)
    setPhotoFile(file)
    setPhotoPreview(URL.createObjectURL(file))
  }

  function validateStep(currentStep) {
    if (currentStep === 1) {
      if (!form.firstName.trim() || !form.lastName.trim()) {
        return 'First name and last name are required.'
      }
      if (form.phone && !/^[+]?[0-9\s-]{7,15}$/.test(form.phone)) {
        return 'Enter a valid phone number.'
      }
      if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
        return 'Enter a valid email address.'
      }
    }

    if (currentStep === 2) {
      const validDocuments = form.documents.filter(doc => doc.documentNumber.trim() !== '')
      if (validDocuments.length === 0) {
        return 'At least one identity document with a number is required.'
      }
    }

    if (currentStep === 3) {
      if (!form.nextOfKinName.trim()) return 'Next of kin name is required.'
      if (!form.nextOfKinRelationship.trim()) return 'Next of kin relationship is required.'
      if (!form.nextOfKinPhone.trim()) return 'Next of kin phone is required.'
    }

    return null
  }

  function nextStep() {
    const message = validateStep(step)
    if (message) {
      setError(message)
      return
    }
    setError(null)
    setStep(current => Math.min(3, current + 1))
  }

  function resetForm() {
    setForm(initialForm())
    setPhotoFile(null)
    setPhotoPreview(null)
    setStep(1)
  }

  function closeForm() {
    setShowForm(false)
    resetForm()
    setError(null)
    if (searchParams.get('new')) {
      const next = new URLSearchParams(searchParams)
      next.delete('new')
      setSearchParams(next, { replace: true })
    }
  }

  async function handleSubmit(event) {
    event.preventDefault()
    const message = validateStep(1) || validateStep(2) || validateStep(3)
    if (message) {
      setError(message)
      return
    }

    setSubmitting(true)
    setError(null)

    try {
      const validDocuments = form.documents.filter(doc => doc.documentNumber.trim() !== '')
      const member = await api.registerMemberWithDocuments({
        firstName: form.firstName,
        lastName: form.lastName,
        dateOfBirth: form.dateOfBirth || null,
        gender: form.gender || null,
        maritalStatus: form.maritalStatus || null,
        phone: form.phone,
        email: form.email,
        address: form.address,
        nextOfKinName: form.nextOfKinName,
        nextOfKinRelationship: form.nextOfKinRelationship,
        nextOfKinPhone: form.nextOfKinPhone,
        nextOfKinAddress: form.nextOfKinAddress,
        identityDocuments: validDocuments.map(doc => ({
          documentType: doc.documentType,
          documentNumber: doc.documentNumber,
          issuer: doc.issuer,
          issueDate: doc.issueDate || null,
          expiryDate: doc.expiryDate || null
        }))
      })

      const warnings = []

      if (photoFile) {
        try {
          await api.uploadMemberPhoto(member.memberId, photoFile)
        } catch (uploadError) {
          warnings.push('passport photo')
        }
      }

      const documentsWithFiles = form.documents.filter(doc => doc.file && doc.documentNumber.trim() !== '')
      if (documentsWithFiles.length > 0) {
        try {
          const savedDocuments = await api.getMemberDocuments(member.memberId)
          for (const localDocument of documentsWithFiles) {
            const savedDocument = savedDocuments.find(doc =>
              doc.documentNumber === localDocument.documentNumber &&
              doc.documentType === localDocument.documentType
            )
            if (savedDocument) {
              await api.uploadDocumentFile(member.memberId, savedDocument.documentId, localDocument.file)
            }
          }
        } catch (uploadError) {
          warnings.push('document attachments')
        }
      }

      setSuccess(
        warnings.length
          ? `Member registered, but some ${warnings.join(' and ')} could not be uploaded.`
          : 'Member registered successfully!'
      )
      closeForm()
      await loadMembers()
    } catch (err) {
      setError(err.message)
    } finally {
      setSubmitting(false)
    }
  }

  async function openProfile(memberId) {
    setSelectedMemberId(memberId)
    setProfile(null)
    setProfileTab('overview')
    setProfileLoading(true)
    setEditForm(null)
    setMemberUser(null)
    try {
      const data = await api.getMemberProfile(memberId)
      setProfile(data)
      setEditForm(profileToEditForm(data.member))
      if (canManageCredentials) {
        try {
          const user = await api.getMemberUser(memberId)
          setMemberUser(user)
          setUserForm(user
            ? {
                username: user.username || '',
                password: '',
                email: user.email || '',
                isActive: user.isActive !== false
              }
            : {
                username: '',
                password: '',
                email: data.member.email || '',
                isActive: true
              })
        } catch (userErr) {
          setMemberUser(null)
          setUserForm({
            username: '',
            password: '',
            email: data.member.email || '',
            isActive: true
          })
        }
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setProfileLoading(false)
    }
  }

  function closeProfile() {
    setSelectedMemberId(null)
    setProfile(null)
    setEditForm(null)
    setMemberUser(null)
    setUserForm({ username: '', password: '', email: '', isActive: true })
    setShowAddDoc(false)
    setNewDoc(emptyDocument())
    setProfileAction(null)
  }

  async function handleArchive() {
    if (!archiveTarget) return
    if (!archiveReason.trim()) {
      setError('Enter a reason for archiving this member.')
      return
    }
    setArchiveAction('archive')
    setError(null)
    setSuccess(null)
    try {
      await api.archiveMember(archiveTarget.memberId, archiveReason.trim())
      const name = `${archiveTarget.firstName} ${archiveTarget.lastName}`
      setSuccess(
        `${name} archived. All financial history and foreign keys are preserved; ` +
        'the linked login has been sealed and requires an administrator password reset after restore.'
      )
      if (selectedMemberId === archiveTarget.memberId) {
        closeProfile()
      }
      setArchiveTarget(null)
      setArchiveReason('')
      await loadMembers()
    } catch (err) {
      setError(err.message)
    } finally {
      setArchiveAction(null)
    }
  }

  async function handleRestore(member) {
    const name = `${member.firstName} ${member.lastName}`
    if (!window.confirm(
      `Restore ${name}? The member will become active, but an administrator must reset ` +
      'the login password before the member can sign in.'
    )) {
      return
    }
    setArchiveAction(`restore-${member.memberId}`)
    setError(null)
    setSuccess(null)
    try {
      await api.restoreMember(member.memberId)
      setSuccess(
        `${name} restored to active status. Financial history is intact. ` +
        'A password reset is required before the member can sign in.'
      )
      await loadMembers()
    } catch (err) {
      setError(err.message)
    } finally {
      setArchiveAction(null)
    }
  }

  async function reloadProfile() {
    if (!selectedMemberId) return
    const data = await api.getMemberProfile(selectedMemberId)
    setProfile(data)
    setMembers(prev => prev.map(member => member.memberId === data.member.memberId ? data.member : member))
  }

  async function handleSaveProfile(event) {
    event.preventDefault()
    if (!selectedMemberId || !editForm) return
    setProfileAction('save-profile')
    setError(null)
    setSuccess(null)
    try {
      const updated = await api.updateMember(selectedMemberId, editForm)
      setProfile(prev => prev ? { ...prev, member: updated } : prev)
      setMembers(prev => prev.map(member => member.memberId === updated.memberId ? updated : member))
      setEditForm(profileToEditForm(updated))
      setSuccess('Member personal and contact details updated.')
    } catch (err) {
      setError(err.message)
    } finally {
      setProfileAction(null)
    }
  }

  async function handleSaveCredentials(event) {
    event.preventDefault()
    if (!selectedMemberId) return
    if (!memberUser && (!userForm.password || userForm.password.length < 8)) {
      setError('Enter a password of at least 8 characters to create the member login.')
      return
    }
    setProfileAction('save-credentials')
    setError(null)
    setSuccess(null)
    try {
      const saved = await api.saveMemberUser(selectedMemberId, {
        username: userForm.username,
        password: userForm.password || null,
        email: userForm.email || null,
        isActive: userForm.isActive
      })
      setMemberUser(saved)
      setUserForm({
        username: saved.username || '',
        password: '',
        email: saved.email || '',
        isActive: saved.isActive !== false
      })
      setSuccess(memberUser ? 'Member login credentials updated.' : 'Member login created.')
    } catch (err) {
      setError(err.message)
    } finally {
      setProfileAction(null)
    }
  }

  async function handleProfilePhotoUpload(event) {
    const file = event.target.files?.[0]
    if (!file || !selectedMemberId) return
    setProfileAction('photo')
    setError(null)
    try {
      const updated = await api.uploadMemberPhoto(selectedMemberId, file)
      setMembers(prev => prev.map(member => member.memberId === updated.memberId ? updated : member))
      setProfile(prev => prev ? { ...prev, member: updated } : prev)
      setSuccess('Passport photo updated.')
    } catch (err) {
      setError(err.message)
    } finally {
      setProfileAction(null)
      event.target.value = ''
    }
  }

  async function handleProfileDocumentUpload(documentId, event) {
    const file = event.target.files?.[0]
    if (!file || !selectedMemberId) return
    setProfileAction(`doc-${documentId}`)
    setError(null)
    try {
      await api.uploadDocumentFile(selectedMemberId, documentId, file)
      await reloadProfile()
      setSuccess('Document attachment uploaded.')
    } catch (err) {
      setError(err.message)
    } finally {
      setProfileAction(null)
      event.target.value = ''
    }
  }

  async function handleDocumentOutcome(documentId, outcome) {
    if (!selectedMemberId) return
    setProfileAction(`verify-${documentId}`)
    setError(null)
    try {
      await api.verifyDocument({
        documentId,
        method: 'MANUAL_REVIEW',
        outcome,
        evidenceReference: 'Reviewed from member profile'
      })
      await reloadProfile()
      setSuccess(outcome === 'VERIFIED' ? 'Document verified.' : 'Document rejected.')
    } catch (err) {
      setError(err.message)
    } finally {
      setProfileAction(null)
    }
  }

  function updateNewDoc(field, value) {
    setNewDoc(prev => ({ ...prev, [field]: value }))
  }

  async function handleAddDocument(event) {
    event.preventDefault()
    if (!selectedMemberId) return
    if (!newDoc.documentNumber.trim()) {
      setError('Document number is required.')
      return
    }
    setProfileAction('add-doc')
    setError(null)
    try {
      await api.addIdentityDocument(selectedMemberId, {
        documentType: newDoc.documentType,
        documentNumber: newDoc.documentNumber,
        issuer: newDoc.issuer,
        issueDate: newDoc.issueDate || null,
        expiryDate: newDoc.expiryDate || null
      })
      setShowAddDoc(false)
      setNewDoc(emptyDocument())
      await reloadProfile()
      setSuccess('Identity document added.')
    } catch (err) {
      setError(err.message)
    } finally {
      setProfileAction(null)
    }
  }

  return (
    <div>
      <div className="page-header dashboard-header">
        <div>
          <h2>Members</h2>
          <p className="page-subtitle">Register, verify and manage SACCO members</p>
        </div>
        <div className="header-actions">
          <span className="count-chip">
            {viewMode === 'archived'
              ? `${archivedMembers.length} archived`
              : `${members.length} member${members.length === 1 ? '' : 's'}`}
          </span>
          {canRegisterMember && viewMode === 'active' && (
            <button className="btn btn-primary" onClick={() => { setShowForm(true); setStep(1); setError(null) }}>
              + Register Member
            </button>
          )}
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}
      {success && <div className="success-message">{success}</div>}

      {canRestoreMember && (
        <div className="tabs member-view-tabs">
          <button
            type="button"
            className={`tab ${viewMode === 'active' ? 'active' : ''}`}
            onClick={() => setViewMode('active')}
          >
            Active Members
          </button>
          <button
            type="button"
            className={`tab ${viewMode === 'archived' ? 'active' : ''}`}
            onClick={() => setViewMode('archived')}
          >
            Archived / Soft-deleted ({archivedMembers.length})
          </button>
        </div>
      )}

      {showForm && (
        <div className="card wizard-card">
          <div className="wizard-header">
            <div>
              <h3>Register New Member</h3>
              <p className="card-subtitle">Complete the three steps below. Fields marked * are required.</p>
            </div>
            <button className="modal-close" type="button" onClick={closeForm}>×</button>
          </div>

          <div className="wizard-steps">
            {['Personal Details', 'KYC & Documents', 'Next of Kin'].map((label, index) => {
              const number = index + 1
              const state = step === number ? 'active' : step > number ? 'completed' : ''
              return (
                <div className={`wizard-step ${state}`} key={label}>
                  <span className="wizard-number">{step > number ? '✓' : number}</span>
                  <span>{label}</span>
                </div>
              )
            })}
          </div>

          <form onSubmit={handleSubmit}>
            {step === 1 && (
              <div className="wizard-panel">
                <div className="photo-upload">
                  <div className="photo-preview">
                    {photoPreview
                      ? <img src={photoPreview} alt="Passport preview" />
                      : <span>No photo</span>}
                  </div>
                  <div>
                    <label className="btn btn-secondary btn-sm upload-label">
                      {photoFile ? 'Change Photo' : 'Upload Passport Photo'}
                      <input type="file" accept="image/*" hidden onChange={handlePhotoChange} />
                    </label>
                    <p className="field-hint">JPG or PNG, maximum 10MB.</p>
                  </div>
                </div>

                <div className="grid-2">
                  <div className="form-group">
                    <label>First Name *</label>
                    <input value={form.firstName} onChange={e => updateField('firstName', e.target.value)} required />
                  </div>
                  <div className="form-group">
                    <label>Last Name *</label>
                    <input value={form.lastName} onChange={e => updateField('lastName', e.target.value)} required />
                  </div>
                </div>

                <div className="grid-3">
                  <div className="form-group">
                    <label>Date of Birth</label>
                    <input type="date" value={form.dateOfBirth} onChange={e => updateField('dateOfBirth', e.target.value)} />
                  </div>
                  <div className="form-group">
                    <label>Gender</label>
                    <select value={form.gender} onChange={e => updateField('gender', e.target.value)}>
                      <option value="">Select...</option>
                      <option value="MALE">Male</option>
                      <option value="FEMALE">Female</option>
                      <option value="OTHER">Other</option>
                    </select>
                  </div>
                  <div className="form-group">
                    <label>Marital Status</label>
                    <select value={form.maritalStatus} onChange={e => updateField('maritalStatus', e.target.value)}>
                      <option value="">Select...</option>
                      <option value="SINGLE">Single</option>
                      <option value="MARRIED">Married</option>
                      <option value="DIVORCED">Divorced</option>
                      <option value="WIDOWED">Widowed</option>
                    </select>
                  </div>
                </div>

                <div className="grid-2">
                  <div className="form-group">
                    <label>Phone Number</label>
                    <input value={form.phone} onChange={e => updateField('phone', e.target.value)} placeholder="+256..." />
                  </div>
                  <div className="form-group">
                    <label>Email</label>
                    <input type="email" value={form.email} onChange={e => updateField('email', e.target.value)} />
                  </div>
                </div>

                <div className="form-group">
                  <label>Physical Address</label>
                  <textarea rows={2} value={form.address} onChange={e => updateField('address', e.target.value)} />
                </div>
              </div>
            )}

            {step === 2 && (
              <div className="wizard-panel">
                <div className="step-note">
                  At least one identity document is mandatory. Attach a scan or photo of the document where possible.
                </div>

                {form.documents.map((doc, index) => (
                  <div className="document-card" key={index}>
                    <div className="document-card-header">
                      <span>Document {index + 1}</span>
                      {form.documents.length > 1 && (
                        <button type="button" className="btn btn-danger btn-sm" onClick={() => removeDocument(index)}>
                          Remove
                        </button>
                      )}
                    </div>

                    <div className="grid-2">
                      <div className="form-group">
                        <label>Document Type</label>
                        <select value={doc.documentType} onChange={e => updateDocument(index, 'documentType', e.target.value)}>
                          {DOCUMENT_TYPES.map(type => (
                            <option key={type.value} value={type.value}>{type.label}</option>
                          ))}
                        </select>
                      </div>
                      <div className="form-group">
                        <label>Document Number *</label>
                        <input
                          value={doc.documentNumber}
                          onChange={e => updateDocument(index, 'documentNumber', e.target.value)}
                          placeholder="e.g. CM12345678"
                        />
                      </div>
                    </div>

                    <div className="grid-2">
                      <div className="form-group">
                        <label>Issuing Authority</label>
                        <input
                          value={doc.issuer}
                          onChange={e => updateDocument(index, 'issuer', e.target.value)}
                          placeholder="e.g. NIRA, Passport Office"
                        />
                      </div>
                      <div className="form-group">
                        <label>Issue Date</label>
                        <input type="date" value={doc.issueDate} onChange={e => updateDocument(index, 'issueDate', e.target.value)} />
                      </div>
                    </div>

                    <div className="grid-2">
                      <div className="form-group">
                        <label>Expiry Date</label>
                        <input type="date" value={doc.expiryDate} onChange={e => updateDocument(index, 'expiryDate', e.target.value)} />
                      </div>
                      <div className="form-group">
                        <label>Scan / Photo of Document</label>
                        <input
                          type="file"
                          accept="image/*,.pdf"
                          onChange={e => updateDocument(index, 'file', e.target.files?.[0] || null)}
                        />
                        {doc.file && (
                          <p className="field-hint">{doc.file.name} ({formatFileSize(doc.file.size)})</p>
                        )}
                      </div>
                    </div>
                  </div>
                ))}

                <button type="button" className="btn btn-success btn-sm" onClick={addDocument}>
                  + Add Another Document
                </button>
              </div>
            )}

            {step === 3 && (
              <div className="wizard-panel">
                <h4 className="section-title">Next of Kin</h4>
                <div className="grid-2">
                  <div className="form-group">
                    <label>Full Name *</label>
                    <input value={form.nextOfKinName} onChange={e => updateField('nextOfKinName', e.target.value)} required />
                  </div>
                  <div className="form-group">
                    <label>Relationship *</label>
                    <input
                      value={form.nextOfKinRelationship}
                      onChange={e => updateField('nextOfKinRelationship', e.target.value)}
                      placeholder="e.g. Spouse, Parent, Sibling"
                      required
                    />
                  </div>
                </div>

                <div className="grid-2">
                  <div className="form-group">
                    <label>Phone Number *</label>
                    <input value={form.nextOfKinPhone} onChange={e => updateField('nextOfKinPhone', e.target.value)} required />
                  </div>
                  <div className="form-group">
                    <label>Address</label>
                    <input value={form.nextOfKinAddress} onChange={e => updateField('nextOfKinAddress', e.target.value)} />
                  </div>
                </div>

                <h4 className="section-title">Review</h4>
                <div className="review-grid">
                  <Info label="Name" value={`${form.firstName} ${form.lastName}`.trim()} />
                  <Info label="Phone" value={form.phone} />
                  <Info label="Email" value={form.email} />
                  <Info label="Gender" value={form.gender} />
                  <Info label="Marital Status" value={form.maritalStatus} />
                  <Info label="Identity Documents" value={String(form.documents.filter(doc => doc.documentNumber.trim()).length)} />
                  <Info label="Next of Kin" value={form.nextOfKinName ? `${form.nextOfKinName} (${form.nextOfKinRelationship})` : ''} />
                  <Info label="Passport Photo" value={photoFile ? 'Attached' : 'Not attached'} />
                </div>
              </div>
            )}

            <div className="wizard-nav">
              <button type="button" className="btn btn-ghost" onClick={closeForm}>Cancel</button>
              <div className="wizard-nav-right">
                {step > 1 && (
                  <button type="button" className="btn btn-secondary" onClick={() => setStep(current => current - 1)}>
                    Back
                  </button>
                )}
                {step < 3 && (
                  <button type="button" className="btn btn-primary" onClick={nextStep}>
                    Continue
                  </button>
                )}
                {step === 3 && (
                  <button type="submit" className="btn btn-success" disabled={submitting}>
                    {submitting ? 'Registering...' : 'Register Member'}
                  </button>
                )}
              </div>
            </div>
          </form>
        </div>
      )}

      <div className="card">
        <div className="card-heading">
          <div>
            <h3>{viewMode === 'archived' ? 'Archived Members' : 'Member List'}</h3>
            <p className="card-subtitle">
              {filteredMembers.length} {viewMode === 'archived' ? 'archived ' : ''}
              member{filteredMembers.length === 1 ? '' : 's'} found
            </p>
          </div>
        </div>

        {viewMode === 'archived' && (
          <div className="restore-password-note">
            Soft-deleted members keep every financial record and foreign key. Restoring re-enables
            the account, but an administrator must reset the login password before the member can
            sign in.
          </div>
        )}

        <div className="toolbar">
          <input
            className="search-input"
            placeholder="Search name, member no, phone or email..."
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
          {viewMode === 'active' && (
            <select className="filter-select" value={statusFilter} onChange={e => setStatusFilter(e.target.value)}>
              <option value="ALL">All statuses</option>
              <option value="ACTIVE">Active</option>
              <option value="PENDING">Pending</option>
              <option value="SUSPENDED">Suspended</option>
              <option value="REJECTED">Rejected</option>
              <option value="CLOSED">Closed</option>
              <option value="INACTIVE">Inactive / Archived</option>
            </select>
          )}
        </div>

        {loading ? (
          <div className="loading">Loading members...</div>
        ) : pageItems.length === 0 ? (
          <p className="empty-text">
            {viewMode === 'archived'
              ? 'No archived members. Soft-deleted members appear here for one-click restoration.'
              : 'No members match your search.'}
          </p>
        ) : viewMode === 'archived' ? (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Member</th>
                  <th>Member No</th>
                  <th>Contact</th>
                  <th>Archived On</th>
                  <th>Reason</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {pageItems.map(member => (
                  <tr key={member.memberId}>
                    <td>
                      <div className="member-cell">
                        <span className="table-avatar">
                          {(member.firstName?.[0] || '') + (member.lastName?.[0] || '')}
                        </span>
                        <div>
                          <strong>{member.firstName} {member.lastName}</strong>
                          <span className="cell-sub">Status: {member.status}</span>
                        </div>
                      </div>
                    </td>
                    <td>{member.membershipNo}</td>
                    <td>
                      <div>{member.phone || '—'}</div>
                      <span className="cell-sub">{member.email || 'Email removed on archive'}</span>
                    </td>
                    <td>{formatDate(member.deletedAt)}</td>
                    <td className="archive-reason-cell">{member.deletionReason || '—'}</td>
                    <td>
                      {canRestoreMember && (
                        <button
                          type="button"
                          className="btn btn-success btn-sm"
                          disabled={archiveAction === `restore-${member.memberId}`}
                          onClick={() => handleRestore(member)}
                        >
                          {archiveAction === `restore-${member.memberId}` ? 'Restoring…' : 'Restore'}
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Member</th>
                  <th>Member No</th>
                  <th>Contact</th>
                  <th>KYC</th>
                  <th>Status</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {pageItems.map(member => (
                  <tr key={member.memberId} className="clickable-row" onClick={() => openProfile(member.memberId)}>
                    <td>
                      <div className="member-cell">
                        {member.passportPhotoPath ? (
                          <img className="table-photo" src={api.fileUrl(member.passportPhotoPath)} alt="" />
                        ) : (
                          <span className="table-avatar">
                            {(member.firstName?.[0] || '') + (member.lastName?.[0] || '')}
                          </span>
                        )}
                        <div>
                          <strong>{member.firstName} {member.lastName}</strong>
                          <span className="cell-sub">
                            {member.gender || 'Gender not set'} · {member.dateOfBirth ? formatDate(member.dateOfBirth) : 'DOB not set'}
                          </span>
                        </div>
                      </div>
                    </td>
                    <td>{member.membershipNo}</td>
                    <td>
                      <div>{member.phone || '—'}</div>
                      <span className="cell-sub">{member.email || 'No email'}</span>
                    </td>
                    <td>
                      {member.nextOfKinName
                        ? <span className="badge badge-info">Next of kin</span>
                        : <span className="badge badge-default">Incomplete</span>}
                    </td>
                    <td><span className={badgeClass(member.status)}>{member.status}</span></td>
                    <td>
                      <div className="row-actions">
                        <button
                          className="link-btn"
                          onClick={event => { event.stopPropagation(); openProfile(member.memberId) }}
                        >
                          View
                        </button>
                        {canArchiveMember && (
                          <button
                            type="button"
                            className="link-btn link-danger"
                            onClick={event => {
                              event.stopPropagation()
                              setArchiveTarget(member)
                              setArchiveReason('')
                              setError(null)
                              setSuccess(null)
                            }}
                          >
                            Archive
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <div className="pagination">
          <span>
            Showing {filteredMembers.length === 0 ? 0 : (page - 1) * PAGE_SIZE + 1}
            –{Math.min(page * PAGE_SIZE, filteredMembers.length)} of {filteredMembers.length}
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

      {archiveTarget && (
        <div
          className="modal-overlay"
          onClick={() => { if (archiveAction !== 'archive') setArchiveTarget(null) }}
        >
          <div className="modal" onClick={event => event.stopPropagation()}>
            <div className="modal-header">
              <h3>Archive Member</h3>
              <button
                type="button"
                className="modal-close"
                disabled={archiveAction === 'archive'}
                onClick={() => setArchiveTarget(null)}
              >
                ×
              </button>
            </div>
            <div className="modal-body">
              <div className="restore-password-note">
                Archiving is a soft delete. <strong>{archiveTarget.firstName} {archiveTarget.lastName}</strong>{' '}
                will be removed from the active register, but every savings, loan, share and ledger
                record is preserved. The linked login is sealed with an anonymized username and a
                randomized password; restoring later requires an administrator password reset.
              </div>
              <p className="field-hint">
                Archiving is blocked automatically when the member has an active loan balance above
                zero or guarantees another active loan.
              </p>
              <div className="form-group">
                <label>Reason for archiving *</label>
                <textarea
                  rows={3}
                  value={archiveReason}
                  onChange={event => setArchiveReason(event.target.value)}
                  placeholder="e.g. Member resigned, duplicate record, dormancy cleanup…"
                  disabled={archiveAction === 'archive'}
                />
              </div>
              {error && <div className="error-message">{error}</div>}
              <div className="form-actions">
                <button
                  type="button"
                  className="btn btn-ghost"
                  disabled={archiveAction === 'archive'}
                  onClick={() => setArchiveTarget(null)}
                >
                  Cancel
                </button>
                <button
                  type="button"
                  className="btn btn-danger"
                  disabled={archiveAction === 'archive' || !archiveReason.trim()}
                  onClick={handleArchive}
                >
                  {archiveAction === 'archive' ? 'Archiving…' : 'Archive Member'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {selectedMemberId && (
        <div className="modal-overlay" onClick={closeProfile}>
          <div className="modal profile-modal" onClick={event => event.stopPropagation()}>
            <div className="modal-header">
              <h3>Member Profile</h3>
              <button className="modal-close" onClick={closeProfile}>×</button>
            </div>

            {profileLoading ? (
              <div className="loading">Loading profile...</div>
            ) : profile ? (
              <div className="modal-body">
                {(error || success) && (
                  <div className={`modal-feedback ${error ? 'error' : 'success'}`}>
                    {error || success}
                  </div>
                )}

                <div className="profile-header">
                  <div className="profile-photo-wrap">
                    {profile.member.passportPhotoPath ? (
                      <img className="profile-photo" src={api.fileUrl(profile.member.passportPhotoPath)} alt="" />
                    ) : (
                      <span className="profile-photo profile-photo-placeholder">
                        {(profile.member.firstName?.[0] || '') + (profile.member.lastName?.[0] || '')}
                      </span>
                    )}
                    <label className="photo-change-btn">
                      {profileAction === 'photo' ? 'Uploading...' : 'Change Photo'}
                      <input
                        type="file"
                        accept="image/*"
                        hidden
                        disabled={profileAction === 'photo'}
                        onChange={handleProfilePhotoUpload}
                      />
                    </label>
                  </div>

                  <div className="profile-heading">
                    <h3>{profile.member.firstName} {profile.member.lastName}</h3>
                    <p>
                      {profile.member.membershipNo} ·{' '}
                      <span className={badgeClass(profile.member.status)}>{profile.member.status}</span>
                    </p>
                    <div className="profile-stats">
                      <div><span>Savings</span><strong>{formatMoney(profile.totalSavings)}</strong></div>
                      <div><span>Share Capital</span><strong>{formatMoney(profile.totalShareCapital)}</strong></div>
                      <div><span>Accounts</span><strong>{profile.accounts.length}</strong></div>
                    </div>
                  </div>
                </div>

                <div className="tabs">
                  {[
                    ['overview', 'Overview'],
                    ['accounts', 'Accounts & Savings'],
                    ['shares', 'Shares'],
                    ['collateral', 'Collateral'],
                    ['documents', 'Documents'],
                    ...(canEditMember ? [['edit', 'Edit Profile']] : []),
                    ...(canManageCredentials ? [['security', 'Login & Security']] : [])
                  ].map(([key, label]) => (
                    <button
                      key={key}
                      className={`tab ${profileTab === key ? 'active' : ''}`}
                      onClick={() => setProfileTab(key)}
                    >
                      {label}
                    </button>
                  ))}
                </div>

                {profileTab === 'overview' && (
                  <div className="tab-panel">
                    <h4 className="section-title">Personal Information</h4>
                    <div className="info-grid">
                      <Info label="Phone" value={profile.member.phone} />
                      <Info label="Email" value={profile.member.email} />
                      <Info label="Date of Birth" value={formatDate(profile.member.dateOfBirth)} />
                      <Info label="Gender" value={profile.member.gender} />
                      <Info label="Marital Status" value={profile.member.maritalStatus} />
                      <Info label="Address" value={profile.member.address} />
                      <Info label="Registered" value={formatDate(profile.member.createdAt)} />
                      <Info label="Membership No" value={profile.member.membershipNo} />
                    </div>

                    <h4 className="section-title">Next of Kin</h4>
                    <div className="info-grid">
                      <Info label="Name" value={profile.member.nextOfKinName} />
                      <Info label="Relationship" value={profile.member.nextOfKinRelationship} />
                      <Info label="Phone" value={profile.member.nextOfKinPhone} />
                      <Info label="Address" value={profile.member.nextOfKinAddress} />
                    </div>

                    <h4 className="section-title">KYC Documents</h4>
                    {profile.documents.length === 0 ? (
                      <p className="empty-text">No identity documents on file.</p>
                    ) : (
                      <div className="doc-list">
                        {profile.documents.map(doc => (
                          <div className="doc-card" key={doc.documentId}>
                            <div className="doc-card-header">
                              <strong>{documentLabel(doc.documentType)}</strong>
                              <span className={badgeClass(doc.status)}>{doc.status}</span>
                            </div>
                            <div className="info-grid">
                              <Info label="Number" value={doc.documentNumber} />
                              <Info label="Issuer" value={doc.issuer} />
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}

                {profileTab === 'edit' && canEditMember && editForm && (
                  <div className="tab-panel">
                    <h4 className="section-title">Personal & Contact Details</h4>
                    <form onSubmit={handleSaveProfile}>
                      <div className="grid-2">
                        <div className="form-group">
                          <label>First Name *</label>
                          <input
                            value={editForm.firstName}
                            onChange={e => setEditForm({ ...editForm, firstName: e.target.value })}
                            required
                          />
                        </div>
                        <div className="form-group">
                          <label>Last Name *</label>
                          <input
                            value={editForm.lastName}
                            onChange={e => setEditForm({ ...editForm, lastName: e.target.value })}
                            required
                          />
                        </div>
                      </div>
                      <div className="grid-3">
                        <div className="form-group">
                          <label>Date of Birth</label>
                          <input
                            type="date"
                            value={editForm.dateOfBirth}
                            onChange={e => setEditForm({ ...editForm, dateOfBirth: e.target.value })}
                          />
                        </div>
                        <div className="form-group">
                          <label>Gender</label>
                          <select
                            value={editForm.gender}
                            onChange={e => setEditForm({ ...editForm, gender: e.target.value })}
                          >
                            <option value="">Select...</option>
                            <option value="MALE">Male</option>
                            <option value="FEMALE">Female</option>
                            <option value="OTHER">Other</option>
                          </select>
                        </div>
                        <div className="form-group">
                          <label>Marital Status</label>
                          <select
                            value={editForm.maritalStatus}
                            onChange={e => setEditForm({ ...editForm, maritalStatus: e.target.value })}
                          >
                            <option value="">Select...</option>
                            <option value="SINGLE">Single</option>
                            <option value="MARRIED">Married</option>
                            <option value="DIVORCED">Divorced</option>
                            <option value="WIDOWED">Widowed</option>
                          </select>
                        </div>
                      </div>
                      <div className="grid-2">
                        <div className="form-group">
                          <label>Phone</label>
                          <input
                            value={editForm.phone}
                            onChange={e => setEditForm({ ...editForm, phone: e.target.value })}
                            placeholder="+256..."
                          />
                        </div>
                        <div className="form-group">
                          <label>Email</label>
                          <input
                            type="email"
                            value={editForm.email}
                            onChange={e => setEditForm({ ...editForm, email: e.target.value })}
                          />
                        </div>
                      </div>
                      <div className="form-group">
                        <label>Address</label>
                        <input
                          value={editForm.address}
                          onChange={e => setEditForm({ ...editForm, address: e.target.value })}
                        />
                      </div>

                      <h4 className="section-title">Next of Kin</h4>
                      <div className="grid-2">
                        <div className="form-group">
                          <label>Name</label>
                          <input
                            value={editForm.nextOfKinName}
                            onChange={e => setEditForm({ ...editForm, nextOfKinName: e.target.value })}
                          />
                        </div>
                        <div className="form-group">
                          <label>Relationship</label>
                          <input
                            value={editForm.nextOfKinRelationship}
                            onChange={e => setEditForm({ ...editForm, nextOfKinRelationship: e.target.value })}
                          />
                        </div>
                        <div className="form-group">
                          <label>Phone</label>
                          <input
                            value={editForm.nextOfKinPhone}
                            onChange={e => setEditForm({ ...editForm, nextOfKinPhone: e.target.value })}
                          />
                        </div>
                        <div className="form-group">
                          <label>Address</label>
                          <input
                            value={editForm.nextOfKinAddress}
                            onChange={e => setEditForm({ ...editForm, nextOfKinAddress: e.target.value })}
                          />
                        </div>
                      </div>

                      <div className="form-actions">
                        <button type="submit" className="btn btn-primary" disabled={profileAction === 'save-profile'}>
                          {profileAction === 'save-profile' ? 'Saving...' : 'Save Changes'}
                        </button>
                      </div>
                    </form>
                  </div>
                )}

                {profileTab === 'security' && canManageCredentials && (
                  <div className="tab-panel">
                    <h4 className="section-title">Member Login</h4>
                    <p className="field-hint">
                      {memberUser
                        ? `Login created ${formatDate(memberUser.createdAt)}. Last sign-in: ${memberUser.lastLogin ? new Date(memberUser.lastLogin).toLocaleString() : 'never'}.`
                        : 'No login is linked to this member yet. Create one below.'}
                    </p>
                    <form onSubmit={handleSaveCredentials}>
                      <div className="grid-2">
                        <div className="form-group">
                          <label>Username *</label>
                          <input
                            value={userForm.username}
                            onChange={e => setUserForm({ ...userForm, username: e.target.value })}
                            minLength={3}
                            required
                          />
                        </div>
                        <div className="form-group">
                          <label>{memberUser ? 'New Password (optional)' : 'Password *'}</label>
                          <input
                            type="password"
                            value={userForm.password}
                            onChange={e => setUserForm({ ...userForm, password: e.target.value })}
                            placeholder={memberUser ? 'Leave blank to keep the current password' : 'At least 8 characters'}
                            minLength={memberUser ? undefined : 8}
                            required={!memberUser}
                          />
                        </div>
                      </div>
                      <div className="grid-2">
                        <div className="form-group">
                          <label>Email</label>
                          <input
                            type="email"
                            value={userForm.email}
                            onChange={e => setUserForm({ ...userForm, email: e.target.value })}
                          />
                        </div>
                        <div className="form-group">
                          <label>Account Status</label>
                          <select
                            value={userForm.isActive ? 'active' : 'inactive'}
                            onChange={e => setUserForm({ ...userForm, isActive: e.target.value === 'active' })}
                          >
                            <option value="active">Active — can sign in</option>
                            <option value="inactive">Inactive — sign-in blocked</option>
                          </select>
                        </div>
                      </div>
                      <div className="form-actions">
                        <button type="submit" className="btn btn-primary" disabled={profileAction === 'save-credentials'}>
                          {profileAction === 'save-credentials'
                            ? 'Saving...'
                            : memberUser ? 'Update Login' : 'Create Login'}
                        </button>
                      </div>
                    </form>
                  </div>
                )}

                {profileTab === 'accounts' && (
                  <div className="tab-panel">
                    <h4 className="section-title">Linked Accounts</h4>
                    {profile.accounts.length === 0 ? (
                      <p className="empty-text">No linked accounts yet.</p>
                    ) : (
                      <table>
                        <thead>
                          <tr><th>Account No</th><th>Type</th><th>Status</th><th>Balance</th></tr>
                        </thead>
                        <tbody>
                          {profile.accounts.map(account => (
                            <tr key={account.accountId}>
                              <td>{account.accountNumber}</td>
                              <td>{account.accountType}</td>
                              <td><span className={badgeClass(account.status)}>{account.status}</span></td>
                              <td>{formatMoney(account.balance)}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    )}
                  </div>
                )}

                {profileTab === 'shares' && (
                  <div className="tab-panel">
                    <div className="mini-stat-row">
                      <div className="mini-stat">
                        <span className="mini-stat-label">Total Share Capital</span>
                        <span className="mini-stat-value">{formatMoney(profile.totalShareCapital)}</span>
                      </div>
                      <div className="mini-stat">
                        <span className="mini-stat-label">Share Transactions</span>
                        <span className="mini-stat-value">{profile.shareTransactions.length}</span>
                      </div>
                    </div>

                    {profile.shareTransactions.length === 0 ? (
                      <p className="empty-text">No share transactions recorded.</p>
                    ) : (
                      <table>
                        <thead>
                          <tr><th>Date</th><th>Type</th><th>Quantity</th><th>Amount</th><th>Status</th></tr>
                        </thead>
                        <tbody>
                          {profile.shareTransactions.map(transaction => (
                            <tr key={transaction.transactionId}>
                              <td>{formatDate(transaction.transactionDate)}</td>
                              <td>{transaction.transactionType}</td>
                              <td>{transaction.quantity}</td>
                              <td>{formatMoney(transaction.amount)}</td>
                              <td><span className={badgeClass(transaction.status)}>{transaction.status}</span></td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    )}
                  </div>
                )}

                {profileTab === 'collateral' && (
                  <div className="tab-panel">
                    <div className="doc-tab-header">
                      <h4 className="section-title">Collateral & Securities</h4>
                      <button
                        type="button"
                        className="btn btn-primary btn-sm"
                        onClick={() => {
                          const memberId = profile.member.memberId
                          closeProfile()
                          navigate(`/collateral?new=1&memberId=${memberId}`)
                        }}
                      >
                        + Register Collateral
                      </button>
                    </div>

                    {(profile.collateral || []).length === 0 ? (
                      <p className="empty-text">No collateral registered for this member.</p>
                    ) : (
                      <>
                        <div className="mini-stat-row">
                          <div className="mini-stat">
                            <span className="mini-stat-label">Assets in Custody</span>
                            <span className="mini-stat-value">
                              {(profile.collateral || []).filter(asset => asset.status !== 'RELEASED').length}
                            </span>
                          </div>
                          <div className="mini-stat">
                            <span className="mini-stat-label">Estimated Value</span>
                            <span className="mini-stat-value">
                              {formatMoney((profile.collateral || []).reduce(
                                (sum, asset) => sum + Number(asset.estimatedValue || 0),
                                0
                              ))}
                            </span>
                          </div>
                        </div>

                        <div className="table-wrap">
                          <table>
                            <thead>
                              <tr>
                                <th>Asset ID</th>
                                <th>Type</th>
                                <th>Estimated Value</th>
                                <th>Status</th>
                                <th></th>
                              </tr>
                            </thead>
                            <tbody>
                              {(profile.collateral || []).map(asset => (
                                <tr key={asset.collateralId}>
                                  <td>
                                    <strong>{asset.assetCode}</strong>
                                    <span className="cell-sub">{asset.titleNumber}</span>
                                  </td>
                                  <td>{ASSET_TYPE_LABELS[asset.assetType] || asset.assetType}</td>
                                  <td>{formatMoney(asset.estimatedValue)}</td>
                                  <td>
                                    <span className={badgeClass(asset.status)}>
                                      {COLLATERAL_STATUS_LABELS[asset.status] || asset.status}
                                    </span>
                                  </td>
                                  <td>
                                    <button
                                      type="button"
                                      className="link-btn"
                                      onClick={() => {
                                        closeProfile()
                                        navigate(`/collateral?view=${asset.collateralId}`)
                                      }}
                                    >
                                      View
                                    </button>
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      </>
                    )}
                  </div>
                )}

                {profileTab === 'documents' && (
                  <div className="tab-panel">
                    <div className="doc-tab-header">
                      <h4 className="section-title">Document Attachments</h4>
                      <button
                        type="button"
                        className="btn btn-primary btn-sm"
                        onClick={() => setShowAddDoc(current => !current)}
                      >
                        {showAddDoc ? 'Close Form' : '+ Add Document'}
                      </button>
                    </div>

                    {showAddDoc && (
                      <form className="add-doc-form" onSubmit={handleAddDocument}>
                        <div className="grid-2">
                          <div className="form-group">
                            <label>Document Type</label>
                            <select value={newDoc.documentType} onChange={e => updateNewDoc('documentType', e.target.value)}>
                              {DOCUMENT_TYPES.map(type => (
                                <option key={type.value} value={type.value}>{type.label}</option>
                              ))}
                            </select>
                          </div>
                          <div className="form-group">
                            <label>Document Number *</label>
                            <input value={newDoc.documentNumber} onChange={e => updateNewDoc('documentNumber', e.target.value)} />
                          </div>
                        </div>
                        <div className="grid-2">
                          <div className="form-group">
                            <label>Issuing Authority</label>
                            <input value={newDoc.issuer} onChange={e => updateNewDoc('issuer', e.target.value)} />
                          </div>
                          <div className="form-group">
                            <label>Issue Date</label>
                            <input type="date" value={newDoc.issueDate} onChange={e => updateNewDoc('issueDate', e.target.value)} />
                          </div>
                        </div>
                        <div className="grid-2">
                          <div className="form-group">
                            <label>Expiry Date</label>
                            <input type="date" value={newDoc.expiryDate} onChange={e => updateNewDoc('expiryDate', e.target.value)} />
                          </div>
                          <div className="form-group add-doc-actions">
                            <button type="button" className="btn btn-ghost btn-sm" onClick={() => setShowAddDoc(false)}>Cancel</button>
                            <button type="submit" className="btn btn-primary btn-sm" disabled={profileAction === 'add-doc'}>
                              {profileAction === 'add-doc' ? 'Adding...' : 'Add Document'}
                            </button>
                          </div>
                        </div>
                      </form>
                    )}

                    {profile.documents.length === 0 ? (
                      <p className="empty-text">No identity documents on file.</p>
                    ) : (
                      <div className="doc-list">
                        {profile.documents.map(doc => (
                          <div className="doc-card" key={doc.documentId}>
                            <div className="doc-card-header">
                              <strong>{documentLabel(doc.documentType)}</strong>
                              <span className={badgeClass(doc.status)}>{doc.status}</span>
                            </div>
                            <div className="info-grid">
                              <Info label="Number" value={doc.documentNumber} />
                              <Info label="Issuer" value={doc.issuer} />
                              <Info label="Issued" value={formatDate(doc.issueDate)} />
                              <Info label="Expires" value={formatDate(doc.expiryDate)} />
                            </div>
                            <div className="doc-actions">
                              {doc.filePath ? (
                                <a className="btn btn-secondary btn-sm" href={api.fileUrl(doc.filePath)} target="_blank" rel="noreferrer">
                                  View attachment
                                </a>
                              ) : (
                                <span className="field-hint">No scan attached</span>
                              )}
                              <label className="btn btn-secondary btn-sm upload-label">
                                {profileAction === `doc-${doc.documentId}` ? 'Uploading...' : doc.filePath ? 'Replace scan' : 'Upload scan'}
                                <input
                                  type="file"
                                  accept="image/*,.pdf"
                                  hidden
                                  disabled={profileAction === `doc-${doc.documentId}`}
                                  onChange={event => handleProfileDocumentUpload(doc.documentId, event)}
                                />
                              </label>
                              {doc.status === 'PENDING' && (
                                <>
                                  <button
                                    type="button"
                                    className="btn btn-success btn-sm"
                                    disabled={profileAction === `verify-${doc.documentId}`}
                                    onClick={() => handleDocumentOutcome(doc.documentId, 'VERIFIED')}
                                  >
                                    {profileAction === `verify-${doc.documentId}` ? 'Saving...' : 'Verify'}
                                  </button>
                                  <button
                                    type="button"
                                    className="btn btn-danger btn-sm"
                                    disabled={profileAction === `verify-${doc.documentId}`}
                                    onClick={() => handleDocumentOutcome(doc.documentId, 'REJECTED')}
                                  >
                                    Reject
                                  </button>
                                </>
                              )}
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>
            ) : (
              <div className="error-message">Could not load member profile.</div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

export default Members
