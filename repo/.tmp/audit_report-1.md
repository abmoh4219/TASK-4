# RegistrarOps Delivery Acceptance & Project Architecture Audit (Static-Only)

Date: 2026-04-14  
Scope root: `.` (`/home/abdelah/Documents/eaglepoint/TASK-4-W2/repo`) 
Method: Static analysis only (no project start, no Docker run, no tests executed)

## 1. Verdict

- **Overall conclusion: Partial Pass**

The repository is substantial and implements most core domains from the prompt (security, orders, search, grading, evaluations, messaging, APIs, migrations, tests), but there are **material security and prompt-fit gaps** that prevent a full pass.

## 2. Scope and Static Verification Boundary

### What was reviewed

- Docs/config/manifests: `README.md`, `pom.xml`, `docker-compose.yml`, `run_tests.sh`, `application*.yml`
- Security/authn/authz: `SecurityConfig.java`, `CustomUserDetailsService.java`, `AuthService.java`, `AuthEventHandlers.java`
- Core modules: order lifecycle, search/catalog, messaging, grading, evaluations, import/export, account deletion
- API surfaces: `src/main/java/com/registrarops/controller/api/v1/*.java`
- Schema/migrations: `src/main/resources/db/migration/*.sql`
- UI/JS: catalog/search/order/messages templates + `static/js/*.js`
- Tests (static review only): unit + API/integration test files under `src/test/java/com/registrarops/**`

### What was not reviewed

- Runtime behavior under real browser/network/db load
- Real container orchestration behavior
- Performance under scale

### What was intentionally not executed

- Application startup
- Docker / Docker Compose
- Unit/API tests
- External services/integrations

### Claims requiring manual verification

- True offline UX behavior under disconnected browser/device conditions
- Scheduled task timing in deployed runtime
- Visual/accessibility behavior across target devices and browsers

## 3. Repository / Requirement Mapping Summary

### Prompt core goals and constraints (extracted)

- Offline-first academic portal with Students/Faculty/Reviewers/Admin
- Unified search (suggestions, trending, typo correction, pinyin/synonym), filters/sorts, fallback recommendations
- Order state machine with strict transitions, 30-minute timeout auto-cancel, refund-window logic, correlation IDs
- In-app notification center only (no external channels), mute + quiet hours + dedup/threading
- Local auth hardening (complex passwords, lockout, optional device binding, unusual-login notices)
- Grade/evaluation data domain, evidence validation (file type + <=10MB), grade rules/versioning/recalculation
- REST-style APIs (students/courses/grades/reports) with authz + server-side validation
- Offline import/export + field mapping + validation reports + retry queue with capped exponential backoff

### Main implementation areas mapped

- Security/auth: `SecurityConfig.java`, `AuthService.java`, `PasswordComplexityValidator.java`
- Search/catalog UI+service: `CatalogController.java`, `SearchApiController.java`, `SearchService.java`, `CatalogService.java`
- Orders: `OrderService.java`, `OrderController.java`, `V006__create_orders.sql`
- Messaging: `MessageService.java`, `MessageController.java`, `V010__create_messages.sql`
- Grade/evaluation: `GradeEngineService.java`, `EvaluationService.java`, `V007/V008/V009`
- Integration APIs: `StudentApiV1.java`, `CourseApiV1.java`, `GradeApiV1.java`, `ReportApiV1.java`
- Import/export/retry: `ImportExportService.java`, `AdminController.java`, `V011__create_retry_jobs.sql`

## 4. Section-by-section Review

## 4.1 Hard Gates

### 4.1.1 Documentation and static verifiability

- **Conclusion:** Pass
- **Rationale:** Clear startup/testing instructions and coherent project structure are provided.
- **Evidence:** `README.md:43`, `README.md:68`, `README.md:75`, `README.md:26`, `pom.xml:1`
- **Manual verification note:** Instructions are present; runtime correctness still requires manual execution.

### 4.1.2 Material deviation from prompt

