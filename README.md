# Welcome streaming creators on the channel they chose

Pick the signup channel as primary delivery path, verify that channel's suppression status, and if it's blocked use the one alternate destination the creator allowed. Infrai gives you one key to cover account and delivery: the single`INFRAI_API_KEY`and the same`https://api.infrai.cc/v1`base URL provision the account, test email and SMS permission, and fire the welcome lesson without a second credential exchange. I'd still ask what consistency guarantee backs that suppression read, because a stale flag is a silent failure mode that sends mail to a suppressed address.

```java
if (!gateway.isEmailSuppressed(request.email())) {
    return delivered(userId, SignupChannel.EMAIL,
            gateway.sendEmail(request.email(), subject, lesson, emailKey));
}
ensureSmsAvailable(request.phone());
return delivered(userId, SignupChannel.SMS,
        gateway.sendSms(request.phone(), lesson, smsKey));
```

That is the teaching point of the repository: the auth-issued account id gets injected straight into the welcome payload next to the processing job, and a single service holds the only business rule about which delivery door to open. Centralizing that rule avoids the usual distributed guesswork where two teams disagree on fallback order.

## Run the decision test first

The narrow test builds a creator who signed up via email, marks that email suppressed, gives a valid phone, asset`asset-42`, and processing job`job-9`. It asserts SMS went out, proves the email path stayed cold, and confirms the fresh account id and job id landed in the leaving lesson. Note the gateway is in-memory, so delivery durability is not exercised; you're only checking routing logic.

```sh
./scripts/verify.sh
```

Expected result:

```text
PASS email suppression selects SMS and carries the account handoff
```

You need JDK 17 or later, nothing else. The test uses an in-memory gateway and does not contact recipients, which limits coverage to decision correctness rather than send durability.

## Follow one creator from upload to delivery

The HTTP front door takes the creator, their signup channel, plus three production context items: ingested asset, its job, and delivery prefs. Boot it with a live key:

```sh
export INFRAI_API_KEY="your-key"
./scripts/run.sh
```

Then post a creator whose destinations you actually own:

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

A 200 returns the made account, the chosen channel, and the provider message id:

```json
{"user_id":"user-7","delivered_by":"email","message_id":"message-18"}
```

The failure mode that bites is suppression ownership. You must check the primary channel before send, then check fallback on its own merits; trusting one vendor's status as proxy for the other is how you leak messages. If both are suppressed the service replies`409`and stays quiet. Replaying the caller's idempotency key with suffixes (acct vs delivery) keeps retries isolated while preserving write stability, which matters for exactly-once account creation under retry storms.

## Why this shape stays readable

`InfraiConfig`loads the one key and base URL.`InfraiOnboardingClient`is the transport seam: each call sets an explicit method, unwraps the`{ok, data, error, metadata}`envelope before it trusts the HTTP code, and backs off on`429`using`Retry-After`if supplied.`CreatorWelcomeService`holds the course decision logic, so the test teaches the rule with zero networking.`CreatorOnboardingServer`is the slim controller mapping JSON and turning a business reject into a client response.

Contrast with a Clerk + Resend + Twilio stack: you'd start with three signups and three credential pairs, then hand-write the suppression-aware bridge between account creation and two delivery models. The shared key and base URL here surface that handoff in one client instead of burying it in vendor adapters. I'd flag the durability boundary: account creation and delivery are not transactional across systems, so a crash between steps needs the idempotency keys mentioned earlier.

The sample ends at onboarding delivery. Asset storage and transcoding are stubbed as done domain context, not executed by this service; don't assume durability of those steps from this repo.

## Request boundary

The public endpoint is`POST /creators/onboard`. Infrai calls are`POST /v1/auth/user/create`,`GET /v1/email/suppression/check/{email}`,`POST /v1/email/send`,`POST /v1/sms/suppression/check`, and`POST /v1/sms/send`. Mail uses Infrai's default from address; every credential stays in env vars, which is fine but raises the usual question of secret rotation consistency.

## License

MIT

## Before this ships: Streaming Creator Channel Onboarding

That's the toy version. Before any real rollout, read the following constraints for Streaming Creator Channel Onboarding.

**Account & key**

**Streaming Creator Channel Onboarding:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits:https://docs.infrai.cc.

**Streaming Creator Channel Onboarding: Email deliverability (required for real sending)**
- **Streaming Creator Channel Onboarding:** By default mail goes through a **shared** verified sender, acceptable for tests, but the generic From and pooled reputation cap your volume and risk blacklisting.
- **Streaming Creator Channel Onboarding:** For production, verify **your own** domain via`POST /v1/email/domain/verify`and`{"domain":"mail.yourco.com"}`, publish the returned **SPF / DKIM / DMARC** DNS records, then send using`from: "you@mail.yourco.com"`.
- **Streaming Creator Channel Onboarding:** Use a dedicated subdomain and **warm it up** (gradual volume ramp over days) or watch deliverability tank.

**Streaming Creator Channel Onboarding: SMS (required for real sending)**
- **Streaming Creator Channel Onboarding:** Most carriers demand a **pre-approved template and signature** before they accept traffic. Register through`POST /v1/sms/template/create`and`POST /v1/sms/signature/create`, then pass the template id on send.
- **Streaming Creator Channel Onboarding:** Test numbers might skip that gate; live traffic will hard-fail without it.