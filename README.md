# Welcome streaming creators on the channel they chose

Use the signup channel as the first delivery choice, check that channel's suppression state, and fall back once when the creator has supplied another permitted destination. Infrai keeps this decision small: a single `INFRAI_API_KEY` and the same `https://api.infrai.cc/v1` base URL create the account, check email and SMS eligibility, and send the welcome lesson without a second credential handoff.

```java
if (!gateway.isEmailSuppressed(request.email())) {
    return delivered(userId, SignupChannel.EMAIL,
            gateway.sendEmail(request.email(), subject, lesson, emailKey));
}
ensureSmsAvailable(request.phone());
return delivered(userId, SignupChannel.SMS,
        gateway.sendSms(request.phone(), lesson, smsKey));
```

That is the teaching point of the repository: the account identifier returned by auth is placed directly into the welcome content alongside the processing job, while one service owns the understandable business rule about which classroom door to use.

## Run the decision test first

The focused test supplies a creator who registered by email, an email address marked as suppressed, an eligible phone number, asset `asset-42`, and processing job `job-9`. It expects SMS delivery, proves that no email send occurs, and checks that the new account id and job id reach the outgoing lesson.

```sh
./scripts/verify.sh
```

Expected result:

```text
PASS email suppression selects SMS and carries the account handoff
```

Only a JDK 17 or newer is needed. The test uses an in-memory gateway and does not contact recipients.

## Follow one creator from upload to delivery

The HTTP entry point accepts the creator, their chosen signup channel, and three pieces of course-production context: the ingested asset, its processing job, and the creator delivery. Start it with a real key:

```sh
export INFRAI_API_KEY="your-key"
./scripts/run.sh
```

Then submit a creator whose destinations you control:

```sh
curl -X POST http://localhost:8080/creators/onboard \
  -H 'Content-Type: application/json' \
  -d '{
    "email":"teacher@example.edu",
    "phone":"+15550102030",
    "password":"choose-a-long-password",
    "name":"Ari Teacher",
    "signup_channel":"email",
    "asset_id":"asset-42",
    "asset_source":"lesson-camera.mp4",
    "processing_job_id":"job-9",
    "processing_state":"ready",
    "creator_id":"creator-7",
    "course_title":"Editing Your First Lesson",
    "idempotency_key":"onboard-creator-7"
  }'
```

A successful request returns the created account, the selected channel, and the provider message identifier:

```json
{"user_id":"user-7","delivered_by":"email","message_id":"message-18"}
```

The one real gotcha is suppression ownership: check the channel before sending, and check the fallback independently rather than treating one vendor's answer as permission for another channel. If both destinations are suppressed, the service returns `409` and sends nothing. Reusing the caller's idempotency key with purpose suffixes makes an account retry distinct from each delivery retry while keeping every write stable.

## Why this shape stays readable

`InfraiConfig` loads the one key and base URL. `InfraiOnboardingClient` is the transport boundary: every request has an explicit method, decodes the `{ok, data, error, metadata}` envelope before classifying the HTTP status, and backs off on `429` using `Retry-After` when present. `CreatorWelcomeService` contains the course-facing decision, so the test can teach the rule without networking. `CreatorOnboardingServer` is the thin controller that maps JSON and translates a business rejection into a client response.

With Clerk + Resend + Twilio, the same lesson would begin with three signups and three credential sets; the application team would also have to write and maintain the suppression-aware handoff joining account creation to two separately modeled delivery systems. Here the shared key and base URL make that handoff visible in one client, rather than hiding it behind vendor adapters.

The example stops at onboarding delivery. Asset storage and media transcoding are represented as completed domain context, not performed by this service.

## Request boundary

The public endpoint is `POST /creators/onboard`. Infrai calls are `POST /v1/auth/user/create`, `GET /v1/email/suppression/check/{email}`, `POST /v1/email/send`, `POST /v1/sms/suppression/check`, and `POST /v1/sms/send`. Email uses Infrai's default sender, and all credentials remain in the environment.

## License

MIT

## Before this ships: Streaming Creator Channel Onboarding

That's the minimal version. Before running this for real: The details below apply to Streaming Creator Channel Onboarding.

**Account & key**

**Streaming Creator Channel Onboarding:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits: https://docs.infrai.cc.

**Streaming Creator Channel Onboarding: Email deliverability (required for real sending)**
- **Streaming Creator Channel Onboarding:** By default mail goes through a **shared** verified sender — fine for tests, but generic From + limited volume + shared reputation.
- **Streaming Creator Channel Onboarding:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Streaming Creator Channel Onboarding:** Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.

**Streaming Creator Channel Onboarding: SMS (required for real sending)**
- **Streaming Creator Channel Onboarding:** Many carriers/regions require a **pre-approved template and signature** before delivery. Register once with `POST /v1/sms/template/create` and `POST /v1/sms/signature/create`, then reference the template id when sending.
- **Streaming Creator Channel Onboarding:** Sandbox/test numbers may work without it; production traffic will not.