- **Conclusion:** Partial Pass
- **Rationale:** Core domains align strongly, but integration-level import/export is implemented as admin web endpoints rather than standardized external API endpoints, and grade API scope controls are incomplete for least-privilege expectations.
- **Evidence:** `AdminController.java:124`, `AdminController.java:130`, `AdminController.java:152`, `CourseApiV1.java:47`, `CourseApiV1.java:50`, `GradeApiV1.java:58`
- **Manual verification note:** Whether admin-only endpoints are acceptable for “system integration” needs product-owner confirmation.

## 4.2 Delivery Completeness

### 4.2.1 Core explicit requirements coverage

- **Conclusion:** Partial Pass
- **Rationale:** Most explicit requirements are implemented (search intelligence, state machine, notification preferences, grading/evaluation, lockout/password/device-binding, role-based APIs). Gaps remain in API object-scope protection and integration interface shape.
- **Evidence:** `SearchService.java:61`, `SearchService.java:36`, `SearchService.java:43`, `SearchService.java:150`, `OrderService.java:264`, `OrderService.java:254`, `AuthService.java:39`, `AuthService.java:40`, `AuthService.java:96`, `MessageService.java:66`, `MessageService.java:74`, `GradeEngineService.java:73`, `EvaluationService.java:189`, `EvaluationService.java:193`
- **Manual verification note:** Offline-first runtime behavior cannot be proven statically.

### 4.2.2 End-to-end 0→1 deliverable vs partial fragment

- **Conclusion:** Pass
- **Rationale:** This is a complete multi-module Spring Boot application with migrations, templates, services, and tests.
- **Evidence:** `README.md:14`, `src/main/resources/db/migration/V001__create_users.sql:1`, `src/test/java/com/registrarops/api/AbstractIntegrationTest.java:34`, `src/main/java/com/registrarops/RegistrarOpsApplication.java:8`

## 4.3 Engineering and Architecture Quality

### 4.3.1 Structure and module decomposition

- **Conclusion:** Pass
- **Rationale:** Responsibilities are separated across controllers/services/repositories/entities/config with clean layering.
- **Evidence:** `src/main/java/com/registrarops/config/SecurityConfig.java:1`, `src/main/java/com/registrarops/service/OrderService.java:1`, `src/main/java/com/registrarops/controller/api/v1/GradeApiV1.java:1`, `src/main/resources/db/migration/V006__create_orders.sql:1`

### 4.3.2 Maintainability and extensibility

- **Conclusion:** Partial Pass
- **Rationale:** Generally maintainable with policies externalized in config and test coverage. However, critical authorization logic is inconsistent across APIs (some endpoints use policy service, others bypass it).
- **Evidence:** `application.yml:40`, `application.yml:41`, `application.yml:42`, `GradeAccessPolicy.java:31`, `GradeAccessPolicy.java:48`, `CourseApiV1.java:50`, `GradeApiV1.java:58`

## 4.4 Engineering Details and Professionalism

### 4.4.1 Error handling, logging, validation, API design

- **Conclusion:** Partial Pass
- **Rationale:** Positive: standardized API error envelope and validation annotations exist; audit masking and structured logging exist. Gap: validation depth is uneven and not all sensitive API reads enforce object scope.
- **Evidence:** `ApiV1ExceptionHandler.java:21`, `StudentApiV1.java:53`, `StudentApiV1.java:55`, `GradeApiV1.java:52`, `GradeApiV1.java:54`, `AuditService.java:79`, `AuditService.java:30`, `CourseApiV1.java:50`

### 4.4.2 Product/service maturity vs demo

- **Conclusion:** Pass
- **Rationale:** The delivery resembles a real product with coherent UX, stateful business logic, persistence, and broad tests.
- **Evidence:** `catalog/index.html:7`, `orders/detail.html:19`, `messages/index.html:1`, `OrderStateMachineTest.java:26`, `ImportExportApiTest.java:21`

## 4.5 Prompt Understanding and Requirement Fit

### 4.5.1 Business objective and implicit constraints fit

- **Conclusion:** Partial Pass
- **Rationale:** The implementation demonstrates clear understanding of prompt semantics, especially offline in-app messaging and education-specific workflows. Main misfit: security least-privilege on grade data and integration API boundary for import/export.
- **Evidence:** `MessageService.java:25`, `MessageService.java:52`, `MessageService.java:66`, `MessageService.java:74`, `CourseApiV1.java:47`, `CourseApiV1.java:50`, `AdminController.java:130`, `AdminController.java:152`

