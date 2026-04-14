# RegistrarOps — Design Document

Generated from the actual implementation under `repo/`.

## 1. Architecture overview

```
                 ┌──────────────────────────────────────────────┐
                 │                  Browser                     │
                 │  (HTML + Bootstrap 5 + HTMX 1.9 + vanilla JS)│
                 └─────────────┬────────────────┬───────────────┘
                               │ HTML / form    │ HTMX partials
                               ▼                ▼
            ┌──────────────────────────────────────────────────┐
            │          Spring Boot 3.2 monolith                │
            │  ┌──────────────┐  ┌──────────────────────────┐  │
            │  │ Controllers  │  │ HTMX/REST API controllers│  │
            │  │ (Thymeleaf)  │  │ /api/notifications, /api │  │
            │  └──────┬───────┘  │ /search, /api/v1/**      │  │
            │         │          └────────────┬─────────────┘  │
            │         ▼                       ▼                │
            │  ┌─────────────────────────────────────────────┐ │
            │  │           Service layer                     │ │
            │  │  Auth, Account, Audit, Catalog, Search,     │ │
            │  │  Order, GradeEngine, Evaluation,            │ │
            │  │  Message, ImportExport                      │ │
            │  └────────────────┬────────────────────────────┘ │
            │                   ▼                              │
            │  ┌──────────────────────────────────────────────┐│
            │  │        Spring Data JPA repositories          ││
            │  └────────────────┬─────────────────────────────┘│
            └───────────────────┼──────────────────────────────┘
                                ▼
                        ┌────────────────┐
                        │  MySQL 8.0     │
                        │ (Flyway-mgmt)  │
                        └────────────────┘
```

There is no separate frontend project. Every page is a Thymeleaf template
rendered server-side. HTMX (loaded from a CDN script tag in
`templates/layout/base.html`) handles real-time partial updates for the
unified search bar suggestions and the notification bell.

## 2. Project layout

```
repo/
├── pom.xml                    Spring Boot 3.2.5 parent
├── mvnw / mvnw.cmd            Maven wrapper (works without local Maven)
├── Dockerfile                 multi-stage build (maven → JRE alpine)
├── Dockerfile.test            Maven image for run_tests.sh inside Docker
├── docker-compose.yml         setup + mysql + app + test (profile)
├── run_tests.sh               unit + api split (passes inside test container)
├── .env.example               committed; copied to .env by setup service
└── src/
    ├── main/
    │   ├── java/com/registrarops/
    │   │   ├── RegistrarOpsApplication.java  (@EnableScheduling)
    │   │   ├── config/SecurityConfig.java
    │   │   ├── controller/{Auth,Dashboard,Catalog,Order,Grade,
    │   │   │              Evaluation,Message,Admin}Controller.java
    │   │   ├── controller/api/
    │   │   │   ├── NotificationApiController.java     HTMX bell
    │   │   │   └── SearchApiController.java           HTMX suggestions
    │   │   ├── controller/api/v1/
    │   │   │   ├── StudentApiV1.java     ADMIN/REVIEWER, paginated
    │   │   │   ├── CourseApiV1.java      all roles + grades for FACULTY/ADMIN
    │   │   │   └── ReportApiV1.java      ADMIN gpa-summary
    │   │   ├── entity/      22 JPA entities + 4 enums
    │   │   ├── repository/  19 Spring Data interfaces
    │   │   ├── security/
    │   │   │   ├── CustomUserDetailsService.java
    │   │   │   ├── PasswordComplexityValidator.java
    │   │   │   └── AuthEventHandlers.java
    │   │   └── service/
    │   │       ├── AuthService.java
    │   │       ├── AccountDeletionService.java
    │   │       ├── AuditService.java          APPEND-ONLY
    │   │       ├── CatalogService.java
    │   │       ├── SearchService.java
    │   │       ├── OrderService.java          state machine
    │   │       ├── OrderStateException.java
    │   │       ├── GradeEngineService.java
    │   │       ├── EvaluationService.java
    │   │       ├── MessageService.java
    │   │       └── ImportExportService.java
    │   └── resources/
    │       ├── application.yml + application-docker.yml + application-test.yml
    │       ├── db/migration/V001..V013_*.sql
    │       ├── static/css/app.css, static/js/{app,search}.js
    │       └── templates/{layout,fragments,auth,dashboard,catalog,
    │                      orders,grades,evaluations,messages,admin}/
    └── test/java/com/registrarops/
        ├── unit/   6 classes — pure JUnit 5 + Mockito (42 tests)
        └── api/    7 classes — Spring Boot Test + Testcontainers (40 tests)
```

