# Africa's Talking SMS Integration

This document covers the production SMS integration in `com.sacco.communication`:
outgoing SMS, the transactional outbox, delivery-report and incoming-SMS
webhooks, OTP verification, and a sandbox/ngrok test procedure.

---

## 1. Architecture at a glance

```
Staff API / OTP service
        │
        ▼
SmsService ──► sms_messages (QUEUED) ──► SmsDispatcher (every 5 s)
                                             │
                                             ▼
                                  AfricasTalkingClient (REST)
                                             │
                                             ▼
                                  Africa's Talking /version1/messaging
                                             │
                     ┌───────────────────────┴───────────────────────┐
                     ▼                                               ▼
        /webhooks/.../delivery                              /webhooks/.../incoming
        sms_delivery_reports                                incoming_sms + sms_opt_outs
                     │
                     ▼
        sms_messages status: SENT → DELIVERED / FAILED
```

Why an outbox instead of RabbitMQ/Redis:

- The SACCO deployment is a single Spring Boot application backed by
  PostgreSQL. The outbox adds no infrastructure to operate.
- `SELECT … FOR UPDATE SKIP LOCKED` lets several application instances run the
  dispatcher without sending the same row twice.
- Retries survive restarts, and every attempt/status is visible in SQL for
  support and audit.
- If volume later justifies a broker, `SmsDispatcher` is the only component that
  needs to change; the public API and schema stay the same.

---

## 2. Configuration

All secrets are environment variables. The full block lives under
`sacco.africastalking` in `src/main/resources/application.yml`.

### Required for real sends

| Variable | Purpose | Example |
|---|---|---|
| `AT_ENABLED` | Master switch | `true` |
| `AT_USERNAME` | AT username; `sandbox` in sandbox | `sandbox` |
| `AT_API_KEY` | AT API key | `atsk_...` |
| `AT_SENDER_ID` | Optional sender ID / short code | `SCMS` |
| `AT_SANDBOX` | Use sandbox host when `AT_BASE_URL` is blank | `true` |
| `AT_WEBHOOK_TOKEN` | High-entropy token in callback path | `openssl rand -hex 32` |
| `AT_OTP_PEPPER` | Stable HMAC pepper for OTP hashes | `openssl rand -hex 32` |

`AT_WEBHOOK_TOKEN` and `AT_OTP_PEPPER` are intentionally separate. Never reuse
the JWT signing key for either.

### Optional tuning

| Variable | Default | Meaning |
|---|---:|---|
| `AT_BASE_URL` | sandbox/production host | Override for a mock or proxy |
| `AT_DEFAULT_COUNTRY_CODE` | `UG` | Region for local numbers like `0772…` |
| `AT_WEBHOOK_ALLOWED_IPS` | empty | Comma-separated IPs/CIDRs |
| `AT_WEBHOOK_TRUST_FORWARDED_HEADERS` | `false` | Trust `X-Forwarded-For` / `X-Real-IP` behind ngrok/nginx |
| `AT_MAX_MESSAGE_LENGTH` | `1000` | Hard body limit |
| `AT_BLOCK_OPTED_OUT` | `true` | Block sends to STOP numbers |
| `AT_DISPATCH_INTERVAL_MS` | `5000` | Outbox poll interval |
| `AT_DISPATCH_BATCH_SIZE` | `50` | Rows claimed per tick |
| `AT_DISPATCH_MAX_ATTEMPTS` | `5` | Attempts before FAILED |
| `AT_DISPATCH_BASE_BACKOFF_MS` | `30000` | First retry delay |
| `AT_DISPATCH_MAX_BACKOFF_MS` | `3600000` | Backoff ceiling |
| `AT_BULK_LIMIT` | `500` | Max recipients per API request |
| `AT_OTP_TTL_SECONDS` | `300` | OTP lifetime |
| `AT_OTP_MAX_ATTEMPTS` | `5` | Wrong-code attempts |
| `AT_OTP_RESEND_COOLDOWN_SECONDS` | `60` | Resend cooldown |
| `AT_OTP_MAX_PER_PHONE` | `5` | Requests per phone per window |
| `AT_OTP_MAX_PER_IP` | `20` | Requests per IP per window |
| `AT_OTP_WINDOW_MINUTES` | `15` | Rate-limit window |
| `AT_OTP_REQUIRE_ACTIVE_MEMBER` | `false` | Only send OTPs to active members |

