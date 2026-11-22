# SS MediaOS SMS Gateway — Policy Notes

These in-app policies are operational summaries and must be reviewed against the deployed API, telecom provider configuration, and current applicable law before production release.

## Privacy
- The Android app is designed to send authorized outbound SMS jobs and must not read or forward incoming SMS/OTP inbox messages.
- The app may send the configured device identifier, API activation credentials, recipient number, message required for an authorized job, request/job ID, timestamps, and send result/error to the configured SS MediaOS API.
- Use HTTPS, protect tokens, restrict staff access, and define a documented retention period on the server.
- A registered mobile number must only be changed after server-side verification.

## Telecom and SMS use
- Process only jobs authorized by the API and associated with a verified entity/account.
- Do not send spam, fraud, harassment, impersonation, or unlawful messages.
- The messaging organization must comply with applicable TRAI/DoT, access-provider, DLT, sender/header, template, consent, and category requirements. This app does not itself register an entity, header, or template.
- Queued/submitted/sent/failed/delivered are distinct statuses. Mark Delivered only when a reliable provider/API delivery receipt confirms it.

## DND, consent, opt-out
- Respect DND preferences and current telecom rules for each message category. Do not assume every available number is eligible.
- Obtain and record consent where required; use messages only for the permitted purpose.
- Promotional communications must follow the applicable commercial-communications/DLT requirements. Do not mislabel promotional content as transactional to bypass restrictions.
- Provide and promptly honor opt-out/preference controls where required; keep server-side suppression records.
- If eligibility, consent, category, or sender configuration cannot be verified, the API should reject the job. Never bypass DND, provider restrictions, rate limits, or legal controls.

## Production checklist
Before release, the service operator should add verified support/contact details, confirm actual data retention and deletion procedures, review the privacy notice with legal counsel, and verify current official TRAI/telecom-provider requirements for the exact message categories in use.