## 3. Database schema (Flyway)

13 migration files under `src/main/resources/db/migration`:

| File | Tables |
|---|---|
| V001 | users |
| V002 | login_attempts, device_bindings, security_notices |
| V003 | courses, course_materials |
| V004 | enrollments |
| V005 | search_terms, catalog_ratings |
| V006 | orders, order_items |
| V007 | grade_rules, grade_rule_history |
| V008 | grade_components, student_grades |
| V009 | evaluation_cycles, evaluation_indicators, evidence_attachments |
| V010 | messages, message_preferences |
| V011 | retry_jobs |
| V012 | audit_logs (append-only) |
| V013 | seed: 4 users (bcrypt hashes), 5 courses, 5 grade rules, 6 search terms |

Hibernate `ddl-auto: none` — Flyway is the canonical schema source. JPA only
reads the schema as it exists; it never modifies tables.

### Key entities and relationships

```
User ─< Enrollment >─ Course
            │            │
            │            ├─< CourseMaterial
            │            │
            │            ├─< GradeRule (versioned, JSON weights)
            │            │       │
            │            │       └─< GradeRuleHistory
            │            │
            │            ├─< GradeComponent ─┐
            │            │                   │
            │            └─< StudentGrade ←──┘ rule_version_id
            │
            ├─< Order ─< OrderItem
            ├─< CatalogRating
            ├─< Message ─ MessagePreference
            ├─< LoginAttempt
            ├─< DeviceBinding
            ├─< SecurityNotice
            └─< (faculty) EvaluationCycle ─< EvaluationIndicator
                                          └─< EvidenceAttachment
AuditLog (append-only, no FK)
RetryJob (job queue)
SearchTerm (trending)
```

## 4. Security architecture

### `config/SecurityConfig.java`

- `BCryptPasswordEncoder(strength=12)` for all password hashing
- `DaoAuthenticationProvider` wired to `CustomUserDetailsService`
- `CookieCsrfTokenRepository.withHttpOnlyFalse()` so HTMX can read the token
  from the cookie / `<meta>` tag and forward it on every mutating request
- Form login at `/login` with custom success/failure handlers
- `sessionManagement.maximumSessions(1)` — one session per user
- `@EnableMethodSecurity(prePostEnabled=true)` enables `@PreAuthorize` on
  REST API methods
- URL rules:
  - `/admin/**` → ROLE_ADMIN
  - `/grades/**` → FACULTY, ADMIN, STUDENT
  - `/evaluations/**` → FACULTY, REVIEWER, ADMIN
  - `/catalog/**`, `/orders/**` → STUDENT, ADMIN
  - `/api/v1/**` CSRF excluded (REST style)
  - everything else `authenticated()`

### `security/CustomUserDetailsService.java`

Loads users from MySQL. Rejects soft-deleted accounts (`deletedAt IS NOT NULL`).
Sets `accountNonLocked` from `AuthService.isLockedOut(username)` so locked
accounts are blocked **before** the password is even compared — Spring throws
`LockedException` and the failure handler redirects to `/login?locked`.

### `security/AuthEventHandlers.java`

| Event | Action |
|---|---|
| `BadCredentialsException` | `authService.trackFailedLogin(user, ip)`, then re-check `isLockedOut` and redirect to `/login?locked` if hit |
| `LockedException` | redirect to `/login?locked` |
| any other failure | redirect to `/login?error` |
| Success | `clearAttempts(user)`, `detectUnusualLogin(user, request)` |