For production, set `AT_OTP_REQUIRE_ACTIVE_MEMBER=true` unless you have a public
registration flow that must verify unregistered numbers. Consider a CAPTCHA or
equivalent on the public OTP endpoint to reduce SMS pumping/toll fraud.

### Starting the application

PowerShell:

```powershell
$env:AT_ENABLED = "true"
$env:AT_USERNAME = "sandbox"
$env:AT_API_KEY = "your-sandbox-api-key"
$env:AT_SENDER_ID = "SCMS"
$env:AT_SANDBOX = "true"
$env:AT_WEBHOOK_TOKEN = "replace-with-openssl-rand-hex-32"
$env:AT_OTP_PEPPER = "replace-with-a-different-openssl-rand-hex-32"
java -jar target\sacco-core-management-1.0-SNAPSHOT.jar
```

Bash:

```bash
export AT_ENABLED=true
export AT_USERNAME=sandbox
export AT_API_KEY='your-sandbox-api-key'
export AT_SENDER_ID=SCMS
export AT_SANDBOX=true
export AT_WEBHOOK_TOKEN="$(openssl rand -hex 32)"
export AT_OTP_PEPPER="$(openssl rand -hex 32)"
java -jar target/sacco-core-management-1.0-SNAPSHOT.jar
```

The application still starts with `AT_ENABLED=false`. In that mode the
dispatcher pauses and immediate sends return HTTP 503; webhook storage and OTP
validation still work if a code was already issued.

---

## 3. Database schema

Flyway migration: `src/main/resources/db/migration/V14__africastalking_sms.sql`.

| Table | Purpose |
|---|---|
| `sms_messages` | Outbox and message history; statuses `QUEUED`, `SENDING`, `SENT`, `DELIVERED`, `FAILED`, `REJECTED`, `EXPIRED` |
| `sms_delivery_reports` | Raw AT delivery callbacks; `processed=false` when the outbox row has no provider id yet |
| `incoming_sms` | Incoming messages, de-duplicated by `provider_message_id` |
| `sms_opt_outs` | STOP/START state per E.164 number |
| `phone_verifications` | OTP challenges; only the HMAC-SHA256 hash is stored |

Useful queries:

```sql
-- Recent outbox state
SELECT message_id, recipient, status, attempts, provider_message_id,
       next_attempt_at, failure_reason, created_at
FROM sms_messages
ORDER BY message_id DESC
LIMIT 20;

-- Delivery reports that still need reconciliation
SELECT report_id, provider_message_id, status, received_at
FROM sms_delivery_reports
WHERE processed = false
ORDER BY report_id;

-- Active opt-outs
SELECT phone_number, reason, source, updated_at
FROM sms_opt_outs
WHERE active = true
ORDER BY updated_at DESC;

-- Active OTP challenges (hashes only)
SELECT verification_id, phone_number, purpose, expires_at, attempts
FROM phone_verifications
WHERE consumed_at IS NULL
ORDER BY created_at DESC;
```

---

## 4. API reference

All staff SMS endpoints require a JWT with `ROLE_ADMIN`, `ROLE_MANAGER`,
`ROLE_TELLER`, or `ROLE_LOAN_OFFICER` (send) or `ROLE_AUDITOR` (view). The
`actorId` query parameter mirrors the existing controllers, but when a JWT is
present the authenticated user is authoritative.

### 4.1 Send one SMS

`POST /api/communications/sms/send`

