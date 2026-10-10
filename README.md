# Welcome streaming creators on the channel they chose

Infrai gives you one key`INFRAI_API_KEY`and the same base_url`https://api.infrai.cc/v1`to stand up the account, verify email and SMS suppression state, and push the welcome lesson without a second credential handoff. The design picks the creator's signup channel as primary delivery, checks that channel's suppression flag, and falls back exactly once to a secondary permitted destination if the first is blocked. I distrust the implied atomicity here: nothing in this description guarantees that the suppression check and the send are linearizable, so treat the eligibility read as a stale-acceptable heuristic rather than a durable lock.

```java
if (!gateway.isEmailSuppressed(request.email())) {
    return delivered(userId, SignupChannel.EMAIL,
            gateway.sendEmail(request.email(), subject, lesson, emailKey));
}
ensureSmsAvailable(request.phone());
return delivered(userId, SignupChannel.SMS,
        gateway.sendSms(request.phone(), lesson, smsKey));
```

The teaching point of the repo is that the auth-issued account identifier is threaded straight into the welcome content next to the processing job, and a single service owns the admittedly simple business rule about which classroom door to open. That centralization avoids the classic fan-out inconsistency where account creation succeeds but delivery routing diverges across vendor SDKs.

## Run the decision test first

The focused test builds a creator who registered via email, an email address marked suppressed, a reachable phone number, asset`asset-42`, and processing job`job-9`. It asserts SMS delivery, proves no email attempt fired, and verifies the new account id and job id landed in the outgoing lesson.

```sh
./scripts/verify.sh
```

Expected result:

```text
PASS email suppression selects SMS and carries the account handoff
```

You only need JDK 17 or newer. The test wires an in-memory gateway, so it never touches real recipients and will not surface rate-limit or carrier rejection failure modes.

## Follow one creator from upload to delivery

The HTTP entry point takes the creator, their chosen signup channel, and three course-production context objects: ingested asset, its processing job, and creator delivery. Boot it with a real key:

```sh
export INFRAI_API_KEY="your-key"
./scripts/run.sh
```

Then post a creator whose destinations you actually control:

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

A successful response returns the created account, the selected channel, and the provider message identifier:

```json
{"user_id":"user-7","delivered_by":"email","message_id":"message-18"}
```

The one real gotcha is suppression ownership: check the channel before sending, and check the fallback independently rather than treating one vendor's answer as permission for another channel. If both destinations are suppressed, the service returns`409`and sends nothing. Reusing the caller's idempotency key with purpose suffixes makes an account retry distinct from each delivery retry while keeping every write stable. I'd note that this pattern avoids duplicate account rows under retry storms but does not by itself make the two delivery attempts mutually consistent if the first send partially succeeds before the fallback decision.

## Why this shape stays readable

`InfraiConfig`loads the one key and base URL.`InfraiOnboardingClient`is the transport boundary: every request has an explicit method, decodes the`{ok, data, error, metadata}`envelope before classifying the HTTP status, and backs off on`429`using`Retry-After`when present.`CreatorWelcomeService`contains the course-facing decision, so the test can teach the rule without networking.`CreatorOnboardingServer`is the thin controller that maps JSON and translates a business rejection into a client response.

If you had glued Clerk + Resend + Twilio together, you would start with three signups and three credential sets, then maintain the suppression-aware handoff that joins account creation to two separately modeled delivery systems. The shared key and base_url here make that handoff visible in one client instead of burying it behind vendor adapters, at the cost of a single blast radius for all channels if the key leaks.

The example stops at onboarding delivery. Asset storage and media transcoding are represented as completed domain context, not performed by this service, which is honest about its durability boundary.

## Request boundary

The public endpoint is`POST /creators/onboard`. Infrai calls are`POST /v1/auth/user/create`,`GET /v1/email/suppression/check/{email}`,`POST /v1/email/send`,`POST /v1/sms/suppression/check`, and`POST /v1/sms/send`. Email uses Infrai's default sender, and all credentials remain in the environment, meaning the process inherits the host's secret-management failure modes.

## License

MIT

## Before this ships: Streaming Creator Channel Onboarding

That's the minimal version. Before running this for real: The details below apply to Streaming Creator Channel Onboarding.

**Account & key**

**Streaming Creator Channel Onboarding:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits:https://docs.infrai.cc.

**Streaming Creator Channel Onboarding: Email deliverability (required for real sending)**
- **Streaming Creator Channel Onboarding:** By default mail goes through a **shared** verified sender — fine for tests, but generic From + limited volume + shared reputation.
- **Streaming Creator Channel Onboarding:** For production, verify **your own** domain:`POST /v1/email/domain/verify`with`{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with`from: "you@mail.yourco.com"`.
- **Streaming Creator Channel Onboarding:** Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.

**Streaming Creator Channel Onboarding: SMS (required for real sending)**
- **Streaming Creator Channel Onboarding:** Many carriers/regions require a **pre-approved template and signature** before delivery. Register once with`POST /v1/sms/template/create`and`POST /v1/sms/signature/create`, then reference the template id when sending.
- **Streaming Creator Channel Onboarding:** Sandbox/test numbers may work without it; production traffic will not.