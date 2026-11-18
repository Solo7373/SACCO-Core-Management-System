# First-run system setup

The application is not usable for official SACCO operations until the
first-run setup is complete. On a fresh installation the setup wizard is the
first screen; on an existing installation the administrator signs in first and
then finishes the wizard.

The setup is stored in a single `system_settings` row created by Flyway
migration `V16__system_setup.sql`. Secrets are **not** stored in that row:
Google credentials are written to a protected directory outside the repository
and the database only records their path.

---

## 1. What is blocked

While `setup_completed = false`, business APIs return HTTP `428 Precondition
Required` with a message that setup must be completed. The following paths stay
reachable so the system can be commissioned safely:

- `/api/setup/**` - the wizard itself
- `/api/auth/**` - sign-in, password change and administrator recovery
- `/api/webhooks/**` - Africa's Talking and Meta callbacks
- `/api/communications/otp/**` - member OTP requests

Set `SACCO_SETUP_ENFORCE=false` only for a controlled development environment.
The default is `true`.

---

## 2. The six steps

### Organisation

Legal name, trading name, registration number, tax PIN, physical address,
postal address lines, city, country, phone, email, website, timezone and
currency. These values identify the SACCO on statements, demand letters and
member messages.

Required to go live: legal name, physical address, phone and email.

### Administrator

Creates the first administrator on a new installation, or rotates the existing
administrator's credentials during an upgrade.

- A new installation has no factory account. The setup creates the first
  `ADMIN` and returns a signed-in session so the remaining steps can run.
- An existing installation must sign in before changing anything. If the old
  `admin / admin123` factory account is still present, setup flags it and
  requires a different password.
- If the administrator password has already been changed, the current password
  is required before the account can be rotated.

The default `admin / admin123` account is no longer seeded by the application.

### Storage

Google Drive is the recommended storage target, and also becomes the Tier 3
backup target. The Google account selected here can also send member email
through Gmail, so one OAuth connection covers both backups and email.

| Mode | Use |
| --- | --- |
| `OAUTH` | Recommended when email reminders are needed. Create one OAuth client, authorize the account that owns the backup folder, and record the client ID, client secret and refresh token. The token must include `https://www.googleapis.com/auth/drive` and `https://www.googleapis.com/auth/gmail.send`. |
| `SERVICE_ACCOUNT` | Drive only. Download a JSON key from Google Cloud, share the backup folder with the service-account email as **Editor**, upload the JSON in the wizard, and paste the folder ID. A service account has no Gmail mailbox. |
| `MOCK` | Local testing only. The Drive and Gmail clients call the mock server at the configured base URLs (default `http://localhost:9098`). |

`LOCAL` can be selected when Google Drive is not available yet. In that case
the configured backup queue mode is used and Drive fields are not required to
go live. Email still requires an OAuth connection if the email channel is
selected later.

The wizard verifies the folder with a read-only Drive request before setup can
be completed. The Google account email is verified with a Gmail profile request
and can be confirmed with a real test send. The verified Drive and Gmail states
are reset whenever the folder, Google account, provider, authentication mode or
Gmail endpoint changes.

Credentials are stored in `GDRIVE_SECRETS_DIR`, defaulting to
`~/.sacco/secrets`:

- `google-service-account.json`
- `google-oauth.json`

Never commit these files. Never place them inside the repository.

#### Automatic backups

Automatic backups are **on by default**, like the WhatsApp backup model. The
scheduler stored in the setup row copies the complete database without staff
having to press a button:

- every hour, once a day, or once a week, selected in the wizard;
- immediately on the next check after the application was closed past its due
  time, so downtime does not skip a backup;
- to the Tier 1 host directory first, then mirrored to the Tier 2 external
  directory, then queued for the Tier 3 cloud target.

A snapshot is a full `pg_dump` of the database. It includes member records and
contact names, savings, shares, loans, collateral, inventory, audit history and
the SMS and WhatsApp message tables. That is the SACCO equivalent of backing up
messages and chat names.

The retention setting prunes `sacco_*.sql` files older than the configured
number of days from the Tier 1 and Tier 2 directories. The setup wizard shows
the last and next backup and has a **Run backup now** button, so the engine can
be proven before go-live. The Backups workspace shows the same automatic
status after setup is complete.

Set `SACCO_BACKUP_SCHEDULER_ENABLED=false` only for maintenance windows where
no background backup should run. The schedule and retention are changed in
system setup, not through environment variables.

### Messaging and loan reminders

The sender identities members will see:

- SMS sender ID (Africa's Talking alphanumeric sender)
- WhatsApp business display name and business phone number
- Email from name, from address and reply-to address
- Voice caller ID

The email address is the Google account connected in Storage. Setup verifies
the Gmail profile and can send a real test message before go-live. Email
reminders cannot be selected until Gmail is connected, and a service-account
Drive connection cannot send email.

The loan reminder policy records:

- Days before the due date (for example `5,3,1,0`; zero is the due date)
- Enabled channels: SMS, WhatsApp, email and/or voice
- Reminder send time (`HH:mm`)
- Grace period in days
- Message template with `{memberName}`, `{loanNumber}`, `{amount}`,
  `{currency}`, `{dueDate}` and `{organisationName}` placeholders
- Consent requirement and opt-out wording

The SMS sender ID configured here is used for outbound SMS when the
`AT_SENDER_ID` environment variable is blank. The WhatsApp and voice channel
identities still need to be provisioned in the Meta and Africa's Talking
dashboards; setup records the identity the SACCO intends to use.

### Communication billing

Records the account that handles communication charges:

- Billing provider and account name
- Billing account / number (paybill, account number or card reference)
- Billing currency and contact email
- Whether the SACCO or the member bears the cost
- Unit charges for SMS, WhatsApp, email and voice
- Low-balance threshold and prepaid balance

**No payment is initiated from this screen.** The values are stored so
campaigns and reports can estimate communication spend. Actual provider
billing remains in Africa's Talking and Meta.

### Review

The review step shows the saved organisation, administrator, storage,
messaging and billing values. Completion is blocked until every required item
is present, including a successful Drive connection test when Google Drive is
selected and a successful Gmail connection test when email reminders are
selected.

---

## 3. Access rules

| Situation | Setup access |
| --- | --- |
| Fresh installation, no administrator | Wizard is public so the first administrator can be created |
| An administrator exists, setup incomplete | Sign in as `ADMIN` (or `RECOVERY_ADMIN`); mutations require `SETTINGS_MANAGE` |
| Setup complete | Open **System Settings** in the sidebar; mutations require `SETTINGS_MANAGE` |

`SETTINGS_MANAGE` is granted to `ADMIN` and `RECOVERY_ADMIN` and is deliberately
removed from `MANAGER`, `TELLER`, `LOAN_OFFICER`, `AUDITOR` and `MEMBER`.

### Live system settings

After go-live, **System Settings** opens the same six sections in edit mode.
The administrator chooses any section, changes the values and saves it; there is
no need to redeploy, edit environment files or restart the backend. This covers:

- SACCO / business legal name, trading name, registration number, tax PIN,
  physical address, official phone, official email, website, timezone and
  currency;
- sender identities for SMS, WhatsApp, email and voice, plus the loan-reminder
  dates, channels, time, grace period, template and opt-out wording;
- the communication billing account, contact email and per-channel unit rates;
- automatic backup schedule, retention and a manual **Run backup now**;
- Google Drive and Gmail connection settings.

The saved identity is used on member financial statements, demand letters and
compliance exports. Transaction fees and tax rates remain in **Finance
Settings**. Administrator passwords are rotated from **Change Password** in the
sidebar, not from the live settings screen.

The public `/api/setup/status` endpoint returns only the setup flags after an
administrator exists. Organisation, sender and billing details are returned
only to an authenticated administrator.

---

## 4. Upgrade note for an existing database

The first start after this change applies V16 and creates the table, but not a
row. The row is created when setup status is first requested.

If the database still contains the old `admin / admin123` account:

1. Start the backend and open the application.
2. Sign in with the old factory credentials.
3. The wizard flags `adminUsesDefaultPassword` and requires a different
   password before setup can be completed.
4. Complete storage, messaging and billing.
5. Complete setup and verify that business pages load normally.

If the factory password was already changed, sign in with the current password
and confirm it in the Administrator step.

---

## 5. Environment variables

| Variable | Default | Purpose |
| --- | --- | --- |
| `SACCO_SETUP_ENFORCE` | `true` | Return HTTP 428 for business APIs until setup is complete |
| `GDRIVE_AUTH_MODE` | `SERVICE_ACCOUNT` | Fallback Drive auth mode before the wizard saves one |
| `GDRIVE_BASE_URL` | `https://www.googleapis.com` | Drive API base URL |
| `GDRIVE_TOKEN_URI` | `https://oauth2.googleapis.com/token` | OAuth token endpoint |
| `GDRIVE_FOLDER_ID` | empty | Fallback backup folder ID |
| `GDRIVE_APPLICATION_NAME` | `SCMS` | Application name sent to Google |
| `GDRIVE_CREDENTIALS_FILE` | empty | Path to an existing service-account JSON |
| `GDRIVE_SECRETS_DIR` | `~/.sacco/secrets` | Protected directory for wizard-uploaded credentials |
| `GDRIVE_TIMEOUT_MS` | `30000` | HTTP timeout for Drive calls |
| `GMAIL_ENABLED` | `true` | Allow outbound email through the Gmail API |
| `GMAIL_BASE_URL` | `https://gmail.googleapis.com` | Fallback Gmail API base URL |
| `GMAIL_TIMEOUT_MS` | `30000` | HTTP timeout for Gmail calls |
| `SACCO_BACKUP_CLOUD_MODE` | `QUEUE_ONLY` | Fallback backup mode before setup is complete |
| `SACCO_BACKUP_SCHEDULER_ENABLED` | `true` | Run automatic backups in the background |
| `SACCO_BACKUP_SCHEDULER_CHECK_INTERVAL_MS` | `60000` | How often the scheduler checks whether a snapshot is due |
| `SACCO_BACKUP_SCHEDULER_INITIAL_DELAY_MS` | `60000` | Delay before the first automatic-backup check after startup |

---

## 6. API walkthrough

All calls use JSON unless noted. A fresh installation allows the wizard
without a token; once the administrator is created, use the returned token for
the remaining steps.

```powershell
$base = "http://localhost:8081/api/setup"

# 1. Read status
Invoke-RestMethod "$base/status"

# 2. Organisation
Invoke-RestMethod "$base/organization" -Method Post -ContentType "application/json" -Body (@{
  legalName = "Example SACCO Limited"
  addressLine1 = "Plot 1, Main Street"
  city = "Kampala"
  country = "Uganda"
  phone = "+256700000000"
  email = "info@example-sacco.local"
  timezone = "Africa/Kampala"
  currency = "UGX"
} | ConvertTo-Json)

# 3. Administrator (returns a token on a fresh installation)
$admin = Invoke-RestMethod "$base/admin" -Method Post -ContentType "application/json" -Body (@{
  username = "admin"
  email = "admin@example-sacco.local"
  newPassword = "ChangeThisNow123!"
  confirmPassword = "ChangeThisNow123!"
} | ConvertTo-Json)
$headers = @{ Authorization = "Bearer $($admin.token)" }

# 4. Storage (OAuth: Drive backups and Gmail email)
Invoke-RestMethod "$base/storage" -Method Post -Headers $headers -ContentType "application/json" -Body (@{
  provider = "GOOGLE_DRIVE"
  authMode = "OAUTH"
  folderId = "your-shared-folder-id"
  googleAccountEmail = "sacco@gmail.com"
  gmailBaseUrl = "https://gmail.googleapis.com"
  applicationName = "SCMS"
  retentionDays = 30
  schedule = "HOURLY"
  oauthClientId = "your-client-id"
  oauthClientSecret = "your-client-secret"
  oauthRefreshToken = "your-refresh-token"
} | ConvertTo-Json)

# Verify Drive and Gmail separately
Invoke-RestMethod "$base/storage/test" -Method Post -Headers $headers
Invoke-RestMethod "$base/email/test" -Method Post -Headers $headers

# Send a real test message. The recipient is optional and defaults to the
# administrator email or the connected Google account.
Invoke-RestMethod "$base/email/send-test" -Method Post -Headers $headers -ContentType "application/json" -Body (@{
  to = "admin@example-sacco.local"
} | ConvertTo-Json)

# Drive-only alternative: use authMode = "SERVICE_ACCOUNT", then upload the
# service-account JSON (multipart). A service account cannot send Gmail.
Invoke-RestMethod "$base/storage/credentials" -Method Post -Headers $headers -Form @{
  file = Get-Item "C:\path\to\service-account.json"
}

# Test the connection
Invoke-RestMethod "$base/storage/test" -Method Post -Headers $headers

# 5. Messaging
Invoke-RestMethod "$base/messaging" -Method Post -Headers $headers -ContentType "application/json" -Body (@{
  smsSenderId = "SCMS"
  whatsappDisplayName = "Example SACCO"
  whatsappPhoneNumber = "+256700000000"
  reminderChannels = @("SMS", "WHATSAPP")
  reminderDaysBefore = @(5, 3, 1, 0)
  reminderSendTime = "08:00"
  reminderTemplate = "Dear {memberName}, your loan {loanNumber} instalment of {currency} {amount} is due on {dueDate}. Please pay on time. {organisationName}"
  consentRequired = $true
  optOutText = "Reply STOP to opt out of SMS reminders."
} | ConvertTo-Json)

# 6. Billing
Invoke-RestMethod "$base/billing" -Method Post -Headers $headers -ContentType "application/json" -Body (@{
  provider = "Africa's Talking / Meta"
  accountName = "Example SACCO"
  accountNumber = "AT-123456"
  currency = "UGX"
  chargeBearer = "SACCO"
  smsUnitCost = 25
  whatsappUnitCost = 0
  emailUnitCost = 0
  voiceUnitCost = 120
  lowBalanceThreshold = 50000
  prepaidBalance = 0
} | ConvertTo-Json)

# 7. Complete
Invoke-RestMethod "$base/complete" -Method Post -Headers $headers
```

For `MOCK` storage testing, set `authMode = "MOCK"` and
`baseUrl = "http://localhost:9098"`, start the mock Drive server, then call the
test endpoint.