```json
{
  "recipient": "0772123456",
  "message": "Your SACCO account notification is ready.",
  "type": "NOTIFICATION",
  "senderId": "SCMS",
  "memberId": 12,
  "immediate": false
}
```

`immediate=false` (recommended) returns `QUEUED` immediately and lets the
dispatcher deliver. `immediate=true` waits for the provider; on a transient
failure the row is still queued for retry and the response may be `QUEUED`.

### 4.2 Send a bulk SMS

`POST /api/communications/sms/send-bulk`

```json
{
  "recipients": ["0772123456", "+256782123456"],
  "message": "Members' meeting on Friday at 10:00.",
  "type": "NOTIFICATION",
  "immediate": false
}
```

Each recipient gets its own outbox row (per-recipient status and retry). Rows
for one request share a `batchId`. The API caps a request at `AT_BULK_LIMIT`
(default 500). Synchronous delivery groups recipients into AT calls of 100.

### 4.3 List, inspect and retry

```http
GET  /api/communications/sms/messages?status=FAILED&recipient=772&page=0&size=20
GET  /api/communications/sms/messages/{messageId}
POST /api/communications/sms/messages/{messageId}/retry
GET  /api/communications/sms/summary
GET  /api/communications/sms/incoming?page=0&size=20
```

Only `FAILED`, `REJECTED` and `EXPIRED` messages can be retried manually.

### 4.4 OTP request and verify (public)

`POST /api/communications/otp/request`

```json
{
  "phone": "0772123456",
  "purpose": "REGISTRATION"
}
```

Purposes: `REGISTRATION`, `LOGIN`, `PASSWORD_RESET`, `PHONE_CHANGE`,
`TRANSACTION`.

`POST /api/communications/otp/verify`

```json
{
  "phone": "0772123456",
  "purpose": "REGISTRATION",
  "code": "123456"
}
```

Security behaviour:

- 6-digit `SecureRandom` code; only `HMAC-SHA256(pepper, phone|purpose|code)` is
  stored.
- 5-minute TTL, 5 attempts, single-use, atomic verification.
- Per-phone/per-IP windows and a resend cooldown return HTTP 429 with
  `Retry-After`.
- A code is invalidated if the SMS cannot be sent.
- A successful verification is audited as `OTP_VERIFIED`.

### 4.5 Webhooks

```
POST /api/webhooks/africastalking/{AT_WEBHOOK_TOKEN}/delivery
POST /api/webhooks/africastalking/{AT_WEBHOOK_TOKEN}/incoming
```

Aliases: `/delivery-report` and `/incoming-message`.

These endpoints are public to Spring Security because AT cannot attach a JWT.
They are authenticated by the path token (constant-time comparison) and,
optionally, an IP/CIDR allowlist. If `AT_WEBHOOK_TOKEN` is blank the endpoints
fail closed with 401. AT retries non-2xx responses; duplicate incoming messages
are de-duplicated by provider message id and still acknowledged with 200.

AT sends form-urlencoded callbacks by default; the parser also accepts JSON.

---

## 5. Sandbox test procedure

### 5.1 Get sandbox credentials

1. Create/sign in to an Africa's Talking account.
2. Open the Sandbox app and copy the API key.
3. Use `AT_USERNAME=sandbox`, `AT_SANDBOX=true`.
4. In the sandbox dashboard, add/confirm a test phone number if prompted.
5. Start the application with the environment variables above.

### 5.2 Expose localhost with ngrok

```bash
ngrok http 8081
```

Copy the HTTPS forwarding URL, for example
`https://a1b2c3d4.ngrok-free.app`.

If you use `AT_WEBHOOK_ALLOWED_IPS`, set
`AT_WEBHOOK_TRUST_FORWARDED_HEADERS=true` so ngrok's `X-Forwarded-For` header is
used. Otherwise leave it `false`; trusting the header without a proxy lets a
caller spoof its source address.

