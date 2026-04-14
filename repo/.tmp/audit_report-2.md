# RegistrarOps Delivery Acceptance & Project Architecture Audit (Static-Only)

Date: 2026-04-14  
Scope root: `/home/abdelah/Documents/eaglepoint/TASK-4-W2/repo`

## 1) Verdict

- **Overall conclusion: Partial Pass**

The repository is substantial and maps to most Prompt flows (auth, catalog/search, grades, evaluations, orders, in-app messaging, import/export APIs). However, there are material gaps that prevent a full pass:

- account-deletion export access is not robust for the promised 7-day window,
- admin policy settings are largely not connected to runtime behavior,
- failed import rows are explicitly not scheduled into the retry queue despite Prompt emphasis on failed-job retry.

---

## 2) Scope and Static Verification Boundary

### What was reviewed

- Documentation, manifests, and configuration: `README.md`, `pom.xml`, `application*.yml`, `run_tests.sh`
- Security/auth: `SecurityConfig`, `AuthService`, `CustomUserDetailsService`, `AuthController`
- Business modules: orders, search/catalog, grades/grade rules, evaluations/evidence, messaging, audit, import/export, policy settings
- DB schema and seed migrations under `src/main/resources/db/migration/*.sql`
- Test suites under `src/test/java/com/registrarops/{api,unit}`
- Thymeleaf templates and static UI assets for static UX/aesthetics review

### What was not reviewed

- Runtime behavior under real browser/network/DB/container timing conditions
- Docker orchestration behavior beyond static script/config consistency
- Performance/scalability characteristics under load

### Intentionally not executed

- Project startup, Maven builds, tests, Docker, or external services (per instruction)

### Claims requiring manual verification

- Real-time behaviors (scheduled jobs, countdown + auto-cancel timing, quiet-hour deferrals) in live runtime
- End-to-end API usability for external machine integrations under real auth/session flows
- Visual rendering consistency across browsers/devices

---

## 3) Repository / Requirement Mapping Summary

### Prompt core business goal / flows (condensed)

- Offline-first registrar portal with role-based workflows for Students, Faculty, Reviewers, Admin.
- Unified catalog search with suggestions/trending/did-you-mean (incl. pinyin/synonyms), filtering/sorting, and fallback recommendations.
- Stateful order lifecycle with 30-minute auto-cancel, refunds, timeline, and in-app notifications/preferences.
- Local auth hardening (complex passwords, lockout, optional device binding, unusual-login notices), soft-delete + export.
- Grade rules engine with weighted categories, GPA, retake strategy, and rule version recalculation.
- REST APIs + offline import/export + validation + retry queue.

### Main implementation areas mapped

- Security/auth: `src/main/java/com/registrarops/config/SecurityConfig.java:64`, `.../service/AuthService.java:39`, `.../security/CustomUserDetailsService.java:45`
- Catalog/search: `.../service/SearchService.java:36`, `.../controller/CatalogController.java:32`, `.../templates/catalog/index.html:18`
- Orders/messaging: `.../service/OrderService.java:98`, `.../service/MessageService.java:74`, `.../templates/orders/detail.html:19`
- Grades/evaluations: `.../service/GradeEngineService.java:73`, `.../service/EvaluationService.java:47`
- APIs/import-export/policy: `.../controller/api/v1/*.java`, `.../service/ImportExportService.java:190`, `.../service/PolicySettingService.java:31`

---

## 4) Section-by-section Review

## 4.1 Hard Gates

### 4.1.1 Documentation and static verifiability

- **Conclusion: Pass**
- **Rationale:** README provides run/test instructions and repository structure; manifests/configs are present and statically coherent.
- **Evidence:** `README.md:23`, `README.md:43`, `README.md:69`, `README.md:75`, `pom.xml:43`, `run_tests.sh:37`
- **Manual verification note:** Runtime correctness of those commands is out of static scope.

### 4.1.2 Material deviation from Prompt