### `service/AuthService.java`

- **Lockout**: `MAX_FAILED_ATTEMPTS = 5` within `LOCKOUT_MINUTES = 15`
- **Device binding**: SHA-256(`ip + "|" + user-agent`) stored per user. The
  first login for a user adds the binding silently; subsequent logins from a
  hash not in `device_bindings` issue a `SecurityNotice` of type
  `UNUSUAL_LOGIN`.

### `security/PasswordComplexityValidator.java`

Static utility used by `AdminController` and the test suite. Rejects any
password that fails:
- `length >= 12`
- has uppercase, lowercase, digit, and one of `!@#$%^&*()_+-=[]{}|;:'",.<>/?` `~\`

### `service/AuditService.java` (APPEND-ONLY)

- The single mutator is `log(...)` — no update or delete is exposed.
- `AuditLogRepository` extends only `Repository<AuditLog,Long>` (NOT
  `JpaRepository`/`CrudRepository`), so update/delete/saveAll methods are
  not even inherited. A reflection-based test
  (`AuditApiTest#testAuditLogRepositoryHasNoUpdateOrDelete`) asserts:
  - `methods.filter(name.contains("delete")).count() == 0`
  - `methods.filter(name == "save").count() == 1`
- Sensitive field masking: `maskSensitive(json)` runs two regexes that
  replace `password`/`token`/`secret`/`api_key`/`phone` JSON values with
  `[MASKED]` before persistence.

### `service/AccountDeletionService.java`

`exportAndSoftDelete(userId)`:
1. Collect all of the user's orders + grades into a JSON file under
   `${registrarops.export-dir:/tmp/exports}/user_{id}_{token}.json`.
2. Stamp `deletedAt = NOW()`, `isActive = false`, store the file path.
3. Audit-log the deletion.

`@Scheduled(cron = "0 30 3 * * *") purgeExpiredSoftDeletes()`: daily at 03:30
locally, anonymizes any user whose `deletedAt` is older than 7 days.

## 5. Order state machine (`service/OrderService.java`)

```
                       ┌──────────┐
                       │  CREATED │
                       └────┬─────┘
                            │
              ┌─────────────┼──────────────┐
              ▼             ▼              ▼
         ┌────────┐    ┌─────────┐    (terminal)
         │ PAYING │    │ CANCELED│
         └───┬────┘    └─────────┘
             │
       ┌─────┼──────┐
       ▼     │      ▼
   ┌──────┐  │ ┌─────────┐
   │ PAID │  │ │ CANCELED│
   └──┬───┘  │ └─────────┘
      │      │
      ▼      │
 ┌──────────┐│
 │ REFUNDED ││
 └──────────┘│
```

- `EnumMap<OrderStatus, EnumSet<OrderStatus>>` is the **single source of
  truth** for legal transitions. Every state change goes through
  `transition(order, next)`, which throws `OrderStateException` if the pair
  is not in the map.
- **Idempotency**: `createOrder(studentId, correlationId, ...)` returns the
  existing order for any duplicate `correlationId` within
  `IDEMPOTENCY_WINDOW_MINUTES = 10`. Beyond that window the duplicate is
  rejected.
- **30-minute auto-cancel**: `@Scheduled(fixedDelay = 60_000)` runs every
  60 seconds and cancels any `PAYING` order older than
  `PAYMENT_TIMEOUT_MINUTES = 30` with reason "Payment timeout".
- **Refund window**: `isRefundAllowed` returns true iff
  `status == PAID && (paidAt + 14 days > now || exceptionStatus == true)`.
- Every transition writes to `AuditService` and calls `MessageService.send`
  with category `ORDER`.

## 6. Grade engine (`service/GradeEngineService.java`)

`calculateGrade(studentId, courseId, ruleVersionId)`:

1. Load `GradeRule.weightsJson` (e.g. `{"coursework":30,"midterm":20,"final":50}`).
2. Load all `GradeComponent` rows for `(courseId, studentId)`.
3. **Apply retake policy**:
   - `HIGHEST_SCORE`: collapse duplicate component names, keeping the highest
     score across attempts.
   - `LATEST_SCORE`: keep the highest `attempt_number`.
4. Compute weighted score = `Σ (componentScore / componentMaxScore) × weight`.
5. Round to 2 decimal places.
6. Convert to GPA using the 4.0 scale:

| Score | Letter | GPA |
|---|---|---|
| 90–100 | A  | 4.00 |
| 85–89  | A- | 3.70 |
| 80–84  | B+ | 3.30 |
| 75–79  | B  | 3.00 |
| 70–74  | B- | 2.70 |
| 60–69  | C  | 2.00 |
| <60    | F  | 0.00 |

7. Persist a new `StudentGrade` row referencing the `ruleVersionId`.

### Rule versioning / backtracking

`recalculateAll(courseId, newRuleVersionId)` iterates every enrollment and
recomputes against the new rule. Old `StudentGrade` rows are kept intact so
the historic calculation is reproducible — auditors can prove which rule
produced any given grade.

## 7. Evaluation cycles (`service/EvaluationService.java`)

```
DRAFT ──openCycle──> OPEN ──submitCycle──> SUBMITTED ──reviewerApprove──> CLOSED
```

- `submitCycle` automatically computes outliers via `detectOutliers(cycleId)`:
  for each `EvaluationIndicator` row in the cycle, compute mean and standard
  deviation across all indicators, and flag (`is_outlier = true`) any
  indicator whose `|score - mean| > 2 * stddev`.
- `uploadEvidence(cycleId, file, uploaderId)`:
  - rejects empty files
  - rejects `file.size > MAX_EVIDENCE_BYTES = 10 MB`
  - rejects MIME types outside `ALLOWED_MIME = {pdf, jpeg, png, docx}`
  - SHA-256 hashes the file, stores under `${registrarops.upload-dir}`
- All transitions write to `AuditService`.

## 8. Messaging (`service/MessageService.java`)

`send(recipientId, category, subject, body, relatedId, relatedType)`:

1. **Muted category** → drop silently (returns `Optional.empty()`).
2. **Quiet hours** → if current local hour falls in
   `[quietStartHour, quietEndHour)` (with midnight wrap support), set
   `deliver_at` to the next quietEndHour. The unread-count query
   (`countUnreadDelivered`) only counts messages where
   `deliverAt IS NULL OR deliverAt <= now`, so deferred messages don't
   inflate the badge until their delivery window opens.
3. **Duplicate dedup**: if a message exists for the same
   `(recipient, category, relatedType, relatedId)` in the last 1 hour,
   bump `thread_count`, set `thread_key`, and reuse the existing row instead
   of inserting a new one.
4. Otherwise insert a fresh row.

The system is **offline-only**. There is no integration with WeChat, email,
or any external channel — every notification is an in-app database row.

## 9. Search (`service/SearchService.java`)

- **Suggestions** (`getSuggestions`): `LIKE` queries against `courses.title`,
  `courses.tags`, `courses.author_name`, combined with the top trending
  search terms.
- **Did you mean** (Levenshtein): when a query returns fewer than 3 results,
  measure edit distance against the top 10 trending terms (via
  `info.debatty:java-string-similarity`) and offer the closest match within
  3 edits as `didYouMean`.
- **Pinyin / synonym normalization** (`normalize`): a small static lookup
  map (Mathematics ↔ 数学, calculus ↔ 微积分, etc.) plus a synonym map
  (math → mathematics, calc → calculus). Pure lookup — no external dict
  needed for the offline-only requirement.
- **Fallback recommendations** (`getFallbackRecommendations`): when a search
  returns zero results, dedup top-rated + most-recent active courses and
  render up to 10.

## 10. Import / export + retry queue (`service/ImportExportService.java`)

- `importCoursesCsv(file, ...)` parses with OpenCSV, validates required
  fields, batch-inserts valid rows, returns `ImportResult { imported, skipped,
  errors[] }` with per-row error messages for the UI.
