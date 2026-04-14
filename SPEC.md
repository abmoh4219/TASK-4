---

# SPEC.md — RegistrarOps Academic Services Portal
# Task ID: TASK-4-W2
# Single source of truth. All decisions trace back to this file.

## Original Business Prompt (Verbatim — Do Not Modify)

Build a RegistrarOps Academic Services Portal that lets a school run course records, student performance, and bookstore-style discovery in one offline-first web system. Users include Students who browse and search for courses and learning materials, Faculty who enter grades and complete evaluation cycles, Department Reviewers who audit outlier scores and approve adjustments, and Administrators who manage integrations, catalogs, and policies. The Thymeleaf-based UI provides a unified search bar with instant suggestions, trending terms, and "did you mean" typo correction (including pinyin/synonym support for names and titles), plus multi-dimensional filtering and sorting by category, tags, author, rating, price range, new arrivals, and bestsellers/new-release rankings; when no results are found, the interface shows locally generated fallback recommendations from recent and popular items. Students can create orders for paid materials or service fees and see a clear order timeline with statuses, cancellations, and refunds; the UI must show a countdown and auto-cancel if not completed within 30 minutes. An in-app Messaging and Notification Center displays order acceptance, status changes, reminders, exceptions, and refunds, with user preferences to mute categories, set quiet hours (10:00 PM–7:00 AM), and consolidate duplicate messages into a single threaded entry.
The backend uses Spring Boot to implement a state-machine-driven order lifecycle (created, paying, paid, canceled, refunded) with strict transitions, configurable refund/change rules (no refunds after 14 days from purchase unless the order is in exception status), and reconciliation support via unique correlation IDs that persist across retries and exports. Because the system must run fully offline, all messaging is delivered in-app only; external channels such as WeChat subscription messages are disabled by configuration and must never be required for any workflow. Authentication is local username-and-password with a minimum 12-character password, complexity checks, salted hashing, 5 failed attempts lockout for 15 minutes, optional device binding per workstation, and unusual-login alerts shown as in-app security notices; account deletion triggers a local data export to a downloadable file and a 7-day soft-delete window. MySQL stores students, courses, enrollments, grade components, evaluation cycles, indicators, evidence attachments (file type and size validation only, max 10 MB each), and audit trails. Grade calculation is handled by a rules engine supporting weighted categories (for example 30% coursework, 20% midterm, 50% final), GPA conversion on a 4.0 scale, credit calculations, makeup/retake policies (highest-score or latest-score strategy per course), and rule versioning with backtracking recalculation when policies change. System integration exposes standardized REST-style APIs for students, courses, grades, and reports, with offline import/export supporting field mapping, validation error reports, and a failed-job retry queue that retries up to 3 times with exponential backoff stored locally; all API access requires role-based authorization and server-side input validation to prevent tampering.

## Project Metadata

- Task ID: TASK-4-W2
- Project Type: fullstack
- Language: Java 17
- Frontend: Thymeleaf 3 + Bootstrap 5 + HTMX 1.x + Vanilla JS (server-side rendered)
- Backend: Spring Boot 3.x
- Database: MySQL 8 (Flyway migrations)
- Infrastructure: Docker + single docker-compose.yml
- Testing: JUnit 5 + Spring Boot Test + Testcontainers (real MySQL in Docker)
- Build: Maven (mvnw wrapper — no local Maven required)

> IMPORTANT: This is a server-side rendered app (Thymeleaf). There is NO separate
> frontend framework or frontend build step. All HTML is rendered by Spring Boot.
> JavaScript is minimal vanilla JS + HTMX for real-time interactions only.

> PRIORITY RULE: Original business prompt takes absolute priority over metadata.

## Roles (all 4 must be implemented with distinct permissions AND working sidebar)

| Role | Key Responsibilities |
|---|---|
| Student | Browse/search catalog, enroll in courses, create orders, view grades, messages |
| Faculty | Enter grades, manage evaluation cycles, submit evidence attachments |
| Department Reviewer | Audit outlier scores, approve grade adjustments, review evaluation cycles |
| Administrator | Manage users/catalogs/policies, import/export, view audit trails, system config |

## Core Modules (all must be fully implemented AND functional in Docker)

1. **Auth & Security** — Local login only, 12-char min password + complexity, bcrypt hashing, lockout (5 attempts / 15 min), optional device binding, unusual-login in-app alerts, CSRF on all forms, account deletion (local export + 7-day soft delete)
2. **Course Catalog & Search** — Unified search bar with HTMX instant suggestions, trending terms, Levenshtein "did you mean" typo correction, pinyin/synonym support, multi-dimensional filters (category/tags/author/rating/price/new arrivals/bestsellers), fallback recommendations when no results
3. **Orders & State Machine** — States: created→paying→paid→canceled→refunded (strict transitions), 30-minute countdown with auto-cancel (Spring scheduler), correlation IDs for idempotency, refund rules (no refund after 14 days except exception status), order timeline UI
4. **Messaging & Notifications** — In-app only (WeChat disabled by config), mute categories, quiet hours (10PM–7AM), duplicate consolidation into threaded entries, real-time badge count via HTMX polling
5. **Grade Engine** — Weighted categories (e.g., 30/20/50), GPA 4.0 scale, credit calculations, makeup/retake policy (highest-score OR latest-score per course), rule versioning, backtracking recalculation when rules change
6. **Evaluation Cycles** — Faculty enter grades, attach evidence (max 10MB, type/size validation), Department Reviewer audits outliers (scores > 2 std dev from mean), approves adjustments, cycle open/close lifecycle
7. **Import/Export & Integration** — REST APIs (students/courses/grades/reports), CSV/Excel import with field mapping + validation error report, failed-job retry queue (3 retries, exponential backoff), offline export to downloadable file
8. **Audit Trail** — Immutable records for all critical actions, no UPDATE/DELETE on audit_logs table

## QA Evaluation — TWO SIMULTANEOUS TESTS (both must pass)

### TEST 1 — Static Code Audit (AI reads every Java file)
- All 8 modules explicitly coded — no stubs
- Security: CSRF filter, lockout logic, bcrypt — real Java code with comments
- Grade engine: weighted calculation, GPA conversion — explicit service methods
- Order state machine: strict transitions coded in service layer
- AuditService: append-only, no update/delete on audit_log table
- Testcontainers used in integration tests — real MySQL, no H2
- No raw SQL string concatenation — JPA/JPQL only

### TEST 2 — Docker Runtime (human clicks every page)
```
docker compose up --build
→ http://localhost:8080
→ Login with all 4 role credentials from README
→ Test every feature end-to-end for every role
→ No broken pages, no 500 errors, no placeholder Thymeleaf templates
→ Every form submits → data persists in MySQL → page refreshes with real data
```

PASS = Test 1 AND Test 2 both pass simultaneously.

## Non-Negotiable Delivery Rules

- Single `docker-compose.yml` (setup + mysql + app + test services)
- `docker compose up --build` → app at http://localhost:8080
- `docker compose --profile test run --build test` → runs run_tests.sh via Docker
- `run_tests.sh` — Docker-first (uses ./mvnw inside container), also runnable locally if Java 17 installed
- Backend tests: `src/test/java/.../unit/` (JUnit5 + Mockito) AND `src/test/java/.../api/` (Spring Boot Test + Testcontainers real MySQL)
- `.env.example` committed to git, auto-copied to `.env` by Docker setup service
- `.env` in `.gitignore`, `.env.example` NOT ignored
- Minimal README: Run / Test / Stop / Login only
- All code inside `repo/`
- Zero manual setup after `git clone`

---