- **Conclusion: Partial Pass**
- **Rationale:** Most core domains are implemented, but there are notable requirement-fit gaps (policy settings not enforced at runtime; failed-import retry not end-to-end; deletion export access risk).
- **Evidence:** `src/main/java/com/registrarops/service/PolicySettingService.java:60`, `src/main/java/com/registrarops/service/OrderService.java:84`, `src/main/java/com/registrarops/service/ImportExportService.java:183`, `src/main/java/com/registrarops/controller/AuthController.java:88`

## 4.2 Delivery Completeness

### 4.2.1 Coverage of explicit core requirements

- **Conclusion: Partial Pass**
- **Rationale:** Broad coverage exists (search, grades, orders, messaging, evaluation, APIs), but specific prompt commitments are incomplete in implementation wiring.
- **Evidence:** `src/main/java/com/registrarops/service/SearchService.java:36`, `src/main/java/com/registrarops/service/OrderService.java:98`, `src/main/java/com/registrarops/service/MessageService.java:74`, `src/main/java/com/registrarops/service/ImportExportService.java:183`

### 4.2.2 End-to-end 0→1 deliverable vs partial demo

- **Conclusion: Pass**
- **Rationale:** Full Spring Boot multi-module structure, DB migrations, templates, APIs, and tests indicate product-like deliverable rather than isolated snippets.
- **Evidence:** `README.md:20`, `src/main/resources/db/migration/V001__create_users.sql:1`, `src/main/resources/templates/layout/base.html:1`, `src/test/java/com/registrarops/api/AbstractIntegrationTest.java:33`

## 4.3 Engineering and Architecture Quality

### 4.3.1 Structure and module decomposition

- **Conclusion: Pass**
- **Rationale:** Clear layering (controller/service/repository/entity), separate API namespaces, and dedicated modules for major business concerns.
- **Evidence:** `src/main/java/com/registrarops/controller/api/v1/GradeApiV1.java:1`, `src/main/java/com/registrarops/service/OrderService.java:1`, `src/main/java/com/registrarops/repository/OrderRepository.java:1`

### 4.3.2 Maintainability and extensibility

- **Conclusion: Partial Pass**
- **Rationale:** Core services are testable and modular, but policy management currently forms a largely disconnected control plane (saved/audited yet not consumed by runtime logic).
- **Evidence:** `src/main/java/com/registrarops/service/PolicySettingService.java:60`, `src/main/java/com/registrarops/controller/api/v1/PolicyApiV1.java:68`, `src/main/java/com/registrarops/service/OrderService.java:84`, `src/main/java/com/registrarops/service/ImportExportService.java:195`

## 4.4 Engineering Details and Professionalism

### 4.4.1 Error handling, logging, validation, API design

- **Conclusion: Partial Pass**
- **Rationale:** Strong validation/error envelope and security tests exist; however, some core behavior promises are not wired end-to-end (retry linkage, effective policy enforcement).
- **Evidence:** `src/main/java/com/registrarops/controller/api/v1/ApiV1ExceptionHandler.java:21`, `src/main/java/com/registrarops/controller/api/v1/dto/PageQueryDto.java:18`, `src/main/java/com/registrarops/service/AuditService.java:69`, `src/main/java/com/registrarops/service/ImportExportService.java:183`

### 4.4.2 Product/service realism vs demo shape

- **Conclusion: Pass**
- **Rationale:** Includes auth, role separation, persistence, migration history, retry table, audit logs, and broad integration/unit tests.
- **Evidence:** `src/main/resources/db/migration/V012__create_audit.sql:1`, `src/main/resources/db/migration/V011__create_retry_jobs.sql:1`, `src/test/java/com/registrarops/api/SecurityHardeningTest.java:33`

## 4.5 Prompt Understanding and Requirement Fit

### 4.5.1 Business objective and constraints fit

- **Conclusion: Partial Pass**
- **Rationale:** Implementation reflects most scenario semantics, but misses are material for operations/governance reliability: policy edits not authoritative at runtime, retry queue not bound to import failures, and deletion export accessibility risk.
- **Evidence:** `src/main/java/com/registrarops/service/PolicySettingService.java:31`, `src/main/java/com/registrarops/service/ImportExportService.java:183`, `src/main/java/com/registrarops/security/CustomUserDetailsService.java:45`, `src/main/java/com/registrarops/controller/AuthController.java:88`