## 4.6 Aesthetics (frontend/full-stack)

### 4.6.1 Visual/interaction quality

- **Conclusion:** Partial Pass
- **Rationale:** Static UI evidence shows consistent structure, filtering UX, status/timeline, suggestion dropdown, and interaction feedback. True rendering/accessibility quality requires manual verification.
- **Evidence:** `catalog/index.html:7`, `catalog/index.html:79`, `catalog/index.html:122`, `fragments/search-suggestions.html:6`, `fragments/search-suggestions.html:27`, `orders/detail.html:19`, `app.js:23`
- **Manual verification note:** Visual polish/accessibility across browsers/devices cannot be confirmed statically.

## 5. Issues / Suggestions (Severity-Rated)

### 1) High — Grade/course APIs expose broad data to faculty without consistent object-level scoping

- **Conclusion:** Fail
- **Evidence:** `CourseApiV1.java:47`, `CourseApiV1.java:48`, `CourseApiV1.java:50`, `GradeApiV1.java:58`
- **Impact:** A faculty principal can access full course grade sets (and list all grades in `GradeApiV1`) without assignment-bound checks, risking unauthorized disclosure.
- **Minimum actionable fix:** Route all grade/courses grade reads through `GradeAccessPolicy` (or equivalent assignment-based guard), including `CourseApiV1#grades` and `GradeApiV1#list`.

### 2) High — Integration import/export capability is not exposed as standardized external REST API

- **Conclusion:** Partial Fail
- **Evidence:** `AdminController.java:124`, `AdminController.java:130`, `AdminController.java:152`, `StudentApiV1.java:34`, `CourseApiV1.java:20`, `GradeApiV1.java:30`, `ReportApiV1.java:24`
- **Impact:** System-to-system offline integration may be blocked or require UI/session automation, deviating from prompt-level integration intent.
- **Minimum actionable fix:** Add authenticated `/api/v1/import` and `/api/v1/export` endpoints (with role checks, field mapping payloads, structured validation error reports, and retry-job visibility).

### 3) Medium — Retry queue infrastructure exists, but production job wiring is implicit and can fail without handlers

- **Conclusion:** Partial Fail
- **Evidence:** `ImportExportService.java:181`, `ImportExportService.java:245`, `ImportExportService.java:254`
- **Impact:** Queue semantics depend on runtime handler registration; missing handler registration leads to retry failures and operational fragility.
- **Minimum actionable fix:** Register default handlers at application startup (documented and tested) for all job types produced in production flows.

### 4) Medium — Admin “config” area is mostly placeholder rather than policy/integration management surface

- **Conclusion:** Partial Fail
- **Evidence:** `AdminController.java:173`, `AdminController.java:175`
- **Impact:** Weak support for administrator policy/integration management expectations in prompt.
- **Minimum actionable fix:** Implement persisted policy management (order/refund/retry/notification settings) with audited change history.

### 5) Medium — API validation is present but not comprehensive against tampering for all input shapes

- **Conclusion:** Partial Fail
- **Evidence:** `StudentApiV1.java:53`, `StudentApiV1.java:55`, `GradeApiV1.java:52`, `GradeApiV1.java:54`, `CourseApiV1.java:47`
- **Impact:** Some routes rely mostly on type coercion and role checks; deeper constraints and DTO validation coverage are inconsistent.
- **Minimum actionable fix:** Move query/body/path inputs to validated DTOs and add consistent validation + 400 response tests across all API routes.

## 6. Security Review Summary

### authentication entry points

- **Conclusion:** Pass
- **Evidence & rationale:** Local form auth with DB users, BCrypt hashing, lockout policy, and custom handlers are implemented.  
  Evidence: `SecurityConfig.java:37`, `SecurityConfig.java:76`, `CustomUserDetailsService.java:34`, `AuthService.java:39`, `AuthService.java:40`, `AuthApiTest.java:53`

### route-level authorization

- **Conclusion:** Pass
- **Evidence & rationale:** URL role rules and method-level `@PreAuthorize` are broadly applied.  
  Evidence: `SecurityConfig.java:68`, `SecurityConfig.java:71`, `SecurityConfig.java:72`, `SecurityConfig.java:74`, `GradeApiV1.java:50`, `ReportApiV1.java:33`