### 5.3 Configure AT callback URLs

In the AT dashboard, set:

```
Delivery report URL:
https://a1b2c3d4.ngrok-free.app/api/webhooks/africastalking/<AT_WEBHOOK_TOKEN>/delivery

Incoming message URL:
https://a1b2c3d4.ngrok-free.app/api/webhooks/africastalking/<AT_WEBHOOK_TOKEN>/incoming
```

The token must match `AT_WEBHOOK_TOKEN` exactly. Generate it with
`openssl rand -hex 32` and do not put it in source control.

### 5.4 Send a test SMS

```bash
TOKEN=$(curl -s http://127.0.0.1:8081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | jq -r .token)

curl -s http://127.0.0.1:8081/api/communications/sms/send \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "recipient": "0772123456",
    "message": "SCMS sandbox test",
    "type": "TEST",
    "immediate": true
  }' | jq
```

Then inspect:

```sql
SELECT message_id, recipient, status, provider_message_id, provider_status,
       attempts, failure_reason
FROM sms_messages
ORDER BY message_id DESC
LIMIT 5;
```

AT's sandbox may simulate delivery rather than reaching a real handset. The
sandbox dashboard/log is the source of truth for the provider response.

### 5.5 Simulate a delivery report

Use the provider message id returned by the send (or from `sms_messages`):

```bash
curl -i -X POST \
  "http://127.0.0.1:8081/api/webhooks/africastalking/$AT_WEBHOOK_TOKEN/delivery" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'id=ATXid_xxxxxxxx' \
  --data-urlencode 'status=Success' \
  --data-urlencode 'phoneNumber=+256772123456' \
  --data-urlencode 'networkCode=63902' \
  --data-urlencode 'failureReason=' \
  --data-urlencode 'retryCount=0'
```

Expected: HTTP 200 and the matching `sms_messages.status` becomes `DELIVERED`
with `delivered_at` set. A late `status=Sent` callback must not downgrade it.

### 5.6 Simulate an incoming message and opt-out

```bash
curl -i -X POST \
  "http://127.0.0.1:8081/api/webhooks/africastalking/$AT_WEBHOOK_TOKEN/incoming" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'id=ATXid_incoming_1' \
  --data-urlencode 'from=+256772123456' \
  --data-urlencode 'to=12345' \
  --data-urlencode 'text=STOP'
```

Expected:

- HTTP 200.
- One `incoming_sms` row, `processed=true`.
- One active `sms_opt_outs` row for the sender.
- A subsequent send to that number returns HTTP 409 until the member replies
  `START` (or staff reactivates the number in SQL).

Send `START` the same way to clear the opt-out.

### 5.7 Test OTP end to end

```bash
curl -s http://127.0.0.1:8081/api/communications/otp/request \
  -H 'Content-Type: application/json' \
  -d '{"phone":"0772123456","purpose":"REGISTRATION"}' | jq

curl -s http://127.0.0.1:8081/api/communications/otp/verify \
  -H 'Content-Type: application/json' \
  -d '{"phone":"0772123456","purpose":"REGISTRATION","code":"123456"}' | jq
```

In sandbox, read the code from the AT sandbox dashboard or the test handset.
The application never logs the plaintext code.

To test the whole flow without AT credentials, point `AT_BASE_URL` at a local
mock server that accepts `POST /version1/messaging` and returns the AT JSON
shape:

```json
{
  "SMSMessageData": {
    "Message": "Sent to 1/1",
    "Recipients": [
      {
        "statusCode": 101,
        "number": "+256772123456",
        "cost": "UGX 30",
        "status": "Success",
        "messageId": "ATXid_mock_1"
      }
    ]
  }
}
```

Then capture the outbound form body from the mock, extract the OTP, and call
`/verify`. This is useful in CI or when the sandbox account is not available.

---

## 6. Resilience and error handling

- **Timeouts:** connect 5 s and read 15 s by default. A hung provider call cannot
  hold a request thread indefinitely.