## 4.6 Aesthetics (frontend/full-stack)

### 4.6.1 Visual/interaction quality fit

- **Conclusion: Pass (Static)**
- **Rationale:** Consistent design tokens/theme, role-based dashboard cards, form hierarchy, badges, hover states, search dropdown, and timeline/countdown UI elements are statically present.
- **Evidence:** `src/main/resources/static/css/app.css:1`, `src/main/resources/templates/catalog/index.html:18`, `src/main/resources/templates/orders/detail.html:19`, `src/main/resources/static/js/app.js:23`
- **Manual verification note:** Cross-browser rendering/accessibility remains manual verification.

---

## 5) Issues / Suggestions (Severity-Rated)

### 5.1 Blocker / High

1. **Severity: High**  
   **Title:** Account export download path is coupled to authenticated principal after soft-delete  
   **Conclusion:** Fail  
   **Evidence:** `src/main/java/com/registrarops/service/AccountDeletionService.java:112`, `src/main/java/com/registrarops/service/AccountDeletionService.java:114`, `src/main/java/com/registrarops/security/CustomUserDetailsService.java:45`, `src/main/java/com/registrarops/controller/AuthController.java:88`  
   **Impact:** User is soft-deleted immediately (`deletedAt`, inactive), login is blocked, and export endpoint requires authenticated principal; export may become inaccessible once the active session ends, undermining the 7-day downloadable export expectation.  
   **Minimum actionable fix:** Provide a signed, expiry-bound export token endpoint independent of account-auth (or maintain explicit export-access session/window) while preserving strict token ownership checks.

2. **Severity: High**  
   **Title:** Admin policy settings are persisted/audited but not authoritative for runtime behavior  
   **Conclusion:** Fail  
   **Evidence:** `src/main/java/com/registrarops/service/PolicySettingService.java:60`, `src/main/java/com/registrarops/controller/api/v1/PolicyApiV1.java:68`, `src/main/java/com/registrarops/service/OrderService.java:84`, `src/main/java/com/registrarops/service/ImportExportService.java:195`, `src/main/resources/db/migration/V015__policy_settings.sql:14`  
   **Impact:** Admin changes can appear successful but not actually change operational behavior (refund window, retry attempts, quiet-hour policy), creating governance drift and auditability risk.  
   **Minimum actionable fix:** Wire runtime reads to `PolicySettingService` (with validated cache/refresh), unify key naming conventions, and remove or clearly label non-effective policy keys.

3. **Severity: High**  
   **Title:** Failed import rows are explicitly not queued for retry despite retry-queue requirement context  
   **Conclusion:** Partial Fail  
   **Evidence:** `src/main/java/com/registrarops/service/ImportExportService.java:183`, `src/main/java/com/registrarops/service/ImportExportService.java:190`, `src/main/java/com/registrarops/controller/api/v1/ImportExportApiV1.java:55`  
   **Impact:** Import failures return error lists but do not enter the local retry workflow; operational recovery depends on manual re-upload, weakening “failed-job retry queue” completeness for import/export flows.  
   **Minimum actionable fix:** Persist retryable import job artifacts/metadata and enqueue failed imports with bounded retries + backoff.

### 5.2 Medium

4. **Severity: Medium**  
   **Title:** External REST integration auth model cannot be confirmed as machine-to-machine friendly  
   **Conclusion:** Cannot Confirm Statistically / Suspected Risk  
   **Evidence:** `src/main/java/com/registrarops/config/SecurityConfig.java:64`, `src/main/java/com/registrarops/config/SecurityConfig.java:75`, `src/main/java/com/registrarops/config/SecurityConfig.java:74`, `src/main/java/com/registrarops/controller/api/v1/ImportExportApiV1.java:55`  
   **Impact:** API consumers may need browser-style session + CSRF mechanics, which can hinder standardized offline integrations if non-browser clients are expected.  
   **Minimum actionable fix:** Add explicit API authentication strategy (e.g., service API key/token profile) and document CSRF/auth expectations per endpoint.

