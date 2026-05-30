# Ebay Lister Go-Live Roadmap

## Big Picture
To turn this app into a sellable product, move from prototype behavior to a secure, multi-tenant, API-driven SaaS.

Core pillars:
1. Product definition
2. Mobile production hardening
3. Vision/OCR quality
4. Pricing/comps reliability
5. eBay production API integration
6. Backend platform reliability
7. Security/compliance
8. Billing and monetization
9. QA/release operations
10. Go-to-market and support

---

## 1) Product Definition (What You Are Selling)
1. Define ideal customer profile (casual flippers, full-time resellers, thrift teams).
2. Define core promise (example: photo to listing draft in under 60 seconds).
3. Define plans (Free, Pro, Team) and usage limits.
4. Define north-star KPI (time-to-list, listings/user/week, weekly retained sellers).

Deliverables:
- Pricing page draft
- Tier matrix
- KPI dashboard spec

---

## 2) Mobile App Production Hardening
1. Add real user authentication (not device-only state).
2. Sync drafts to backend (user-scoped), not only SharedPreferences.
3. Add offline queue and retry behavior.
4. Add crash reporting and performance tracing.
5. Add analytics funnel events (capture -> analyze -> save draft -> publish).
6. Remove hardcoded secrets/tokens from client code.

Deliverables:
- Auth flow
- Cloud draft sync
- Telemetry instrumentation
- Secure token handling

---

## 3) Vision/OCR Quality Pipeline
1. Prioritize OCR text extraction (model numbers, UPC/EAN, FCC IDs, labels).
2. Add confidence thresholds and human confirmation when low.
3. Keep correction learning loop and evaluate impact.
4. Build benchmark dataset from real thrift/garage-sale photos.
5. Track metrics: exact-match title accuracy, variant accuracy, confidence calibration.

Deliverables:
- Evaluation dataset
- Weekly quality report
- Confidence UX rules

---

## 4) Pricing and Comps Engine
1. Use sold comps as primary source with source transparency.
2. Keep outlier filtering and category-aware rules.
3. Show explainability: sample count, range, median confidence.
4. Add cache TTL policy by category and volatility.
5. Add fallback labeling: "Estimated Market Value" when exact comps are unavailable.

Deliverables:
- Pricing policy doc
- Source fallback policy
- Price confidence rubric

---

## 5) eBay Production Integration (Key Shift)
1. Move from app handoff to deterministic API-driven draft creation.
2. Implement eBay OAuth connect flow and token refresh lifecycle.
3. Build listing payload mapper: title, specifics, condition, price, shipping, returns.
4. Upload photos via eBay-compatible media pipeline.
5. Return draft/listing IDs and sync status back to app.
6. Keep optional "open in eBay" UX as secondary convenience.

Important: deep links to prefilled eBay mobile listing are not consistently reliable. API-created drafts are the robust production path.

Deliverables:
- OAuth connect/disconnect
- Draft creation endpoint
- Listing publish endpoint
- Listing status sync endpoint

---

## 6) Backend Platform Readiness
1. Separate dev/staging/prod environments.
2. Add persistent database for users, drafts, tokens, jobs, audit logs.
3. Add job queue for image processing and external API tasks.
4. Add retries, idempotency keys, and dead-letter handling.
5. Add structured logs, metrics, and alerts.
6. Add rate limiting and abuse protection.

Deliverables:
- Deployment architecture diagram
- Queue worker setup
- Alerting and runbooks

---

## 7) Security and Compliance
1. Encrypt secrets and OAuth tokens at rest.
2. Use least-privilege API scopes.
3. Add tenant isolation checks on every data access path.
4. Add privacy policy, terms, and data deletion workflow.
5. Add key rotation and incident response process.

Deliverables:
- Security checklist
- Privacy/ToS pages
- Data retention/deletion policy

---

## 8) Billing and Monetization
1. Integrate subscriptions (monthly/yearly) with entitlement checks.
2. Enforce usage quotas per plan.
3. Add trial conversion flow and upgrade prompts.
4. Monitor gross margin (AI/API costs vs revenue).

Deliverables:
- Billing integration
- Entitlement middleware
- Plan quota controls

---

## 9) QA and Release Operations
1. Add automated unit/integration tests for pipeline and pricing.
2. Add end-to-end tests for auth, draft creation, and publish.
3. Build test matrix across Android versions/devices.
4. Use staged rollout (internal -> beta -> production).
5. Prepare rollback and incident communication playbooks.

Deliverables:
- Test plan
- Release checklist
- Incident runbook

---

## 10) Go-To-Market and Support
1. Launch with clear onboarding and first-win experience.
2. Add in-app support and response SLA for paying users.
3. Build content channel (demo clips, reseller workflows, case studies).
4. Add referral/affiliate loop.

Deliverables:
- Onboarding script
- Support SOP
- Launch campaign checklist

---

## 90-Day Suggested Execution Plan
### Days 1-30
1. Auth + cloud draft sync
2. Crash/analytics instrumentation
3. OCR confidence UX polish
4. Security cleanup (no hardcoded secrets)

### Days 31-60
1. eBay OAuth production flow
2. API-driven draft creation
3. Item specifics/category mapping
4. Listing photo upload pipeline

### Days 61-90
1. Closed beta with real sellers
2. Pricing/quality tuning from real traffic
3. Billing launch
4. Public rollout with staged release

---

## Weekly Metrics To Track
1. Activation rate (new user to first saved draft)
2. Time from photo to publish-ready draft
3. Draft-to-publish conversion
4. Weekly retained sellers
5. Paid conversion and churn
6. Cost per analyzed item
7. Gross margin per paid user

---

## Immediate Next Build Backlog (Practical)
1. Remove hardcoded backend token/defaults from app state.
2. Add backend user accounts and JWT auth.
3. Replace local-only draft storage with backend sync API.
4. Implement eBay OAuth connect endpoint and token vault.
5. Add API-driven "Create eBay Draft" endpoint.
6. Update Android button to call backend draft endpoint first, then open draft URL.
7. Add full error surfaces in UI (token expired, category missing, specifics required).

This document is the baseline. Keep it updated as decisions and integrations evolve.