- **E.164 enforcement:** libphonenumber validates and normalizes local,
  international-without-plus and E.164 input before persistence.
- **Retryable failures:** timeouts, 429, 5xx, routing/queue failures. Rows go back
  to `QUEUED` with exponential backoff and ±20% jitter, capped at
  `AT_DISPATCH_MAX_BACKOFF_MS`.
- **Permanent failures:** invalid number, bad credentials, malformed message.
  Rows become `FAILED` for staff review.
- **Stale sends:** a row stuck in `SENDING` longer than
  `AT_DISPATCH_STALE_SENDING_SECONDS` is requeued after a crash.
- **Delivery-report race:** reports are stored even if the outbox row has not yet
  received its provider id; the dispatcher reconciles them later.
- **Duplicates:** incoming SMS is de-duplicated by provider message id; a chunk
  that partially succeeds is not requeued for rows already marked `SENT`.
- **Opt-outs:** STOP/STOPALL/UNSUBSCRIBE/CANCEL/END/QUIT activate an opt-out;
  START/UNSTOP/SUBSCRIBE clear it. `AT_BLOCK_OPTED_OUT=true` blocks sends.
- **HTTP mapping:** validation 400, opt-out 409, rate limit 429 with
  `Retry-After`, provider unavailable 503, provider rejection 502.

---

## 7. Production checklist

- [ ] `AT_ENABLED=true` and real production username/API key.
- [ ] `AT_SANDBOX=false` (or an explicit production `AT_BASE_URL`).
- [ ] `AT_WEBHOOK_TOKEN` is 32+ random bytes and is not in source control.
- [ ] `AT_OTP_PEPPER` is stable across restarts and different from the JWT key.
- [ ] Callback URLs use HTTPS and point at the production host.
- [ ] `AT_WEBHOOK_ALLOWED_IPS` is populated with AT's published callback ranges
      where feasible; `AT_WEBHOOK_TRUST_FORWARDED_HEADERS` is `true` only behind
      a trusted proxy/load balancer.
- [ ] `AT_OTP_REQUIRE_ACTIVE_MEMBER=true` unless public registration requires
      otherwise; add CAPTCHA/bot protection to the public OTP endpoint.
- [ ] Database backups include the SMS/OTP tables (the existing backup service
      covers the whole schema).
- [ ] Monitor `sms_messages` rows in `FAILED` and `sms_delivery_reports` rows
      with `processed=false`.
- [ ] Review retention for `incoming_sms`, `sms_delivery_reports` and
      `phone_verifications`; `OtpService.purgeExpired()` removes OTP challenges
      older than 30 days.
- [ ] Ensure the application is behind TLS; the webhook token travels in the URL
      path because AT cannot add custom headers.

---

## 8. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Immediate send returns 503 | `AT_ENABLED`, username or API key missing | Set the three env vars and restart |
| AT returns 401/403 | Wrong API key or username | Re-copy sandbox/production credentials |
| Webhook returns 401 | Token missing/incorrect, or `AT_WEBHOOK_TOKEN` blank | Match the path token exactly; configure the env var |
| Webhook returns 401 from an allowlisted setup | Source IP mismatch | Check `AT_WEBHOOK_ALLOWED_IPS`; enable forwarded-header trust only behind the proxy |
| Delivery report does not update the row | Report arrived before provider id, or wrong `id` | Check `sms_delivery_reports.processed=false`; the dispatcher retries reconciliation |
| OTP request returns 429 | Phone/IP window or resend cooldown | Wait for `Retry-After`, or inspect `phone_verifications` |
| OTP verify always says no active code | Code expired, consumed, or different purpose | Request a new code for the same purpose |
| Bulk send returns 409 | Every recipient is opted out | Check `sms_opt_outs`; reply START |
| Dispatcher appears idle | `AT_ENABLED=false` or credentials missing | Check startup env and logs |