5. **Severity: Medium**  
   **Title:** Order notification set does not clearly implement reminder/exception messaging paths  
   **Conclusion:** Partial Fail  
   **Evidence:** `src/main/java/com/registrarops/service/OrderService.java:187`, `src/main/java/com/registrarops/service/OrderService.java:203`, `src/main/java/com/registrarops/service/OrderService.java:221`, `src/main/java/com/registrarops/service/OrderService.java:247`  
   **Impact:** Prompt asks for acceptance/status/reminder/exception/refund notices; current sends cover acceptance/status/refund/cancel but reminder/exception paths are not evident.
   **Minimum actionable fix:** Add explicit reminder scheduling (e.g., pre-timeout nudges) and exception-state message emission tied to order exception transitions.

### 5.3 Low

6. **Severity: Low**  
   **Title:** Comment-to-config mismatch around CSRF handling for `/api/v1/**`  
   **Conclusion:** Fail (documentation accuracy)  
   **Evidence:** `src/main/java/com/registrarops/controller/api/v1/StudentApiV1.java:27`, `src/main/java/com/registrarops/config/SecurityConfig.java:64`  
   **Impact:** Misleading inline docs can cause integration misunderstandings.  
   **Minimum actionable fix:** Correct comments or align CSRF behavior intentionally.

---

## 6) Security Review Summary

### authentication entry points

- **Conclusion: Pass**
- **Evidence:** Form login configured and custom handlers wired: `src/main/java/com/registrarops/config/SecurityConfig.java:75`; lockout + unusual login handling: `src/main/java/com/registrarops/service/AuthService.java:39`, `src/main/java/com/registrarops/service/AuthService.java:122`; password hashing via BCrypt: `src/main/java/com/registrarops/config/SecurityConfig.java:34`; complexity validator: `src/main/java/com/registrarops/security/PasswordComplexityValidator.java:16`.

### route-level authorization

- **Conclusion: Pass**
- **Evidence:** URL role guards in security config (`/admin/**`, `/orders/**`, etc.): `src/main/java/com/registrarops/config/SecurityConfig.java:68`, `:72`, `:74`; API method-level guards: `src/main/java/com/registrarops/controller/api/v1/GradeApiV1.java:51`, `src/main/java/com/registrarops/controller/api/v1/ImportExportApiV1.java:57`.

### object-level authorization

- **Conclusion: Partial Pass**
- **Evidence:** Grade object-level checks enforced by policy service: `src/main/java/com/registrarops/service/GradeAccessPolicy.java:31`, `:48`; order ownership enforced in service/controller: `src/main/java/com/registrarops/service/OrderService.java:289`, `src/main/java/com/registrarops/controller/OrderController.java:40`.
- **Reasoning:** Strong in reviewed domains; broader object-level checks for all entities/endpoints were not exhaustively provable statically.

### function-level authorization

- **Conclusion: Pass**
- **Evidence:** Sensitive actions guarded with `@PreAuthorize` (grade entry/recalc, evaluation approve, import/export admin): `src/main/java/com/registrarops/controller/GradeController.java:80`, `:121`; `src/main/java/com/registrarops/controller/EvaluationController.java:136`; `src/main/java/com/registrarops/controller/api/v1/ImportExportApiV1.java:57`.

### tenant / user data isolation

- **Conclusion: Partial Pass**
- **Evidence:** User-scoped retrievals (`findByStudentId...`, recipient-scoped messages): `src/main/java/com/registrarops/repository/OrderRepository.java:19`, `src/main/java/com/registrarops/repository/MessageRepository.java:21`; account export token bound to current user file: `src/main/java/com/registrarops/controller/AuthController.java:90`.
- **Reasoning:** Isolation appears intentional in key flows; no explicit multi-tenant model in prompt/repo.

### admin / internal / debug protection