### object-level authorization

- **Conclusion:** Partial Pass
- **Evidence & rationale:** Strong checks exist in orders and grade access policy, but not uniformly used by all grade-related API endpoints.  
  Evidence: `OrderService.java:273`, `GradeAccessPolicy.java:31`, `GradeAccessPolicy.java:48`, `CourseApiV1.java:50`, `GradeApiV1.java:58`

### function-level authorization

- **Conclusion:** Pass
- **Evidence & rationale:** Sensitive operations (recalculate/review/admin) are function-guarded.  
  Evidence: `GradeApiV1.java:89`, `EvaluationController.java:127`, `AdminController.java:39`

### tenant / user data isolation

- **Conclusion:** Cannot Confirm Statistically
- **Evidence & rationale:** User/object isolation exists within a single-school model, but no tenant model is present to assess multi-tenant isolation.  
  Evidence: `V001__create_users.sql:1`, `OrderRepository.java:16`, `StudentApiV1.java:74`

### admin / internal / debug protection

- **Conclusion:** Pass
- **Evidence & rationale:** Admin routes are explicitly role-restricted and tested for denial to non-admin users.  
  Evidence: `SecurityConfig.java:68`, `SecurityHardeningTest.java:42`, `SecurityHardeningTest.java:53`

## 7. Tests and Logging Review

### Unit tests

- **Conclusion:** Pass
- **Rationale:** Core deterministic logic is covered (state machine, refund boundaries, password policy, search correction/pinyin/synonym, message threading/quiet hours, grading engine).
- **Evidence:** `OrderStateMachineTest.java:26`, `OrderStateMachineTest.java:103`, `RefundRuleTest.java:55`, `PasswordValidatorTest.java:50`, `SearchServiceTest.java:69`, `SearchServiceTest.java:116`, `MessageServiceTest.java:69`, `GradeEngineServiceTest.java:73`

### API / integration tests

- **Conclusion:** Partial Pass
- **Rationale:** Broad API/UI integration tests exist, including auth hardening and import/retry scenarios. Gaps remain around Course API object-scope and full negative matrices for all external APIs.
- **Evidence:** `AuthApiTest.java:19`, `OrderApiTest.java:109`, `CatalogApiTest.java:90`, `EvaluationApiTest.java:42`, `ImportExportApiTest.java:113`, `SecurityHardeningTest.java:114`

### Logging categories / observability

- **Conclusion:** Pass
- **Rationale:** Structured logging is present across security/order/retry plus append-only audit logging.
- **Evidence:** `application.yml:44`, `application.yml:48`, `AuthService.java:68`, `OrderService.java:138`, `ImportExportService.java:190`, `AuditLogRepository.java:24`

### Sensitive-data leakage risk in logs / responses

- **Conclusion:** Partial Pass
- **Rationale:** Sensitive field masking is implemented for audit payloads, reducing leakage risk there; comprehensive runtime log-redaction across all log paths still needs manual verification.
- **Evidence:** `AuditService.java:30`, `AuditService.java:34`, `AuditService.java:79`, `AuditApiTest.java:52`

## 8. Test Coverage Assessment (Static Audit)

### 8.1 Test Overview

- **Unit tests exist:** Yes (`src/test/java/com/registrarops/unit/*.java`)
- **API/integration tests exist:** Yes (`src/test/java/com/registrarops/api/*.java`)
- **Frameworks:** JUnit 5 + Spring Boot Test + MockMvc + Spring Security Test + Testcontainers
- **Test entry points/documented commands:** `run_tests.sh`, README test section
- **Evidence:** `pom.xml:106`, `pom.xml:111`, `pom.xml:116`, `AbstractIntegrationTest.java:34`, `README.md:68`, `README.md:75`, `run_tests.sh:37`

### 8.2 Coverage Mapping Table

