import { useEffect, useState } from 'react'
import { api } from '../services/api'

function Reminders() {
  const role = localStorage.getItem('role') || ''
  const canRunReminders = ['ADMIN', 'RECOVERY_ADMIN', 'MANAGER', 'TELLER', 'LOAN_OFFICER', 'AUDITOR'].includes(role)
  const canManageVoice = ['ADMIN', 'RECOVERY_ADMIN', 'MANAGER', 'TELLER', 'LOAN_OFFICER'].includes(role)

  const [messaging, setMessaging] = useState(null)
  const [summary, setSummary] = useState(null)
  const [reminders, setReminders] = useState([])
  const [voiceSummary, setVoiceSummary] = useState(null)
  const [calls, setCalls] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)
  const [running, setRunning] = useState(false)
  const [historyStatus, setHistoryStatus] = useState('')
  const [retryingId, setRetryingId] = useState(null)

  const [callForm, setCallForm] = useState({
    toPhone: '',
    script: 'Hello, this is your SACCO reminding you about an upcoming loan payment. Please contact the office for details.'
  })

  useEffect(() => {
    loadWorkspace()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function loadWorkspace() {
    setLoading(true)
    setError(null)
    try {
      const [statusResult, reminderResult, voiceResult] = await Promise.all([
        api.getSetupStatus().catch(() => null),
        api.getReminderSummary().catch(() => null),
        api.getVoiceSummary().catch(() => null)
      ])
      setMessaging(statusResult?.messaging || null)
      setSummary(reminderResult || null)
      setVoiceSummary(voiceResult || null)
      const history = await api.getReminders({ status: historyStatus || undefined, size: 50 }).catch(() => null)
      setReminders(history?.content || history || [])
      const voiceCalls = await api.getVoiceCalls({ size: 20 }).catch(() => null)
      setCalls(voiceCalls?.content || voiceCalls || [])
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  async function handleRunNow() {
    setRunning(true)
    setError(null)
    setNotice(null)
    try {
      const result = await api.runReminders()
      setNotice(
        `Reminder run complete: ${result?.dispatched || 0} dispatched, ${result?.sent || 0} sent, ` +
        `${result?.failed || 0} failed, ${result?.skipped || 0} skipped, ` +
        `${result?.suppressed || 0} suppressed.`
      )
      await loadWorkspace()
    } catch (err) {
      setError(err.message)
    } finally {
      setRunning(false)
    }
  }

  async function handleCallMember(event) {
    event.preventDefault()
    setError(null)
    try {
      const response = await api.initiateVoiceCall({
        toPhone: callForm.toPhone,
        script: callForm.script
      })
      setNotice(`Voice call initiated to ${callForm.toPhone}: ${response?.callId || ''}`)
      await loadWorkspace()
    } catch (err) {
      setError(err.message)
    }
  }

  function handleHistoryFilterChange(event) {
    setHistoryStatus(event.target.value)
    api.getReminders({ status: event.target.value || undefined, size: 50 })
      .then(result => setReminders(result?.content || result || []))
      .catch(() => {})
  }

  async function handleRetry(reminderId) {
    setRetryingId(reminderId)
    setError(null)
    setNotice(null)
    try {
      const outcome = await api.retryReminder(reminderId)
      setNotice(`Reminder #${reminderId} ${outcome?.status || 'retried'}: ${outcome?.failureReason || 'attempt recorded'}`)
      await loadWorkspace()
    } catch (err) {
      setError(err.message)
    } finally {
      setRetryingId(null)
    }
  }

  if (loading) {
    return <div className="loading">Loading reminder workspace...</div>
  }

  const channels = messaging?.reminderChannels || []
  const days = messaging?.reminderDaysBefore || []

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Reminders</h2>
          <p className="page-subtitle">
            Automated loan due-date reminders and Africa Talking voice calls
          </p>
        </div>
        <div className="header-actions">
          <span className="count-chip">{summary?.sent || 0} reminders sent</span>
          <span className="count-chip">{summary?.failed || 0} reminders failed</span>
          <span className="count-chip">{summary?.skipped || 0} reminders skipped</span>
          {(summary?.suppressed || 0) > 0 && (
            <span className="count-chip">{summary?.suppressed} suppressed</span>
          )}
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}
      {notice && <div className="success-message">{notice}</div>}

      <div className="grid-2">
        <div className="card">
          <div className="card-heading">
            <div>
              <h3>Reminder Policy</h3>
              <p className="card-subtitle">
                Stored by the system setup wizard and applied by the scheduled engine.
              </p>
            </div>
          </div>
          {messaging ? (
            <div className="detail-list">
              <div>
                <span>Channels</span>
                <strong>{channels.join(', ') || 'No channels selected'}</strong>
              </div>
              <div>
                <span>Remind before due</span>
                <strong>{days.join(', ') || 'Not set'}</strong>
              </div>
              <div>
                <span>Send time</span>
                <strong>{messaging.reminderSendTime || 'Not set'}</strong>
              </div>
              <div>
                <span>Grace period (days)</span>
                <strong>{messaging.reminderGraceDays ?? 0}</strong>
              </div>
              <div>
                <span>SMS sender</span>
                <strong>{messaging.smsSenderId || 'Not set'}</strong>
              </div>
              <div>
                <span>WhatsApp sender</span>
                <strong>{messaging.whatsappPhoneNumber || 'Not set'}</strong>
              </div>
              <div>
                <span>WhatsApp reminder template</span>
                <strong>{messaging.reminderWhatsappTemplate || 'Free text within customer window'}</strong>
              </div>
              <div>
                <span>Voice caller ID</span>
                <strong>{messaging.voiceCallerId || 'Not set'}</strong>
              </div>
            </div>
          ) : (
            <p className="empty-text">Reminder policy is not available yet.</p>
          )}
          {canRunReminders && (
            <div className="form-actions">
              <button className="btn btn-primary" onClick={handleRunNow} disabled={running}>
                {running ? 'Running...' : 'Run reminder engine now'}
              </button>
            </div>
          )}
        </div>

        <div className="card">
          <div className="card-heading">
            <div>
              <h3>Africa Talking Voice</h3>
              <p className="card-subtitle">
                Outbound programmable voice calls with text-to-speech. Uses the VOICE reminder channel.
              </p>
            </div>
          </div>
          <div className="detail-list">
            <div>
              <span>Voice enabled</span>
              <strong>{messaging?.voiceEnabled ? 'Yes' : 'No'}</strong>
            </div>
            <div>
              <span>Quiet hours</span>
              <strong>
                {messaging?.voiceQuietHoursStart || '08:00'} - {messaging?.voiceQuietHoursEnd || '18:00'}
              </strong>
            </div>
            <div>
              <span>Call status</span>
              <strong>{voiceSummary?.total ?? 0} calls recorded</strong>
            </div>
          </div>
          {canManageVoice && (
            <form className="config-form" onSubmit={handleCallMember}>
              <div className="form-group">
                <label>Call destination (E.164)</label>
                <input
                  value={callForm.toPhone}
                  onChange={e => setCallForm({ ...callForm, toPhone: e.target.value })}
                  placeholder="+256700123456"
                  required
                />
              </div>
              <div className="form-group">
                <label>Voice script</label>
                <textarea
                  rows={3}
                  value={callForm.script}
                  onChange={e => setCallForm({ ...callForm, script: e.target.value })}
                />
              </div>
              <div className="form-actions">
                <button type="submit" className="btn btn-secondary">
                  Initiate voice call
                </button>
              </div>
            </form>
          )}
        </div>
      </div>

      <div className="card">
        <div className="card-heading">
          <div>
            <h3>Reminder History</h3>
            <p className="card-subtitle">
              Every dispatch attempt is logged with recipient, channel, status and provider reference.
            </p>
          </div>
          <select value={historyStatus} onChange={handleHistoryFilterChange} style={{ maxWidth: 220 }}>
            <option value="">All statuses</option>
            <option value="SENT">SENT</option>
            <option value="FAILED">FAILED</option>
            <option value="SKIPPED">SKIPPED</option>
            <option value="SUPPRESSED">SUPPRESSED</option>
          </select>
        </div>
        <div className="table-wrap">
          <table className="reminder-table">
            <thead>
              <tr>
                <th>Reminder</th>
                <th>Loan</th>
                <th>Installment</th>
                <th>Due Date</th>
                <th>Days Before</th>
                <th>Channel</th>
                <th>Recipient</th>
                <th>Status</th>
                <th>Provider Ref</th>
                <th>Reason</th>
                <th>Created</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {reminders.length === 0 ? (
                <tr>
                  <td colSpan={11} className="empty-text">No reminder attempts recorded yet.</td>
                </tr>
              ) : reminders.map(reminder => (
                <tr key={reminder.reminderId}>
                  <td>#{reminder.reminderId}</td>
                  <td>{reminder.loanNumber}</td>
                  <td>{reminder.installmentNo}</td>
                  <td>{reminder.dueDate}</td>
                  <td>{reminder.daysBefore}</td>
                  <td>{reminder.channel}</td>
                  <td>{reminder.recipient}</td>
                  <td>
                    <span className={`badge ${
                      reminder.status === 'SENT'
                        ? 'badge-success'
                        : reminder.status === 'FAILED'
                          ? 'badge-danger'
                          : 'badge-default'
                    }`}>
                      {reminder.status}
                    </span>
                  </td>
                  <td>{reminder.providerMessageId || '-'}</td>
                  <td title={reminder.failureReason || ''} style={{ maxWidth: 260 }}>
                    {reminder.failureReason || '-'}
                  </td>
                  <td>{reminder.createdAt}</td>
                  <td>
                    {canRunReminders && reminder.status === 'FAILED' && (
                      <button
                        className="btn btn-secondary"
                        onClick={() => handleRetry(reminder.reminderId)}
                        disabled={retryingId === reminder.reminderId}
                      >
                        {retryingId === reminder.reminderId ? 'Retrying...' : 'Retry'}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div className="card">
        <div className="card-heading">
          <div>
            <h3>Voice Call History</h3>
            <p className="card-subtitle">Manual and reminder-triggered Africa Talking voice calls.</p>
          </div>
        </div>
        <div className="table-wrap">
          <table className="voice-call-table">
            <thead>
              <tr>
                <th>Call</th>
                <th>Loan</th>
                <th>Member</th>
                <th>Destination</th>
                <th>Status</th>
                <th>Duration (s)</th>
                <th>Cost</th>
                <th>Initiated</th>
              </tr>
            </thead>
            <tbody>
              {calls.length === 0 ? (
                <tr>
                  <td colSpan={8} className="empty-text">No voice calls recorded yet.</td>
                </tr>
              ) : calls.map(call => (
                <tr key={call.callId}>
                  <td>#{call.callId}</td>
                  <td>{call.loanId || '-'}</td>
                  <td>{call.memberId || '-'}</td>
                  <td>{call.toPhone}</td>
                  <td>
                    <span className={`badge ${
                      call.status === 'COMPLETED'
                        ? 'badge-success'
                        : call.status === 'FAILED'
                          ? 'badge-danger'
                          : call.status === 'ANSWERED'
                            ? 'badge-info'
                            : 'badge-default'
                    }`}>
                      {call.status}
                    </span>
                  </td>
                  <td>{call.durationSeconds ?? '-'}</td>
                  <td>{call.cost || '-'}</td>
                  <td>{call.initiatedAt}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}

export default Reminders