- **Conclusion: Pass**
- **Evidence:** `/admin/**` restricted to admin role: `src/main/java/com/registrarops/config/SecurityConfig.java:68`; tests verify student/faculty denial: `src/test/java/com/registrarops/api/SecurityHardeningTest.java:43`, `:53`.

---

## 7) Tests and Logging Review

### Unit tests

- **Conclusion: Pass (static presence/quality)**
- **Evidence:** State machine/refund/search/messages/grade-engine/password tests exist: `src/test/java/com/registrarops/unit/OrderStateMachineTest.java:58`, `src/test/java/com/registrarops/unit/RefundRuleTest.java:26`, `src/test/java/com/registrarops/unit/SearchServiceTest.java:69`, `src/test/java/com/registrarops/unit/MessageServiceTest.java:69`, `src/test/java/com/registrarops/unit/PasswordValidatorTest.java:9`.

### API/integration tests

- **Conclusion: Partial Pass**
- **Evidence:** Auth/security/order/catalog/grade/eval/import-export/audit suites exist: `src/test/java/com/registrarops/api/AuthApiTest.java:38`, `src/test/java/com/registrarops/api/SecurityHardeningTest.java:33`, `src/test/java/com/registrarops/api/OrderApiTest.java:32`, `src/test/java/com/registrarops/api/ImportExportApiTest.java:28`.
- **Reasoning:** Good breadth, but not all prompt-risk scenarios are covered (e.g., reminder/exception notifications; deletion export usability over session expiry).

### Logging categories / observability

- **Conclusion: Pass**
- **Evidence:** Structured audit logging with action/entity fields: `src/main/java/com/registrarops/service/AuditService.java:44`; configured logger levels: `src/main/resources/application.yml:46`.

### Sensitive-data leakage risk in logs/responses

- **Conclusion: Partial Pass**
- **Evidence:** Audit masking for password/token/phone: `src/main/java/com/registrarops/service/AuditService.java:25`, `:80`; test coverage for masking: `src/test/java/com/registrarops/api/AuditApiTest.java:52`.
- **Reasoning:** Audit path is masked; full application-wide response/log leakage risk cannot be fully proven statically.

---

## 8) Test Coverage Assessment (Static Audit)

### 8.1 Test Overview

- Unit tests: **present** under `src/test/java/com/registrarops/unit`
- API/integration tests: **present** under `src/test/java/com/registrarops/api`
- Framework stack: JUnit + Spring Boot Test + Spring Security Test + Testcontainers MySQL
  - Evidence: `pom.xml:111`, `pom.xml:115`, `src/test/java/com/registrarops/api/AbstractIntegrationTest.java:33`
- Test entry points documented:
  - `README.md:69`, `README.md:75`, `run_tests.sh:37`

### 8.2 Coverage Mapping Table

