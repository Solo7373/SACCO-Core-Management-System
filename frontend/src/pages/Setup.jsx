import { useEffect, useState } from 'react'
import { api } from '../services/api'

const STEPS = [
  { id: 'organization', title: 'Organisation', hint: 'Legal identity and contacts' },
  { id: 'admin', title: 'Administrator', hint: 'Secure the first login' },
  { id: 'storage', title: 'Storage', hint: 'Google Drive and backups' },
  { id: 'messaging', title: 'Messaging', hint: 'Senders and loan reminders' },
  { id: 'billing', title: 'Billing (optional)', hint: 'Optional communication cost estimates' },
  { id: 'review', title: 'Review', hint: 'Confirm and go live' }
]

const CHANNELS = [
  { id: 'SMS', label: 'SMS' },
  { id: 'WHATSAPP', label: 'WhatsApp' },
  { id: 'EMAIL', label: 'Email' },
  { id: 'VOICE', label: 'Voice call' }
]

const EMPTY_FORM = {
  organization: {
    legalName: '',
    tradingName: '',
    registrationNumber: '',
    taxPin: '',
    addressLine1: '',
    addressLine2: '',
    city: '',
    country: 'Uganda',
    phone: '',
    email: '',
    website: '',
    timezone: 'Africa/Kampala',
    currency: 'UGX'
  },
  admin: {
    username: 'admin',
    email: '',
    currentPassword: '',
    newPassword: '',
    confirmPassword: ''
  },
  storage: {
    provider: 'GOOGLE_DRIVE',
    authMode: 'OAUTH',
    folderId: '',
    googleAccountEmail: '',
    gmailBaseUrl: 'https://gmail.googleapis.com',
    baseUrl: 'https://www.googleapis.com',
    tokenUri: 'https://oauth2.googleapis.com/token',
    applicationName: 'SCMS',
    backupMode: 'GOOGLE_DRIVE',
    automaticBackups: true,
    retentionDays: 30,
    schedule: 'HOURLY',
    oauthClientId: '',
    oauthClientSecret: '',
    oauthRefreshToken: ''
  },
  messaging: {
    smsSenderId: '',
    whatsappDisplayName: '',
    whatsappPhoneNumber: '',
    emailFromName: '',
    emailFromAddress: '',
    emailReplyTo: '',
    voiceCallerId: '',
    reminderDaysBefore: '5,3,1,0',
    reminderChannels: ['SMS', 'WHATSAPP'],
    reminderSendTime: '08:00',
    reminderGraceDays: 3,
    reminderTemplate:
      'Dear {memberName}, your loan {loanNumber} instalment of {currency} {amount} is due on {dueDate}. Please pay on time to avoid penalties. {organisationName}',
    reminderWhatsappTemplate: '',
    reminderWhatsappLanguage: 'en',
    voiceEnabled: false,
    voiceQuietHoursStart: '08:00',
    voiceQuietHoursEnd: '18:00',
    consentRequired: true,
    optOutText: 'Reply STOP to opt out of SMS reminders.'
  },
  billing: {
    provider: "Africa's Talking / Meta",
    accountName: '',
    accountNumber: '',
    currency: 'UGX',
    contactEmail: '',
    chargeBearer: 'SACCO',
    smsUnitCost: 0,
    whatsappUnitCost: 0,
    emailUnitCost: 0,
    voiceUnitCost: 0,
    lowBalanceThreshold: 0,
    prepaidBalance: 0
  }
}

function fromStatus(status) {
  const organization = status?.organization || {}
  const storage = status?.storage || {}
  const messaging = status?.messaging || {}
  const billing = status?.billing || {}
  return {
    organization: { ...EMPTY_FORM.organization, ...organization },
    admin: {
      ...EMPTY_FORM.admin,
      username: status?.adminUsername || 'admin',
      email: status?.adminEmail || ''
    },
    storage: {
      ...EMPTY_FORM.storage,
      ...storage,
      automaticBackups: storage.automaticBackupsEnabled ?? true,
      retentionDays: storage.retentionDays ?? 30,
      oauthClientId: '',
      oauthClientSecret: '',
      oauthRefreshToken: ''
    },
    messaging: {
      ...EMPTY_FORM.messaging,
      ...messaging,
      emailFromAddress: messaging.emailFromAddress || storage.googleAccountEmail || '',
      reminderDaysBefore: (messaging.reminderDaysBefore || []).join(','),
      reminderChannels: messaging.reminderChannels || [],
      reminderWhatsappTemplate: messaging.reminderWhatsappTemplate || '',
      reminderWhatsappLanguage: messaging.reminderWhatsappLanguage || 'en',
      voiceEnabled: Boolean(messaging.voiceEnabled),
      voiceQuietHoursStart: messaging.voiceQuietHoursStart || '08:00',
      voiceQuietHoursEnd: messaging.voiceQuietHoursEnd || '18:00'
    },
    billing: { ...EMPTY_FORM.billing, ...billing }
  }
}

function Field({ label, hint, children }) {
  return (
    <div className="form-group">
      <label>{label}</label>
      {children}
      {hint && <p className="field-hint">{hint}</p>}
    </div>
  )
}

function formatDateTime(value) {
  if (!value) return 'not yet'
  return new Date(value).toLocaleString()
}

function formatBytes(value) {
  const bytes = Number(value || 0)
  if (!bytes) return '—'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let size = bytes
  let unit = 0
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024
    unit += 1
  }
  return `${size.toFixed(unit === 0 ? 0 : 1)} ${units[unit]}`
}