- **Retry queue**: `scheduleRetry(jobType, payload)` inserts a
  `retry_jobs` row in `PENDING` status.
- `@Scheduled(fixedDelay = 60_000) processRetryQueue()`:
  - claims pending jobs whose `next_retry_at <= now` and
    `attempt_count < max_attempts`
  - on success → `SUCCEEDED`
  - on failure → increment `attempt_count`; if at `max_attempts` mark
    `FAILED`; otherwise schedule
    `next_retry_at = now + 2^N × 60 seconds` (exponential backoff)

## 11. HTMX integration

| Trigger | Endpoint | Returns |
|---|---|---|
| `<input>` keyup (300 ms debounce) | `GET /api/search/suggestions?q=...` | Thymeleaf fragment `fragments/search-suggestions :: dropdown` |
| Topbar bell, every 30 s | `GET /api/notifications/count` | plain text count number |
| Notification list panel | `GET /api/notifications/list` | `fragments/notification-badge :: list` |

The base layout sets two meta tags:

```html
<meta name="_csrf"        content="...">
<meta name="_csrf_header" content="...">
```

`static/js/app.js` listens for `htmx:configRequest` and forwards the token
on every HTMX request — so HTMX-driven mutating requests still pass the
CSRF filter.

## 12. Audit log design

- Append-only at the database level (no `updated_at` column on
  `audit_logs`).
- Append-only at the type level (`AuditLogRepository` extends only
  `Repository<AuditLog,Long>` — no inherited update / delete methods, no
  hand-written ones).
- Sensitive field masking applied before persistence.
- The `/admin/audit` page is read-only with paginated reverse-chronological
  display and an `IMMUTABLE AUDIT RECORD` badge in the header.
- Every mutating service writes audit entries: order transitions
  (`ORDER_CREATED`, `ORDER_PAYING`, `ORDER_PAID`, `ORDER_CANCELED`,
  `ORDER_REFUNDED`), grade calculations (`GRADE_CALCULATED`), evaluation
  events (`EVAL_CREATED`, `EVAL_OPENED`, `EVAL_SUBMITTED`, `EVAL_APPROVED`,
  `EVIDENCE_UPLOADED`), CSV imports (`CSV_IMPORT`), user management
  (`USER_CREATED`, `USER_DEACTIVATED`), account deletion
  (`ACCOUNT_DELETE_REQUESTED`, `ACCOUNT_PURGED`), catalog ratings
  (`RATE_ITEM`).

## 13. Test architecture

- `src/test/java/com/registrarops/unit/` — pure unit tests with Mockito,
  no Spring context, run in <2 seconds total. **42 tests**.
- `src/test/java/com/registrarops/api/` — `@SpringBootTest` integration
  tests with `MockMvc`. **40 tests**.
  - `AbstractIntegrationTest` spins up a real **MySQL 8 container via
    Testcontainers** (NOT H2) with `@DynamicPropertySource` rebinding the
    Spring datasource.
  - `@DirtiesContext(AFTER_CLASS)` forces context teardown between test
    classes so Hikari pools don't accumulate.
  - Each class clears its own state in `@BeforeEach` where needed.
- Both suites run inside Docker via `docker compose --profile test run test`,
  which uses `Dockerfile.test` (Maven image + mounted Docker socket so
  Testcontainers can spawn sibling MySQL containers).

## 14. Run & operate

```bash
# Bring up the stack
docker compose up --build

# App: http://localhost:8080
#   Login                          | Username  | Password
#   --------------------------------|-----------|---------------------
#   Administrator                   | admin     | Admin@Registrar24!
#   Faculty                         | faculty   | Faculty@Reg2024!
#   Department reviewer             | reviewer  | Review@Reg2024!
#   Student                         | student   | Student@Reg24!

# Run the whole test suite inside Docker
docker compose --profile test run --build test

# Stop everything
docker compose down

# Wipe the volume too
docker compose down -v
```