| Requirement / Risk Point                                         | Mapped Test Case(s)                                                                                                                         | Key Assertion / Fixture / Mock                                               | Coverage Assessment | Gap                                                                                                              | Minimum Test Addition                                                              |
| ---------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- | ------------------- | ---------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| Auth success + lockout + CSRF on auth/logout                     | `AuthApiTest.java:38`, `AuthApiTest.java:53`, `AuthApiTest.java:69`                                                                         | lockout after repeated failures; POST without CSRF forbidden                 | basically covered   | Missing full 401/403 matrix for `/api/v1/**` unauthenticated behavior                                            | Add API-level unauthenticated tests for all v1 routes                              |
| Password complexity (12+ and character classes)                  | `PasswordValidatorTest.java:50`                                                                                                             | explicit validator rule checks                                               | sufficient          | N/A                                                                                                              | Optional: add unicode edge-case tests                                              |
| Order state machine + idempotency + timeout + refund boundary    | `OrderStateMachineTest.java:103`, `OrderStateMachineTest.java:126`, `OrderApiTest.java:85`, `RefundRuleTest.java:55`                        | transition exceptions, timeout cancellation, 14-day boundary                 | sufficient          | No concurrency/race test for repeated payment/cancel calls                                                       | Add concurrent mutation test on same order                                         |
| Search suggestions + typo correction + pinyin/synonym + fallback | `SearchServiceTest.java:69`, `SearchServiceTest.java:108`, `SearchServiceTest.java:116`, `CatalogApiTest.java:81`, `CatalogApiTest.java:90` | did-you-mean, normalization, no-result fallback, HTMX endpoint               | sufficient          | No large-dataset ranking stability tests                                                                         | Add deterministic sort/ranking tests with larger fixtures                          |
| Messaging quiet hours/mute/dedup                                 | `MessageServiceTest.java:43`, `MessageServiceTest.java:54`, `MessageServiceTest.java:69`                                                    | quiet-hour wrap, muted drop, duplicate thread increment                      | basically covered   | End-to-end controller/UI preference flow lightly tested                                                          | Add integration tests for `/messages/preferences/*` happy paths                    |
| Evaluation evidence validation and outlier review                | `EvaluationApiTest.java:42`, `EvaluationApiTest.java:55`, `EvaluationApiTest.java:66`                                                       | reject >10MB/invalid MIME; outlier detection                                 | sufficient          | No tests for file path/sanitization edge cases                                                                   | Add upload filename/path traversal hardening tests                                 |
| Import/export field mapping + retry queue backoff/failure cap    | `ImportExportApiTest.java:28`, `ImportExportApiTest.java:113`                                                                               | mapped import success; retries end as FAILED after max attempts              | basically covered   | No startup wiring test proving production handlers are registered                                                | Add context test asserting handlers for all active job types                       |
| Grade API authorization and object scope                         | `SecurityHardeningTest.java:114`, `SecurityHardeningTest.java:122`, `SecurityHardeningTest.java:130`                                        | student denied for other student; faculty denied when no recorded components | insufficient        | Missing direct tests for `/api/v1/courses/{id}/grades` object scope and `/api/v1/grades` data-scope restrictions | Add explicit negative tests proving faculty cannot read unrelated course/list data |

### 8.3 Security Coverage Audit

- **authentication:** **basically covered** (`AuthApiTest.java:38`, `AuthApiTest.java:53`)
- **route authorization:** **basically covered** (`SecurityHardeningTest.java:42`, `SecurityHardeningTest.java:130`)
- **object-level authorization:** **insufficient** (some checks tested, but key endpoint gap remains in `CourseApiV1`)  
  Evidence: `CourseApiV1.java:50`, missing dedicated negative test coverage for this endpoint.
- **tenant/data isolation:** **cannot confirm** (single-tenant model only; no tenant tests/entities)
- **admin/internal protection:** **covered** (`SecurityHardeningTest.java:42`, `AuditApiTest.java:74`)

### 8.4 Final Coverage Judgment

- **Final Coverage Judgment: Partial Pass**

Covered well: auth lockout basics, order state/refund rules, search intelligence, evaluation evidence limits, retry mechanics basics.  
Not covered enough: high-risk object-level authorization on all grade/course-grade API reads and full external API negative matrices, meaning severe data-scope defects could still evade current tests.

## 9. Final Notes

- This report is strictly static and evidence-based.
- No runtime success claims are made.
- The most material acceptance blockers are concentrated in **authorization scope completeness** and **integration interface fit** rather than missing core domain code.