function Setup({ initialStatus = null, embedded = false, liveMode = false, onComplete, onLogin }) {
  const [status, setStatus] = useState(initialStatus)
  const [form, setForm] = useState(() => fromStatus(initialStatus))
  const [step, setStep] = useState(0)
  const [loading, setLoading] = useState(!initialStatus)
  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [testing, setTesting] = useState(false)
  const [testingEmail, setTestingEmail] = useState(false)
  const [sendingTestEmail, setSendingTestEmail] = useState(false)
  const [runningBackup, setRunningBackup] = useState(false)
  const [testEmailTo, setTestEmailTo] = useState('')
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)
  const [completed, setCompleted] = useState(liveMode ? false : Boolean(initialStatus?.completed))
  const editingLive = liveMode || Boolean(status?.completed)

  useEffect(() => {
    loadStatus()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (status) {
      setForm(fromStatus(status))
      if (status.completed && !liveMode) setCompleted(true)
    }
  }, [status])

  async function loadStatus() {
    setLoading(true)
    setError(null)
    try {
      const next = await api.getSetupStatus()
      setStatus(next)
    } catch (err) {
      setError(err.message || 'Could not read the setup status.')
    } finally {
      setLoading(false)
    }
  }

  function update(section, key, value) {
    setForm(prev => ({
      ...prev,
      [section]: { ...prev[section], [key]: value }
    }))
  }

  function toggleChannel(channel) {
    setForm(prev => {
      const current = prev.messaging.reminderChannels || []
      const next = current.includes(channel)
        ? current.filter(item => item !== channel)
        : [...current, channel]
      return { ...prev, messaging: { ...prev.messaging, reminderChannels: next } }
    })
  }

  function validateCurrentStep() {
    const { organization, admin, storage, messaging } = form
    if (step === 0) {
      if (!organization.legalName.trim()) return 'Enter the organisation legal name.'
      if (!organization.addressLine1.trim()) return 'Enter the organisation physical address.'
      if (!organization.phone.trim()) return 'Enter the organisation phone number.'
      if (!organization.email.trim()) return 'Enter the organisation email address.'
    }
    if (step === 1 && !editingLive) {
      if (!admin.username.trim()) return 'Enter the administrator username.'
      if (!admin.email.trim()) return 'Enter the administrator email address.'
      if (status?.adminConfigured && !status.adminUsesDefaultPassword && !admin.currentPassword) {
        return 'Enter the current administrator password before changing the administrator account.'
      }
      if (!admin.newPassword || admin.newPassword.length < 8) {
        return 'The new administrator password must be at least 8 characters.'
      }
      if (admin.newPassword !== admin.confirmPassword) {
        return 'The administrator passwords do not match.'
      }
    }
    if (step === 2) {
      if (storage.provider === 'GOOGLE_DRIVE') {
        if (!storage.folderId.trim()) return 'Enter the Google Drive folder ID.'
        if (storage.authMode === 'SERVICE_ACCOUNT' && !status?.storage?.credentialsConfigured) {
          return 'Upload the Google service-account JSON file.'
        }
        if (storage.authMode === 'OAUTH' && !status?.storage?.credentialsConfigured) {
          return 'Enter the OAuth client ID, client secret and refresh token.'
        }
        if (!status?.storage?.connected) {
          return 'Test the Google Drive connection before continuing.'
        }
      }
      if (storage.authMode === 'OAUTH') {
        if (!storage.googleAccountEmail.trim()) {
          return 'Enter the Google account email that owns the backup folder.'
        }
        if (!status?.storage?.emailConnected) {
          return 'Test the Gmail connection before continuing. The same Google account sends member email.'
        }
      }
    }
    if (step === 3) {
      if (!messaging.reminderChannels?.length) return 'Select at least one loan reminder channel.'
      if (messaging.reminderChannels.includes('SMS') && !messaging.smsSenderId.trim()) {
        return 'Enter the SMS sender ID.'
      }
      if (messaging.reminderChannels.includes('WHATSAPP') && !messaging.whatsappPhoneNumber.trim()) {
        return 'Enter the WhatsApp sender number.'
      }
      if (messaging.reminderChannels.includes('EMAIL')) {
        if (!form.storage.googleAccountEmail && !messaging.emailFromAddress.trim()) {
          return 'Connect a Google account in Storage before selecting email reminders.'
        }
        if (form.storage.authMode === 'SERVICE_ACCOUNT') {
          return 'Email reminders need Google OAuth. A service account cannot send Gmail.'
        }
        if (!status?.storage?.emailConnected) {
          return 'Test the Gmail connection in Storage before selecting email reminders.'
        }
      }
      if (messaging.reminderChannels.includes('VOICE') && !messaging.voiceCallerId.trim()) {
        return 'Enter the voice caller ID.'
      }
      if (messaging.reminderChannels.includes('WHATSAPP') && !(messaging.reminderWhatsappTemplate || '').trim()) {
        return 'Enter the approved WhatsApp reminder template name, or clear the WhatsApp channel to use free text inside the customer window.'
      }
      if (!messaging.reminderTemplate.trim()) return 'Enter the loan reminder message template.'
      if (messaging.consentRequired && !messaging.optOutText.trim()) {
        return 'Enter the opt-out wording used in member messages.'
      }
    }
    return null
  }

  function storagePayload() {
    return {
      provider: form.storage.provider,
      authMode: form.storage.authMode,
      folderId: form.storage.folderId,
      googleAccountEmail: form.storage.googleAccountEmail,
      gmailBaseUrl: form.storage.gmailBaseUrl,
      baseUrl: form.storage.baseUrl,
      tokenUri: form.storage.tokenUri,
      applicationName: form.storage.applicationName,
      backupMode: form.storage.backupMode,
      automaticBackups: Boolean(form.storage.automaticBackups),
      retentionDays: Number(form.storage.retentionDays) || 30,
      schedule: form.storage.schedule,
      oauthClientId: form.storage.oauthClientId,
      oauthClientSecret: form.storage.oauthClientSecret,
      oauthRefreshToken: form.storage.oauthRefreshToken
    }
  }

  function messagingPayload() {
    return {
      smsSenderId: form.messaging.smsSenderId,
      whatsappDisplayName: form.messaging.whatsappDisplayName,
      whatsappPhoneNumber: form.messaging.whatsappPhoneNumber,
      emailFromName: form.messaging.emailFromName,
      emailFromAddress: form.messaging.emailFromAddress,
      emailReplyTo: form.messaging.emailReplyTo,
      voiceCallerId: form.messaging.voiceCallerId,
      reminderDaysBefore: String(form.messaging.reminderDaysBefore || '')
        .split(',')
        .map(value => Number(value.trim()))
        .filter(value => Number.isInteger(value) && value >= 0 && value <= 90),
      reminderChannels: form.messaging.reminderChannels,
      reminderSendTime: form.messaging.reminderSendTime,
      reminderGraceDays: Number(form.messaging.reminderGraceDays) || 0,
      reminderTemplate: form.messaging.reminderTemplate,
      reminderWhatsappTemplate: form.messaging.reminderWhatsappTemplate,
      reminderWhatsappLanguage: form.messaging.reminderWhatsappLanguage,
      voiceEnabled: Boolean(form.messaging.voiceEnabled),
      voiceQuietHoursStart: form.messaging.voiceQuietHoursStart,
      voiceQuietHoursEnd: form.messaging.voiceQuietHoursEnd,
      consentRequired: Boolean(form.messaging.consentRequired),
      optOutText: form.messaging.optOutText
    }
  }

  function billingPayload() {
    return {
      provider: form.billing.provider,
      accountName: form.billing.accountName,
      accountNumber: form.billing.accountNumber,
      currency: form.billing.currency,
      contactEmail: form.billing.contactEmail,
      chargeBearer: form.billing.chargeBearer,
      smsUnitCost: Number(form.billing.smsUnitCost) || 0,
      whatsappUnitCost: Number(form.billing.whatsappUnitCost) || 0,
      emailUnitCost: Number(form.billing.emailUnitCost) || 0,
      voiceUnitCost: Number(form.billing.voiceUnitCost) || 0,
      lowBalanceThreshold: Number(form.billing.lowBalanceThreshold) || 0,
      prepaidBalance: Number(form.billing.prepaidBalance) || 0
    }
  }

  async function handleNext() {
    setError(null)
    setNotice(null)
    const validation = validateCurrentStep()
    if (validation) {
      setError(validation)
      return
    }

    // The live settings workspace does not reset the administrator password.
    // Credentials are rotated from Change Password in the sidebar, and the
    // contact email is managed from Users.
    if (step === 1 && editingLive) {
      setNotice('The administrator account is secured. Use Change Password in the sidebar to rotate the password.')
      setStep(2)
      return
    }

    setSaving(true)
    try {
      let next
      if (step === 0) next = await api.saveSetupOrganization(form.organization)
      if (step === 1) {
        next = await api.saveSetupAdmin(form.admin)
        if (next?.token) {
          localStorage.setItem('token', next.token)
          localStorage.setItem('userId', next.userId ?? '1')
          localStorage.setItem('username', next.adminUsername || form.admin.username)
          localStorage.setItem('role', 'ADMIN')
          localStorage.setItem('memberId', '')
          localStorage.setItem('passwordResetRequired', 'false')
          if (onLogin) {
            onLogin({
              userId: next.userId,
              username: next.adminUsername || form.admin.username,
              role: 'ADMIN',
              token: next.token
            })
          }
        }
      }
      if (step === 2) next = await api.saveSetupStorage(storagePayload())
      if (step === 3) next = await api.saveSetupMessaging(messagingPayload())
      if (step === 4) next = await api.saveSetupBilling(billingPayload())
      if (next) {
        setStatus(next)
        setStep(current => Math.min(current + 1, STEPS.length - 1))
        setNotice(`${STEPS[step].title} saved.`)
      }
    } catch (err) {
      setError(err.message || 'Could not save this step.')
    } finally {
      setSaving(false)
    }
  }

  async function handleTestDrive() {
    setError(null)
    setNotice(null)
    if (!form.storage.folderId.trim()) {
      setError('Enter the Google Drive folder ID first.')
      return
    }
    if (form.storage.authMode === 'SERVICE_ACCOUNT' && !status?.storage?.credentialsConfigured) {
      setError('Upload the Google service-account JSON first.')
      return
    }
    setTesting(true)
    try {
      const next = await api.saveSetupStorage(storagePayload())
      setStatus(next)
      const result = await api.testSetupStorage()
      if (result.connected) {
        setNotice(`Google Drive connected${result.folderName ? ` to “${result.folderName}”` : ''}.`)
      } else {
        setError(result.message || 'Google Drive connection failed.')
      }
      await loadStatus()
    } catch (err) {
      setError(err.message || 'Google Drive connection failed.')
    } finally {
      setTesting(false)
    }
  }

  async function handleCredentialsUpload(event) {
    const file = event.target.files?.[0]
    if (!file) return
    setError(null)
    setNotice(null)
    setUploading(true)
    try {
      const result = await api.uploadSetupCredentials(file)
      setNotice(result.message || 'Credentials stored.')
      await loadStatus()
    } catch (err) {
      setError(err.message || 'Could not store the credentials file.')
    } finally {
      setUploading(false)
      event.target.value = ''
    }
  }

  async function handleTestEmail() {
    setError(null)
    setNotice(null)
    if (form.storage.authMode === 'SERVICE_ACCOUNT') {
      setError('Gmail needs Google OAuth. A service account has no mailbox — switch Authentication to OAuth.')
      return
    }
    setTestingEmail(true)
    try {
      const next = await api.saveSetupStorage(storagePayload())
      setStatus(next)
      if (form.storage.authMode === 'OAUTH' && !next?.storage?.credentialsConfigured) {
        setError('Enter the OAuth client ID, client secret and refresh token first.')
        return
      }
      const result = await api.testSetupEmail()
      if (result.connected) {
        setNotice(`Gmail connected as ${result.emailAddress || form.storage.googleAccountEmail}.`)
      } else {
        setError(result.message || 'Gmail connection failed.')
      }
      await loadStatus()
    } catch (err) {
      setError(err.message || 'Gmail connection failed.')
    } finally {
      setTestingEmail(false)
    }
  }

  async function handleSendTestEmail() {
    setError(null)
    setNotice(null)
    if (form.storage.authMode === 'SERVICE_ACCOUNT') {
      setError('Gmail needs Google OAuth. A service account has no mailbox — switch Authentication to OAuth.')
      return
    }
    if (!form.storage.googleAccountEmail.trim()) {
      setError('Enter the Google account email first, then test the Gmail connection.')
      return
    }
    setSendingTestEmail(true)
    try {
      const next = await api.saveSetupStorage(storagePayload())
      setStatus(next)
      const result = await api.sendSetupTestEmail(testEmailTo.trim())
      setNotice(`Test email sent to ${result.to} from ${result.from}.`)
      await loadStatus()
    } catch (err) {
      setError(err.message || 'Could not send the test email.')
    } finally {
      setSendingTestEmail(false)
    }
  }

  async function handleRunBackup() {
    setError(null)
    setNotice(null)
    setRunningBackup(true)
    try {
      const next = await api.saveSetupStorage(storagePayload())
      setStatus(next)
      const result = await api.runSetupBackup()
      const latestLocal = (result?.recentJobs || []).find(job => job.tier === 'LOCAL')
      if (!latestLocal || latestLocal.status !== 'COMPLETED') {
        throw new Error(latestLocal?.errorMessage || 'The backup did not complete. Check the backend log.')
      }
      const cloudMessage = result?.cloudConfigured
        ? (result.lastCloudUpload ? ' Google Drive upload completed.' : ' Google Drive upload is queued.')
        : ' Cloud upload is queued until Google Drive is configured.'
      setNotice(`Backup completed: ${latestLocal.fileName} (${formatBytes(latestLocal.sizeBytes)}).${cloudMessage}`)
      await loadStatus()
    } catch (err) {
      setError(err.message || 'Could not run the backup.')
    } finally {
      setRunningBackup(false)
    }
  }

  async function handleComplete() {
    setError(null)
    setNotice(null)
    setSaving(true)
    try {
      const next = await api.completeSetup()
      setStatus(next)
      setCompleted(true)
      setNotice('Setup complete. The system is ready for official use.')
      if (onComplete) onComplete(next)
    } catch (err) {
      setError(err.message || 'Could not complete setup.')
    } finally {
      setSaving(false)
    }
  }

  if (loading && !status) {
    return (
      <div className={embedded ? 'setup-embedded' : 'setup-page'}>
        <div className="setup-shell">
          <div className="loading">Reading system setup status…</div>
        </div>
      </div>
    )
  }

  if (completed) {
    return (
      <div className={embedded ? 'setup-embedded' : 'setup-page'}>
        <div className="setup-shell setup-success">
          <div className="setup-success-mark">✓</div>
          <h2>System setup complete</h2>
          <p>
            {status?.organization?.legalName || 'Your SACCO'} is configured for official use.
            Automatic backups, Google Drive uploads, Gmail email and sender identities are stored.
            Communication billing can be added at any time from System Settings.
          </p>
          {notice && <div className="success-message">{notice}</div>}
          {error && <div className="error-message">{error}</div>}
          <div className="setup-actions">
            {embedded ? (
              <button type="button" className="btn btn-ghost" onClick={() => setCompleted(false)}>
                Edit live settings
              </button>
            ) : (
              <button type="button" className="btn btn-primary" onClick={() => window.location.reload()}>
                Continue to sign in
              </button>
            )}
          </div>
        </div>
      </div>
    )
  }

  const backupInfo = status?.storage?.backup || {}

  return (
    <div className={embedded ? 'setup-embedded' : 'setup-page'}>
      <div className="setup-shell">
        <div className="setup-heading">
          <div>
            <span className="setup-kicker">{editingLive ? 'Live configuration' : 'Go-live configuration'}</span>
            <h1>{status?.organization?.legalName || 'SACCO Core Management System'}</h1>
            <p>
              {editingLive
                ? 'Update the SACCO names, official phone and email, sender identities, optional communication billing and backup policy. Each step saves immediately and applies to new transactions, statements, receipts and messages.'
                : 'Complete these settings before the system is used for official SACCO operations. Communication billing is optional and can be skipped; no payment is initiated from this screen.'}
            </p>
          </div>
          <div className="setup-progress-chip">
            Step {step + 1} of {STEPS.length}
          </div>
        </div>

        {editingLive && (
          <div className="info-banner">
            You are editing live settings. Changes take effect as soon as each step is saved.
            The administrator password is changed from Change Password in the sidebar.
          </div>
        )}

        <div className="setup-step-grid">
          {STEPS.map((item, index) => (
            <button
              key={item.id}
              type="button"
              className={`setup-step ${index === step ? 'active' : ''} ${index < step ? 'done' : ''}`}
              onClick={() => {
                if (index <= step || embedded || status?.completed) setStep(index)
              }}
            >
              <span className="setup-step-number">{index < step ? '✓' : index + 1}</span>
              <span>
                <strong>{item.title}</strong>
                <small>{item.hint}</small>
              </span>
            </button>
          ))}
        </div>

        {error && <div className="error-message">{error}</div>}
        {notice && <div className="success-message">{notice}</div>}

        <div className="card setup-card">
          {step === 0 && (
            <>
              <div className="setup-section-heading">
                <h2>Organisation identity</h2>
                <p>These details appear on member statements, demand letters and reminder messages.</p>
              </div>
              <div className="form-grid two-column">
                <Field label="SACCO / business legal name" hint="Registered name used on statements, contracts and official correspondence.">
                  <input value={form.organization.legalName} onChange={e => update('organization', 'legalName', e.target.value)} placeholder="Example SACCO Limited" />
                </Field>
                <Field label="Trading name" hint="The shorter name members see in messages, receipts and on the portal.">
                  <input value={form.organization.tradingName} onChange={e => update('organization', 'tradingName', e.target.value)} placeholder="Example SACCO" />
                </Field>
                <Field label="Registration number">
                  <input value={form.organization.registrationNumber} onChange={e => update('organization', 'registrationNumber', e.target.value)} />
                </Field>
                <Field label="Tax PIN">
                  <input value={form.organization.taxPin} onChange={e => update('organization', 'taxPin', e.target.value)} />
                </Field>
                <Field label="Physical address">
                  <input value={form.organization.addressLine1} onChange={e => update('organization', 'addressLine1', e.target.value)} placeholder="Plot, street, building" />
                </Field>
                <Field label="Address line 2">
                  <input value={form.organization.addressLine2} onChange={e => update('organization', 'addressLine2', e.target.value)} />
                </Field>
                <Field label="City / town">
                  <input value={form.organization.city} onChange={e => update('organization', 'city', e.target.value)} />
                </Field>
                <Field label="Country">
                  <input value={form.organization.country} onChange={e => update('organization', 'country', e.target.value)} />
                </Field>
                <Field label="Official phone number" hint="The number members should call for account and loan support.">
                  <input value={form.organization.phone} onChange={e => update('organization', 'phone', e.target.value)} placeholder="+256..." />
                </Field>
                <Field label="Official email address" hint="Used for member correspondence and official notices.">
                  <input type="email" value={form.organization.email} onChange={e => update('organization', 'email', e.target.value)} placeholder="info@example.co.ug" />
                </Field>
                <Field label="Website">
                  <input value={form.organization.website} onChange={e => update('organization', 'website', e.target.value)} />
                </Field>
                <Field label="Timezone">
                  <input value={form.organization.timezone} onChange={e => update('organization', 'timezone', e.target.value)} placeholder="Africa/Kampala" />
                </Field>
                <Field label="Currency">
                  <input value={form.organization.currency} maxLength={10} onChange={e => update('organization', 'currency', e.target.value.toUpperCase())} />
                </Field>
              </div>
            </>
          )}

          {step === 1 && (
            <>
              <div className="setup-section-heading">
                <h2>Administrator account</h2>
                <p>
                  {editingLive
                    ? 'The administrator account is already secured. Change the password from Change Password in the sidebar; the contact email is managed from Users.'
                    : 'This is the first secured login. The old factory password is never accepted once setup is complete.'}
                </p>
              </div>
              {editingLive ? (
                <>
                  <div className="info-banner">
                    For security, live settings do not reset the administrator password. Use
                    Change Password in the sidebar, and Users to maintain account contact details.
                  </div>
                  <div className="form-grid two-column">
                    <Field label="Username">
                      <input value={status?.adminUsername || form.admin.username} disabled />
                    </Field>
                    <Field label="Email">
                      <input type="email" value={status?.adminEmail || form.admin.email} disabled />
                    </Field>
                  </div>
                </>
              ) : (
                <>
                  {status?.adminConfigured && (
                    <div className="info-banner">
                      An administrator account already exists. Enter its current password if it is not
                      still using the factory default, then choose a new password.
                    </div>
                  )}
                  <div className="form-grid two-column">
                    <Field label="Username">
                      <input value={form.admin.username} onChange={e => update('admin', 'username', e.target.value)} />
                    </Field>
                    <Field label="Email">
                      <input type="email" value={form.admin.email} onChange={e => update('admin', 'email', e.target.value)} />
                    </Field>
                    <Field label="Current password" hint="Only required when an existing administrator has already changed the factory password.">
                      <input type="password" value={form.admin.currentPassword} onChange={e => update('admin', 'currentPassword', e.target.value)} autoComplete="current-password" />
                    </Field>
                    <Field label="New password">
                      <input type="password" value={form.admin.newPassword} onChange={e => update('admin', 'newPassword', e.target.value)} autoComplete="new-password" placeholder="At least 8 characters" />
                    </Field>
                    <Field label="Confirm new password">
                      <input type="password" value={form.admin.confirmPassword} onChange={e => update('admin', 'confirmPassword', e.target.value)} autoComplete="new-password" />
                    </Field>
                  </div>
                </>
              )}
            </>
          )}

          {step === 2 && (
            <>
              <div className="setup-section-heading">
                <h2>Storage, backup and email</h2>
                <p>
                  Google Drive is the recommended Tier 3 backup target. Automatic backups run in
                  the background, copying the complete SACCO database: member records and contact
                  names, savings, loans, SMS and WhatsApp messages. With OAuth, the same Google
                  account also sends member email through Gmail. Credentials are stored outside
                  the repository and are never written to the database.
                </p>
              </div>
              <div className="form-grid two-column">
                <Field label="Storage provider">
                  <select value={form.storage.provider} onChange={e => update('storage', 'provider', e.target.value)}>
                    <option value="GOOGLE_DRIVE">Google Drive (recommended)</option>
                    <option value="LOCAL">Local only</option>
                  </select>
                </Field>
                <Field label="Authentication">
                  <select value={form.storage.authMode} onChange={e => update('storage', 'authMode', e.target.value)}>
                    <option value="OAUTH">OAuth — Drive backups and Gmail email (recommended)</option>
                    <option value="SERVICE_ACCOUNT">Service account JSON — Drive only</option>
                    <option value="MOCK">Mock server (testing only)</option>
                  </select>
                </Field>
                <Field label="Drive folder ID" hint="Share the folder with the Google account as Editor (or the service-account email), then paste the folder ID.">
                  <input value={form.storage.folderId} onChange={e => update('storage', 'folderId', e.target.value)} />
                </Field>
                <Field label="Application name">
                  <input value={form.storage.applicationName} onChange={e => update('storage', 'applicationName', e.target.value)} />
                </Field>
                <Field
                  label="Google account email"
                  hint="The Gmail address that owns the Drive folder. This becomes the email sender for member reminders."
                >
                  <input
                    type="email"
                    value={form.storage.googleAccountEmail}
                    onChange={e => update('storage', 'googleAccountEmail', e.target.value)}
                    placeholder="sacco@gmail.com"
                  />
                </Field>
                <Field
                  label="Gmail API base URL"
                  hint="Leave the default for Google. The local mock server can be used during testing."
                >
                  <input value={form.storage.gmailBaseUrl} onChange={e => update('storage', 'gmailBaseUrl', e.target.value)} />
                </Field>
                <Field label="Automatic backup schedule" hint="The scheduler catches up immediately after the app was closed, like a phone backup.">
                  <select value={form.storage.schedule} onChange={e => update('storage', 'schedule', e.target.value)}>
                    <option value="HOURLY">Every hour</option>
                    <option value="DAILY">Once a day</option>
                    <option value="WEEKLY">Once a week</option>
                  </select>
                </Field>
                <Field label="Retention (days)">
                  <input type="number" min="1" max="3650" value={form.storage.retentionDays} onChange={e => update('storage', 'retentionDays', e.target.value)} />
                </Field>
                <Field label="Automatic backups" hint="On by default. The scheduler runs without staff having to press a button.">
                  <label className="setup-check">
                    <input
                      type="checkbox"
                      checked={Boolean(form.storage.automaticBackups)}
                      onChange={e => update('storage', 'automaticBackups', e.target.checked)}
                    />
                    Back up automatically
                  </label>
                </Field>
              </div>

              {form.storage.authMode === 'SERVICE_ACCOUNT' && (
                <div className="setup-inline-panel">
                  <div>
                    <strong>Service-account JSON</strong>
                    <p className="field-hint">
                      {status?.storage?.credentialsConfigured
                        ? `Stored: ${status.storage.credentialsFileName || 'credentials'}`
                        : 'Choose the JSON key downloaded from Google Cloud.'}
                      {' '}A service account can upload backups but cannot send Gmail. Choose
                      OAuth if you also want email reminders.
                    </p>
                  </div>
                  <label className="btn btn-ghost btn-sm setup-file-button">
                    {uploading ? 'Uploading…' : 'Choose JSON file'}
                    <input type="file" accept=".json,application/json" onChange={handleCredentialsUpload} disabled={uploading} />
                  </label>
                </div>
              )}

              {form.storage.authMode === 'OAUTH' && (
                <>
                  <div className="info-banner">
                    Create one OAuth refresh token for the Google account that owns the backup
                    folder. It must include both
                    <code> https://www.googleapis.com/auth/drive </code>
                    and
                    <code> https://www.googleapis.com/auth/gmail.send</code>.
                    That single token is reused for Drive uploads and Gmail sending.
                  </div>
                  <div className="form-grid two-column">
                    <Field label="OAuth client ID">
                      <input value={form.storage.oauthClientId} onChange={e => update('storage', 'oauthClientId', e.target.value)} />
                    </Field>
                    <Field label="OAuth client secret">
                      <input type="password" value={form.storage.oauthClientSecret} onChange={e => update('storage', 'oauthClientSecret', e.target.value)} />
                    </Field>
                    <Field label="Refresh token">
                      <input type="password" value={form.storage.oauthRefreshToken} onChange={e => update('storage', 'oauthRefreshToken', e.target.value)} />
                    </Field>
                  </div>
                </>
              )}

              {form.storage.authMode === 'MOCK' && (
                <div className="info-banner">
                  Mock mode is for testing the upload queue without Google credentials. The backup
                  worker will call the local mock server at the base URL below.
                </div>
              )}

              <div className="form-grid two-column">
                <Field label="Drive base URL" hint="Leave the default for Google. Use http://localhost:9098 for the mock server.">
                  <input value={form.storage.baseUrl} onChange={e => update('storage', 'baseUrl', e.target.value)} />
                </Field>
                <Field label="Token URI">
                  <input value={form.storage.tokenUri} onChange={e => update('storage', 'tokenUri', e.target.value)} />
                </Field>
              </div>

              <div className="setup-actions">
                <button type="button" className="btn btn-ghost" onClick={handleTestDrive} disabled={testing || testingEmail || saving}>
                  {testing ? 'Testing…' : 'Test Google Drive connection'}
                </button>
                {status?.storage?.connected && (
                  <span className="setup-connected">Drive connected</span>
                )}
                <button type="button" className="btn btn-ghost" onClick={handleTestEmail} disabled={testing || testingEmail || saving}>
                  {testingEmail ? 'Testing Gmail…' : 'Test Gmail connection'}
                </button>
                {status?.storage?.emailConnected && (
                  <span className="setup-connected">Gmail connected</span>
                )}
              </div>
              {status?.storage?.lastError && <div className="error-message">{status.storage.lastError}</div>}
              {status?.storage?.emailLastError && <div className="error-message">{status.storage.emailLastError}</div>}

              <div className="setup-inline-panel">
                <div>
                  <strong>Automatic backups {backupInfo.automaticEnabled ? 'are on' : 'are paused'}</strong>
                  <p className="field-hint">
                    {backupInfo.schedule ? `Schedule: ${String(backupInfo.schedule).toLowerCase()}. ` : ''}
                    Last backup: {formatDateTime(backupInfo.lastBackupAt)}
                    {backupInfo.lastBackupSizeBytes ? ` (${formatBytes(backupInfo.lastBackupSizeBytes)})` : ''}.
                    {backupInfo.automaticEnabled
                      ? ` Next backup: ${formatDateTime(backupInfo.nextBackupAt)}.`
                      : ' Use Run backup now below.'}
                    {' '}Each run is a complete copy of the database, including members, contact
                    names, savings, loans, SMS and WhatsApp messages.
                    {backupInfo.cloudConfigured
                      ? ' Google Drive uploads are enabled.'
                      : ` Cloud uploads stay queued until the Drive target is ready (${backupInfo.cloudTarget || 'not configured'}).`}
                  </p>
                  {backupInfo.lastError && <p className="field-hint">{backupInfo.lastError}</p>}
                </div>
                <button
                  type="button"
                  className="btn btn-ghost btn-sm"
                  onClick={handleRunBackup}
                  disabled={runningBackup || saving || testing || testingEmail}
                >
                  {runningBackup ? 'Running backup…' : 'Run backup now'}
                </button>
              </div>

              {form.storage.authMode !== 'SERVICE_ACCOUNT' && (
                <div className="setup-inline-panel">
                  <div>
                    <strong>Send a test email</strong>
                    <p className="field-hint">
                      Sends a real message through Gmail. Leave the recipient blank to use the
                      administrator or connected Google account.
                    </p>
                  </div>
                  <div className="setup-email-test">
                    <input
                      type="email"
                      value={testEmailTo}
                      onChange={e => setTestEmailTo(e.target.value)}
                      placeholder="recipient@example.com"
                    />
                    <button
                      type="button"
                      className="btn btn-ghost btn-sm"
                      onClick={handleSendTestEmail}
                      disabled={sendingTestEmail || testingEmail || saving}
                    >
                      {sendingTestEmail ? 'Sending…' : 'Send test email'}
                    </button>
                  </div>
                </div>
              )}
            </>
          )}

          {step === 3 && (
            <>
              <div className="setup-section-heading">
                <h2>Messaging and loan reminders</h2>
                <p>
                  These are the sender identities members will see when the SACCO sends instalment
                  reminders. Select only the channels you have been provisioned for.
                </p>
              </div>
              <div className="form-grid two-column">
                <Field label="SMS sender ID">
                  <input value={form.messaging.smsSenderId} onChange={e => update('messaging', 'smsSenderId', e.target.value)} placeholder="e.g. SCMS" />
                </Field>
                <Field label="WhatsApp display name">
                  <input value={form.messaging.whatsappDisplayName} onChange={e => update('messaging', 'whatsappDisplayName', e.target.value)} />
                </Field>
                <Field label="WhatsApp business number">
                  <input value={form.messaging.whatsappPhoneNumber} onChange={e => update('messaging', 'whatsappPhoneNumber', e.target.value)} placeholder="+256..." />
                </Field>
                <Field label="Voice caller ID">
                  <input value={form.messaging.voiceCallerId} onChange={e => update('messaging', 'voiceCallerId', e.target.value)} />
                </Field>
                <Field label="Email from name">
                  <input value={form.messaging.emailFromName} onChange={e => update('messaging', 'emailFromName', e.target.value)} />
                </Field>
                <Field
                  label="Email from address"
                  hint={form.storage.googleAccountEmail
                    ? 'Member email is sent from the Google account connected in Storage.'
                    : 'Connect a Google account in Storage to send email reminders.'}
                >
                  {form.storage.googleAccountEmail ? (
                    <input type="email" value={form.storage.googleAccountEmail} readOnly />
                  ) : (
                    <input type="email" value={form.messaging.emailFromAddress} onChange={e => update('messaging', 'emailFromAddress', e.target.value)} />
                  )}
                </Field>
                <Field label="Reply-to address">
                  <input type="email" value={form.messaging.emailReplyTo} onChange={e => update('messaging', 'emailReplyTo', e.target.value)} />
                </Field>
                <Field label="Reminder send time">
                  <input value={form.messaging.reminderSendTime} onChange={e => update('messaging', 'reminderSendTime', e.target.value)} placeholder="08:00" />
                </Field>
                <Field label="Days before due date" hint="Comma-separated, e.g. 5,3,1,0. Zero means the due date itself.">
                  <input value={form.messaging.reminderDaysBefore} onChange={e => update('messaging', 'reminderDaysBefore', e.target.value)} />
                </Field>
                <Field label="Grace period (days)">
                  <input type="number" min="0" max="90" value={form.messaging.reminderGraceDays} onChange={e => update('messaging', 'reminderGraceDays', e.target.value)} />
                </Field>
              </div>

              <Field label="Reminder channels">
                <div className="setup-channel-grid">
                  {CHANNELS.map(channel => (
                    <label key={channel.id} className="setup-check">
                      <input
                        type="checkbox"
                        checked={(form.messaging.reminderChannels || []).includes(channel.id)}
                        onChange={() => toggleChannel(channel.id)}
                      />
                      {channel.label}
                    </label>
                  ))}
                </div>
              </Field>

              {form.messaging.reminderChannels?.includes('EMAIL') && (
                <div className={status?.storage?.emailConnected ? 'info-banner' : 'error-message'}>
                  {status?.storage?.emailConnected
                    ? `Email reminders will be sent from ${status.storage.googleAccountEmail || form.storage.googleAccountEmail || 'the connected Google account'}.`
                    : 'Email reminders need a tested Gmail connection. Go back to Storage and test Gmail first.'}
                </div>
              )}

              <Field label="Loan reminder template" hint="Placeholders: {memberName}, {loanNumber}, {amount}, {currency}, {dueDate}, {organisationName}.">
                <textarea rows={4} value={form.messaging.reminderTemplate} onChange={e => update('messaging', 'reminderTemplate', e.target.value)} />
              </Field>

              <div className="form-grid two-column">
                {form.messaging.reminderChannels?.includes('WHATSAPP') && (
                  <>
                    <Field label="Approved WhatsApp reminder template" hint="Meta-approved utility template name used for automated instalment reminders, for example loan_due_reminder. Leave blank to fall back to free text inside the customer service window.">
                      <input value={form.messaging.reminderWhatsappTemplate || ''} onChange={e => update('messaging', 'reminderWhatsappTemplate', e.target.value)} placeholder="loan_due_reminder" />
                    </Field>
                    <Field label="WhatsApp reminder language">
                      <input value={form.messaging.reminderWhatsappLanguage || 'en'} onChange={e => update('messaging', 'reminderWhatsappLanguage', e.target.value)} placeholder="en" />
                    </Field>
                  </>
                )}
                <Field label="Voice calls enabled" hint="When enabled, the loan reminder engine can place an Africa Talking outbound voice call with text-to-speech.">
                  <select value={form.messaging.voiceEnabled ? 'yes' : 'no'} onChange={e => update('messaging', 'voiceEnabled', e.target.value === 'yes')}>
                    <option value="no">No</option>
                    <option value="yes">Yes</option>
                  </select>
                </Field>
                <Field label="Voice quiet hours start" hint="Voice calls are suppressed before this time (HH:mm, 24-hour).">
                  <input value={form.messaging.voiceQuietHoursStart || '08:00'} onChange={e => update('messaging', 'voiceQuietHoursStart', e.target.value)} placeholder="08:00" />
                </Field>
                <Field label="Voice quiet hours end" hint="Voice calls are suppressed from this time (HH:mm, 24-hour) onwards.">
                  <input value={form.messaging.voiceQuietHoursEnd || '18:00'} onChange={e => update('messaging', 'voiceQuietHoursEnd', e.target.value)} placeholder="18:00" />
                </Field>
              </div>

              <div className="form-grid two-column">
                <Field label="Consent required">
                  <select value={form.messaging.consentRequired ? 'yes' : 'no'} onChange={e => update('messaging', 'consentRequired', e.target.value === 'yes')}>
                    <option value="yes">Yes — only message opted-in members</option>
                    <option value="no">No</option>
                  </select>
                </Field>
                <Field label="Opt-out wording">
                  <input value={form.messaging.optOutText} onChange={e => update('messaging', 'optOutText', e.target.value)} />
                </Field>
              </div>
            </>
          )}

          {step === 4 && (
            <>
              <div className="setup-section-heading">
                <h2>Communication billing (optional)</h2>
                <p>
                  Optionally record the account that pays for SMS, WhatsApp, email and voice charges
                  and the unit rates used for cost estimates. This step can be left blank and
                  completed later from System Settings. No payment is initiated from this screen.
                </p>
              </div>
              <div className="form-grid two-column">
                <Field label="Billing provider">
                  <input value={form.billing.provider} onChange={e => update('billing', 'provider', e.target.value)} />
                </Field>
                <Field label="Billing account name">
                  <input value={form.billing.accountName} onChange={e => update('billing', 'accountName', e.target.value)} />
                </Field>
                <Field label="Billing account / number">
                  <input value={form.billing.accountNumber} onChange={e => update('billing', 'accountNumber', e.target.value)} placeholder="Paybill, account number or card reference" />
                </Field>
                <Field label="Billing currency">
                  <input value={form.billing.currency} onChange={e => update('billing', 'currency', e.target.value.toUpperCase())} maxLength={10} />
                </Field>
                <Field label="Billing contact email">
                  <input type="email" value={form.billing.contactEmail} onChange={e => update('billing', 'contactEmail', e.target.value)} />
                </Field>
                <Field label="Who bears the cost?">
                  <select value={form.billing.chargeBearer} onChange={e => update('billing', 'chargeBearer', e.target.value)}>
                    <option value="SACCO">SACCO</option>
                    <option value="MEMBER">Member</option>
                  </select>
                </Field>
              </div>

              <div className="setup-section-heading compact">
                <h3>Per-channel unit charges</h3>
                <p>Used to estimate communication spend and to show the cost of a reminder campaign.</p>
              </div>
              <div className="form-grid four-column">
                <Field label="SMS per message">
                  <input type="number" min="0" step="0.0001" value={form.billing.smsUnitCost} onChange={e => update('billing', 'smsUnitCost', e.target.value)} />
                </Field>
                <Field label="WhatsApp per message">
                  <input type="number" min="0" step="0.0001" value={form.billing.whatsappUnitCost} onChange={e => update('billing', 'whatsappUnitCost', e.target.value)} />
                </Field>
                <Field label="Email per message">
                  <input type="number" min="0" step="0.0001" value={form.billing.emailUnitCost} onChange={e => update('billing', 'emailUnitCost', e.target.value)} />
                </Field>
                <Field label="Voice per call">
                  <input type="number" min="0" step="0.0001" value={form.billing.voiceUnitCost} onChange={e => update('billing', 'voiceUnitCost', e.target.value)} />
                </Field>
                <Field label="Low balance threshold">
                  <input type="number" min="0" step="0.01" value={form.billing.lowBalanceThreshold} onChange={e => update('billing', 'lowBalanceThreshold', e.target.value)} />
                </Field>
                <Field label="Prepaid balance">
                  <input type="number" min="0" step="0.01" value={form.billing.prepaidBalance} onChange={e => update('billing', 'prepaidBalance', e.target.value)} />
                </Field>
              </div>
            </>
          )}

          {step === 5 && (
            <>
              <div className="setup-section-heading">
                <h2>{editingLive ? 'Review live settings' : 'Review and go live'}</h2>
                <p>
                  {editingLive
                    ? 'These values are active now. Use the step buttons above to change a section; each save takes effect immediately.'
                    : 'Confirm the configuration. Business pages stay blocked until setup is complete.'}
                </p>
              </div>

              {status?.missingFields?.length > 0 && (
                <div className="setup-missing">
                  <strong>Still required</strong>
                  <ul>
                    {status.missingFields.map(item => <li key={item}>{item}</li>)}
                  </ul>
                </div>
              )}

              <div className="setup-review-grid">
                <div className="setup-review-card">
                  <h4>Organisation</h4>
                  <p>{status?.organization?.legalName || 'Not set'}</p>
                  <p>{status?.organization?.addressLine1 || 'Address not set'}</p>
                  <p>{status?.organization?.phone || 'Phone not set'} · {status?.organization?.email || 'Email not set'}</p>
                </div>
                <div className="setup-review-card">
                  <h4>Administrator</h4>
                  <p>{status?.adminUsername || 'Not set'}</p>
                  <p>{status?.adminEmail || 'Email not set'}</p>
                  <p>{status?.adminUsesDefaultPassword ? 'Factory password still active' : 'Password updated'}</p>
                </div>
                <div className="setup-review-card">
                  <h4>Storage</h4>
                  <p>{status?.storage?.provider === 'GOOGLE_DRIVE' ? 'Google Drive' : 'Local only'}</p>
                  <p>{status?.storage?.connected ? 'Drive connection verified' : 'Drive connection not verified'}</p>
                  <p>{status?.storage?.googleAccountEmail || 'Google account not set'}</p>
                  <p>{status?.storage?.emailConnected ? 'Gmail connected' : 'Gmail not connected'}</p>
                  <p>
                    Automatic backups: {status?.storage?.automaticBackupsEnabled ? 'on' : 'paused'}
                    {status?.storage?.schedule ? ` — ${String(status.storage.schedule).toLowerCase()}` : ''}
                  </p>
                  <p>Last backup: {formatDateTime(status?.storage?.backup?.lastBackupAt)}</p>
                </div>
                <div className="setup-review-card">
                  <h4>Messaging</h4>
                  <p>{(status?.messaging?.reminderChannels || []).join(', ') || 'No channels selected'}</p>
                  <p>Due-date reminders: {status?.messaging?.reminderDaysBefore?.join(', ') || 'Not set'}</p>
                  <p>{status?.messaging?.smsSenderId ? `SMS sender: ${status.messaging.smsSenderId}` : 'SMS sender not set'}</p>
                  <p>{status?.storage?.emailConnected ? `Email sender: ${status.storage.googleAccountEmail || status.messaging?.emailFromAddress}` : 'Email sender not connected'}</p>
                </div>
                <div className="setup-review-card">
                  <h4>Billing (optional)</h4>
                  <p>{status?.billing?.accountName || 'Not configured'}</p>
                  <p>{status?.billing?.accountNumber || 'Can be added later'}</p>
                  <p>{status?.billing?.currency || 'No billing currency set'}</p>
                </div>
              </div>
            </>
          )}
        </div>

        <div className="setup-footer">
          <div>
            {step > 0 && (
              <button type="button" className="btn btn-ghost" onClick={() => { setError(null); setNotice(null); setStep(step - 1) }} disabled={saving || runningBackup}>
                Back
              </button>
            )}
          </div>
          <div className="setup-footer-actions">
            {step < STEPS.length - 1 ? (
              <button type="button" className="btn btn-primary" onClick={handleNext} disabled={saving || uploading || testing || testingEmail || sendingTestEmail || runningBackup}>
                {saving ? 'Saving…' : 'Save and continue'}
              </button>
            ) : editingLive ? (
              <button type="button" className="btn btn-primary" onClick={() => { window.location.href = '/' }}>
                Done
              </button>
            ) : (
              <button type="button" className="btn btn-primary" onClick={handleComplete} disabled={saving || status?.missingFields?.length > 0}>
                {saving ? 'Completing…' : (embedded ? 'Save and finish' : 'Complete setup and go live')}
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

export default Setup