| Requirement / Risk Point                        | Mapped Test Case(s)                                                                   | Key Assertion / Fixture / Mock                                                        | Coverage Assessment               | Gap                                                            | Minimum Test Addition                                                                    |
| ----------------------------------------------- | ------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------- | --------------------------------- | -------------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| Local auth success/failure + lockout            | `AuthApiTest.java:38`, `:45`, `:53`                                                   | successful auth, wrong password redirect, lockout after repeated failures             | sufficient                        | none major                                                     | add lockout expiry time-window test                                                      |
| Password complexity >=12 + composition          | `PasswordValidatorTest.java:9`, `:20`, `:44`                                          | min length, uppercase/lowercase/digit/special checks                                  | sufficient                        | none major                                                     | add unicode/special charset edge cases                                                   |
| CSRF enforcement on mutating endpoints          | `AuthApiTest.java:69`, `SecurityHardeningTest.java:35`                                | POST without CSRF returns 403                                                         | basically covered                 | API-specific matrix is limited                                 | add CSRF tests for `/api/v1/import/courses` and policy PUT variations                    |
| Route-level RBAC for admin/internal             | `SecurityHardeningTest.java:43`, `:53`, `:76`                                         | student/faculty forbidden for `/admin` and admin-only report API                      | sufficient                        | none major                                                     | add reviewer/admin cross-check for all `/admin/*` actions                                |
| Object-level grade access                       | `SecurityHardeningTest.java:114`, `:122`                                              | student blocked from others’ grades, faculty scope enforcement                        | sufficient                        | course/grade edge cases still possible                         | add tests for reviewer/admin unrestricted reads                                          |
| Order state machine + ownership + refunds       | `OrderApiTest.java:32`, `:85`, `:109`; `OrderStateMachineTest.java:58`                | transition rules, refund boundary, cross-user mutation denied                         | sufficient                        | no concurrency/retry race tests                                | add duplicate request race/idempotency concurrency test                                  |
| 30-minute timeout + countdown                   | `OrderStateMachineTest.java:119`; static UI refs `orders/detail.html:19`, `app.js:23` | auto-cancel logic unit tested; countdown present in UI JS                             | basically covered                 | real scheduler timing not runtime-verified                     | add integration test simulating expired PAYING row + endpoint timeline assertion         |
| Search did-you-mean + pinyin/synonym + fallback | `SearchServiceTest.java:69`, `:108`, `:116`, `:81`; `CatalogApiTest.java:90`          | Levenshtein correction, synonym and pinyin normalization, fallback/no-result behavior | sufficient                        | ranking quality not benchmarked                                | add deterministic ranking tests for ties/multi-match                                     |
| Evaluation evidence validation (type/size)      | `EvaluationApiTest.java:42`, `:55`                                                    | >10MB rejected, invalid MIME rejected                                                 | sufficient                        | file-name/path safety not explicitly tested                    | add path traversal filename test                                                         |
| Import/export + retry queue backoff             | `ImportExportApiTest.java:28`, `:92`, `:124`                                          | mapping import, fail→success retry, max-attempt failure                               | basically covered                 | import-failure-to-retry linkage missing by design              | add test asserting failed import rows enqueue retry job once artifact persistence exists |
| Policy update correctness                       | `SecurityHardeningTest.java:196`, `:214`                                              | policy API read/write + invalid value rejected                                        | insufficient (for runtime effect) | tests verify persistence only, not behavioral enforcement      | add tests that policy change alters order/refund/retry behavior                          |
| Account deletion export + 7-day window          | `SecurityHardeningTest.java:58`                                                       | only soft-delete field mutation tested                                                | insufficient                      | no test for post-delete export accessibility over time/session | add tests for token download after session refresh and near-retention boundary           |

### 8.3 Security Coverage Audit

- **Authentication:** basically covered (login success/failure, lockout, logout, CSRF baseline).  
  Evidence: `AuthApiTest.java:38`, `:53`, `:69`.
- **Route authorization:** covered for key paths (`/admin`, reports, grade list restrictions).  
  Evidence: `SecurityHardeningTest.java:43`, `:76`, `:102`.
- **Object-level authorization:** covered in grades/orders for principal mismatch scenarios.  
  Evidence: `SecurityHardeningTest.java:114`; `OrderApiTest.java:109`.
- **Tenant/data isolation:** partially covered (user-scoped behavior present), but no multi-tenant model tests.  
  Evidence: `OrderApiTest.java:100`; `MessageRepository.java:21`.
- **Admin/internal protection:** covered via role-forbidden tests.  
  Evidence: `SecurityHardeningTest.java:43`, `:53`.

### 8.4 Final Coverage Judgment

- **Partial Pass**

Major risks are reasonably covered for auth, RBAC, state-machine rules, and core validation. However, tests could still pass while severe defects remain in policy-to-runtime enforcement and deletion-export usability lifecycle; these are high-impact operational/business risks not meaningfully asserted today.

---

## 9) Final Notes

- This audit is strictly static; no runtime success is claimed.
- The codebase is substantial and mostly aligned to the Prompt’s business scope.
- The top acceptance risks are implementation-wiring risks (policy control-plane effectiveness, retry workflow completeness, and deletion-export lifecycle accessibility), not superficial code style concerns.
