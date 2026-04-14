# PLAN.md — RegistrarOps Execution Plan
# Task ID: TASK-4-W2
# [ ] = pending  [x] = complete
# Rule: Complete ALL tasks in a phase without stopping. Pause ONLY at phase boundaries.
# Fix Java compile errors within the same task before marking [x].
# CRITICAL: QA reads every Java file (static) AND clicks every page (Docker runtime).
# ARCHITECTURE: Thymeleaf server-side rendered monolith — NO separate frontend app.

---

## PHASE 0 — Project Foundation, Maven Wrapper, Docker & Scaffold
> Goal: App starts in Docker, login page visible, test folders created, .env.example committed
> Complete all tasks continuously, then pause. Wait for "proceed".

- [x] 0.1 Create repo/.gitignore (content from CLAUDE.md)
- [x] 0.2 Create repo/.env.example (content from CLAUDE.md — committed, NOT in .gitignore)
- [x] 0.3 Create repo/README.md (minimal format from CLAUDE.md)
- [x] 0.4 Create repo/run_tests.sh (exact content from CLAUDE.md — chmod +x)
- [x] 0.5 Create repo/pom.xml — Spring Boot 3.2.x parent, all dependencies from CLAUDE.md (web, thymeleaf, thymeleaf-extras-springsecurity6, security, data-jpa, mysql-connector-j, flyway-core, flyway-mysql, validation, quartz, opencsv, poi, java-string-similarity, spring-boot-starter-test, spring-security-test, testcontainers junit-jupiter + mysql). Java 17. Maven compiler plugin.
- [x] 0.6 Create Maven wrapper: generate .mvn/wrapper/maven-wrapper.properties pointing to Maven 3.9.x, create mvnw shell script (chmod +x), create mvnw.cmd for Windows
- [x] 0.7 Create repo/Dockerfile (multi-stage build from CLAUDE.md — eclipse-temurin:17-jdk-alpine builder + eclipse-temurin:17-jre-alpine runtime)
- [x] 0.8 Create repo/Dockerfile.test (from CLAUDE.md — eclipse-temurin:17-jdk-alpine, copies src, copies run_tests.sh, chmod +x)
- [x] 0.9 Create repo/docker-compose.yml (single file, exact content from CLAUDE.md — setup + mysql + app + test[profile:test] services)
- [x] 0.10 Create src/main/java/com/registrarops/RegistrarOpsApplication.java (@SpringBootApplication)
- [x] 0.11 Create src/main/resources/application.yml — datasource (from env vars), jpa.hibernate.ddl-auto=validate, flyway.enabled=true, thymeleaf.cache=false, server.port=8080, logging.level settings. NEVER use ddl-auto=create.
- [x] 0.12 Create src/main/resources/application-docker.yml — override datasource URL for Docker network (mysql host instead of localhost)
- [x] 0.13 Create src/main/resources/application-test.yml — empty (Testcontainers auto-configures datasource via @DynamicPropertySource)
- [x] 0.14 Create src/main/resources/static/css/app.css — full custom CSS with CSS variables from CLAUDE.md (dark academic SaaS theme — deep navy backgrounds, blue accent, role color system, status badge colors, Bootstrap 5 dark theme overrides, skeleton shimmer animation, HTMX loading indicators)
- [x] 0.15 Create src/main/resources/static/js/app.js — global JS: countdown timer for orders (updates every second), flash message auto-dismiss, HTMX loading state handlers
- [x] 0.16 Create src/main/resources/static/js/search.js — search suggestion handler: debounce 300ms, HTMX triggers, keyboard navigation in dropdown, clear on escape
- [x] 0.17 Create src/main/resources/templates/layout/base.html — Thymeleaf layout fragment: Bootstrap 5 (local vendor or CDN), HTMX CDN script, app.css, app.js, sidebar fragment (th:replace), topbar fragment, main content block (layout:fragment="content"), flash messages, CSRF meta tag
- [x] 0.18 Create src/main/resources/templates/fragments/sidebar.html — role-gated nav using sec:authorize. Each role sees ONLY:
       ROLE_ADMIN: Dashboard, Catalog, Orders, Grades, Evaluations, Messages, Admin (Users/Import/Audit/Config)
       ROLE_FACULTY: Dashboard, Grades, Evaluations, Messages
       ROLE_REVIEWER: Dashboard, Evaluations, Messages
       ROLE_STUDENT: Dashboard, Catalog, Orders, Messages
- [x] 0.19 Create src/main/resources/templates/fragments/topbar.html — sticky top bar: breadcrumb, page title, notification bell (hx-get="/api/notifications/count" hx-trigger="load, every 30s"), user avatar + role badge, sign out link
- [x] 0.20 Create placeholder controllers returning placeholder Thymeleaf pages for: /, /login, /catalog, /orders, /grades, /evaluations, /messages, /admin
- [x] 0.21 Create test folder skeleton:
       src/test/java/com/registrarops/unit/GradeEngineServiceTest.java — placeholder @Test void placeholder(){}
       src/test/java/com/registrarops/unit/OrderStateMachineTest.java — placeholder
       src/test/java/com/registrarops/unit/SearchServiceTest.java — placeholder
       src/test/java/com/registrarops/unit/MessageServiceTest.java — placeholder
       src/test/java/com/registrarops/unit/PasswordValidatorTest.java — placeholder
       src/test/java/com/registrarops/unit/RefundRuleTest.java — placeholder
       src/test/java/com/registrarops/api/AuthApiTest.java — placeholder
       src/test/java/com/registrarops/api/CatalogApiTest.java — placeholder
       src/test/java/com/registrarops/api/OrderApiTest.java — placeholder
       src/test/java/com/registrarops/api/GradeApiTest.java — placeholder
       src/test/java/com/registrarops/api/EvaluationApiTest.java — placeholder
       src/test/java/com/registrarops/api/AuditApiTest.java — placeholder
- [x] 0.22 Verify: docker compose up --build → app starts → http://localhost:8080 shows a page (even if basic login redirect). Fix ALL build errors before marking done.

**Phase 0 checkpoint: docker compose up --build → app accessible at http://localhost:8080. Sidebar shows correct items per role (even with placeholder pages). Dark SaaS CSS applied.**

---

## PHASE 1 — Database Schema (Flyway Migrations + JPA Entities)
> Goal: All tables created via Flyway, all entities compile, seed data for all 4 roles
> Complete all tasks continuously, then pause. Wait for "proceed".

- [x] 1.1 V001__create_users.sql: users (id BIGINT AUTO_INCREMENT PK, username VARCHAR(100) UNIQUE, password_hash VARCHAR(255), role ENUM('ROLE_STUDENT','ROLE_FACULTY','ROLE_REVIEWER','ROLE_ADMIN'), email VARCHAR(200), full_name VARCHAR(200), is_active TINYINT DEFAULT 1, deleted_at DATETIME NULL, export_file_path VARCHAR(500) NULL, created_at DATETIME, updated_at DATETIME)
- [x] 1.2 V002__create_security.sql: login_attempts (id, username, ip_address, attempted_at), device_bindings (id, user_id, device_hash, label, bound_at), security_notices (id, user_id, notice_type, message, is_read, created_at)
- [x] 1.3 V003__create_courses.sql: courses (id, code VARCHAR(20) UNIQUE, title, description, credits DECIMAL(4,2), category, tags JSON, author_name, cover_image_url, price DECIMAL(10,2) DEFAULT 0, is_active, created_at), course_materials (id, course_id, title, type, file_url, price DECIMAL(10,2), created_at)
- [x] 1.4 V004__create_enrollments.sql: enrollments (id, student_id, course_id, enrolled_at, status ENUM('active','completed','withdrawn'), UNIQUE(student_id,course_id))
- [x] 1.5 V005__create_catalog_search.sql: search_terms (id, term VARCHAR(500) UNIQUE, search_count INT DEFAULT 0, last_searched_at), catalog_ratings (id, user_id, item_type, item_id, score TINYINT CHECK(score BETWEEN 1 AND 5), created_at, UNIQUE(user_id,item_type,item_id))
- [x] 1.6 V006__create_orders.sql: orders (id BIGINT AUTO_INCREMENT PK, correlation_id VARCHAR(36) UNIQUE, student_id, status ENUM('created','paying','paid','canceled','refunded'), total_amount DECIMAL(10,2), exception_status TINYINT DEFAULT 0, created_at, updated_at, paid_at, canceled_at, refunded_at, cancel_reason), order_items (id, order_id, item_type, item_id, item_name, unit_price, quantity, subtotal)
- [x] 1.7 V007__create_grade_rules.sql: grade_rules (id, course_id, version INT DEFAULT 1, is_active TINYINT DEFAULT 1, retake_policy ENUM('highest_score','latest_score'), weights JSON — e.g. {"coursework":30,"midterm":20,"final":50}, created_by, created_at), grade_rule_history (id, rule_id, old_weights JSON, new_weights JSON, changed_by, changed_at)
- [x] 1.8 V008__create_grades.sql: grade_components (id, course_id, student_id, component_name, score DECIMAL(5,2), max_score DECIMAL(5,2), attempt_number INT DEFAULT 1, recorded_by, recorded_at), student_grades (id, student_id, course_id, rule_version_id, weighted_score DECIMAL(5,2), letter_grade VARCHAR(5), gpa_points DECIMAL(3,2), credits DECIMAL(4,2), calculated_at)
- [x] 1.9 V009__create_evaluations.sql: evaluation_cycles (id, course_id, faculty_id, title, status ENUM('draft','open','submitted','reviewed','closed'), opened_at, closed_at, submitted_at), evaluation_indicators (id, cycle_id, indicator_name, weight DECIMAL(5,2), mean_score DECIMAL(5,2), std_dev DECIMAL(5,2)), evidence_attachments (id, cycle_id, original_filename VARCHAR(500), stored_path VARCHAR(1000), mime_type VARCHAR(100), file_size_bytes BIGINT, sha256_hash VARCHAR(64), uploaded_by, uploaded_at)
- [x] 1.10 V010__create_messages.sql: messages (id, recipient_id, sender_type VARCHAR(50), category VARCHAR(100), subject VARCHAR(500), body TEXT, related_id BIGINT NULL, related_type VARCHAR(50) NULL, is_read TINYINT DEFAULT 0, is_muted TINYINT DEFAULT 0, thread_key VARCHAR(200) NULL, created_at, deliver_at DATETIME NULL), message_preferences (id, user_id UNIQUE, muted_categories JSON, quiet_start_hour TINYINT DEFAULT 22, quiet_end_hour TINYINT DEFAULT 7)
- [x] 1.11 V011__create_retry_jobs.sql: retry_jobs (id, job_type, payload LONGTEXT, attempt_count INT DEFAULT 0, max_attempts INT DEFAULT 3, next_retry_at DATETIME, status ENUM('pending','running','succeeded','failed'), error_message TEXT NULL, created_at, updated_at)
- [x] 1.12 V012__create_audit.sql: audit_logs (id BIGINT AUTO_INCREMENT PK, actor_id BIGINT, actor_username VARCHAR(100), action VARCHAR(200), entity_type VARCHAR(100), entity_id BIGINT NULL, old_value_masked JSON NULL, new_value_masked JSON NULL, ip_address VARCHAR(45), created_at DATETIME NOT NULL — NO updated_at, this is append-only)
- [x] 1.13 V013__seed_data.sql: INSERT 4 users with bcrypt hashes for Admin@Registrar24!, Faculty@Reg2024!, Review@Reg2024!, Student@Reg24! (pre-compute real bcrypt hashes — not placeholders). INSERT sample courses (5 courses with categories/tags/prices), INSERT sample enrollments, INSERT default message_preferences for each user, INSERT grade_rules for each course, INSERT sample search_terms for trending.
- [x] 1.14 Create all JPA @Entity classes matching the migrations exactly. Use @Column with correct types. Use Lombok @Data or explicit getters/setters. All entities have @CreationTimestamp and @UpdateTimestamp where applicable.
- [x] 1.15 Create all Spring Data JPA @Repository interfaces. AuditLogRepository has ONLY findBy* methods and the inherited save(). Add Javadoc comment: "// APPEND-ONLY: no update or delete operations. Every action creates a new record."
- [x] 1.16 Verify: docker compose up --build → Flyway runs migrations → all tables present in MySQL → all 4 logins work.

**Phase 1 checkpoint: docker compose up --build → Flyway migrations run → all 13 migration files applied → all 4 credentials log in and see correct role dashboard.**

---

## PHASE 2 — Authentication, Security & Account Management
> Goal: Login works for all 4 roles, CSRF active, lockout, device binding, account deletion
> Complete all tasks continuously, then pause. Wait for "proceed".

- [x] 2.1 Create src/security/CustomUserDetailsService.java — loads User by username, checks isActive and deletedAt IS NULL, builds UserDetails with correct GrantedAuthority (role)
- [x] 2.2 Create src/security/PasswordComplexityValidator.java — validates: length >= 12, has uppercase, lowercase, digit, special char. Used as @ConstraintValidator + called in AuthService.
- [x] 2.3 Create src/config/SecurityConfig.java — configure HttpSecurity: CSRF enabled (CookieCsrfTokenRepository), form login (custom /login page), logout (/logout), role-based URL access (/admin/** → ROLE_ADMIN, /grades/** → ROLE_FACULTY + ROLE_ADMIN + ROLE_REVIEWER etc.), session management (maximumSessions=1), BCryptPasswordEncoder(12) bean.
- [x] 2.4 Create src/service/AuthService.java — trackFailedLogin(username, ip): INSERT LoginAttempt; isLockedOut(username): COUNT login_attempts where attempted_at > NOW()-15min >= 5 → return true; clearAttempts(username): delete on successful login; detectUnusualLogin(userId, request): compare device hash (SHA-256 of IP+UserAgent) to device_bindings → if no match AND device_bindings not empty → create in-app security notice via MessageService.
- [x] 2.5 Create src/controller/AuthController.java — GET /login (show login form with CSRF), POST /login (Spring Security handles, AuthController handles post-login redirect by role), GET /profile, GET /account/delete (show confirmation), POST /account/delete (trigger AccountDeletionService), GET /account/export/{token} (download export file).
- [x] 2.6 Create src/service/AccountDeletionService.java — exportUserData(userId): collect all user data (orders, grades, messages) → serialize to JSON → write to /tmp/exports/user_{id}_{token}.json → save path on user record → send in-app message with download link; softDelete(userId): set deletedAt=NOW() → schedule hard cleanup after 7 days (Quartz job).
- [x] 2.7 Create src/service/AuditService.java — log(actor, action, entityType, entityId, oldData, newData, request): create AuditLog entity, mask any phone/password fields via Jackson serializer, persist. ONLY method that writes. Javadoc: "Append-only. Never call update or delete on AuditLog."
- [x] 2.8 Create templates/auth/login.html — premium dark login page extending base layout but WITHOUT sidebar (full-screen centered card), RegistrarOps logo/wordmark, username + password inputs (dark Bootstrap 5 form-control), blue Sign In button with loading spinner, error alert (th:if="${param.error}"), locked account alert (th:if="${param.locked}"), CSRF hidden field (th:action="@{/login}" auto-includes it).
- [x] 2.9 Fill in src/test/java/com/registrarops/unit/PasswordValidatorTest.java — testValidPassword(), testTooShort(), testMissingUppercase(), testMissingDigit(), testMissingSpecialChar(), testExactly12Chars()
- [x] 2.10 Fill in src/test/java/com/registrarops/api/AuthApiTest.java — uses @SpringBootTest + Testcontainers: testLoginSuccess(), testLoginWrongPassword(), testAccountLockedAfter5Attempts(), testCsrfRequiredOnPost(), testLogoutInvalidatesSession()
- [x] 2.11 Verify: docker compose up --build → login page renders with dark theme → all 4 credentials log in → wrong password shows error → 5 failures locks account.

**Phase 2 checkpoint: Login page is premium and visually polished. All 4 logins succeed. Account lockout enforced. CSRF token present on all forms.**

---

## PHASE 3 — Course Catalog, Search & Recommendations
> Goal: Search with HTMX suggestions, typo correction, filters, fallback recommendations all working
> Complete all tasks continuously, then pause. Wait for "proceed".

- [x] 3.1 Create src/service/SearchService.java — getSuggestions(query): full-text search on courses.title, courses.tags, course_materials.title using LIKE; combine with trending search_terms; apply Levenshtein correction if results < 3 (use java-string-similarity library); normalize for pinyin (local map of common Chinese names/titles to pinyin). Returns SuggestionResult{items, didYouMean, trending}. recordSearch(term): upsert search_terms. getFallbackRecommendations(): ORDER BY catalog_ratings AVG DESC LIMIT 10 + ORDER BY created_at DESC LIMIT 10.
- [x] 3.2 Create src/service/CatalogService.java — search(CatalogFilter): JPA Specification or @Query with dynamic predicates (category, tags JSON contains, author, price range, rating min/max, new arrivals filter by created_at, bestsellers order). getItemDetail(type, id). rate(userId, itemType, itemId, score). getTrending(): top search_terms.
- [x] 3.3 Create src/controller/CatalogController.java — GET /catalog (search page with filters), GET /catalog/detail/{type}/{id} (item detail with rating form), POST /catalog/rate (submit rating).
- [x] 3.4 Create src/controller/api/SearchApiController.java — GET /api/search/suggestions?q= (HTMX endpoint, returns Thymeleaf partial template fragments/search-suggestions.html). Rate limited by session.
- [x] 3.5 Create templates/fragments/search-suggestions.html — HTMX partial: list of suggestion items (clickable, redirect to /catalog?q=), "Did you mean: X?" link, trending chips. Renders as a dropdown.
- [x] 3.6 Create templates/catalog/index.html — full catalog page: top unified search bar (dark input, HTMX-powered suggestions dropdown), filter sidebar (category select, price range slider, tags multi-select, new arrivals toggle, sort by: rating/price/new/bestsellers), main results grid (Bootstrap cards per item: cover image placeholder, title, author, price, rating stars, tags), pagination, "No results" section with fallback recommendations carousel.
- [x] 3.7 Create templates/catalog/detail.html — item detail: title, description, author, price, rating (editable stars for students), tags, enroll/add-to-cart button, related items.
- [x] 3.8 Fill in src/test/java/com/registrarops/unit/SearchServiceTest.java — testSuggestionsReturnResults(), testLevenshteinCorrection(), testFallbackWhenNoResults(), testTrendingTermsOrderedByCount(), testSearchRecordsTerm()
- [x] 3.9 Fill in src/test/java/com/registrarops/api/CatalogApiTest.java — testSearchReturnsResults(), testFilterByCategory(), testFilterByPriceRange(), testNoResultsReturnsFallback(), testRatingSubmit(), testStudentCannotManageCatalog()

**Phase 3 checkpoint: QA as student → catalog page loads → type in search bar → HTMX dropdown shows suggestions → typo "Calculas" suggests "Calculus" → filters work → no results shows fallback recommendations.**

---

## PHASE 4 — Orders, State Machine & 30-Minute Countdown
> Goal: Full order lifecycle, auto-cancel, refund rules, correlation IDs — all working in browser
> Complete all tasks continuously, then pause. Wait for "proceed".

- [x] 4.1 Create src/service/OrderService.java — createOrder(studentId, items, correlationId): check existing by correlationId (within 10min → return existing, idempotent), validate items exist + prices, INSERT order with status=created, INSERT order_items, write audit log. confirmPayment(orderId): CREATED→PAYING (only if CREATED), write audit. completePaid(orderId): PAYING→PAID, write audit, send acceptance message. cancelOrder(orderId, reason): only from CREATED or PAYING, set canceled_at, write audit, send cancellation message. refundOrder(orderId): PAID→REFUNDED; validate: paid_at > 14 days → throw RefundWindowException UNLESS order.exceptionStatus=true; write audit, send refund message. cancelExpiredOrders(): @Scheduled finds all PAYING orders older than 30min → cancel each.
- [x] 4.2 Add to OrderService: validate state transitions strictly — any invalid transition throws OrderStateTransitionException("Cannot transition from X to Y"). All state changes write AuditService.log(). All state changes send MessageService notification.
- [x] 4.3 Create src/controller/OrderController.java — GET /orders (my orders list), GET /orders/{id} (detail + timeline), POST /orders/create (with correlationId in form), POST /orders/{id}/pay (confirm payment), POST /orders/{id}/cancel, GET /orders/{id}/refund-request (show form), POST /orders/{id}/refund.
- [x] 4.4 Create templates/orders/list.html — orders table with status badges (paying=amber+pulse animation, paid=green, canceled=red, refunded=blue), sort by date, filter by status. Empty state if no orders.
- [x] 4.5 Create templates/orders/detail.html — order detail: item list + prices, status badge, order timeline (vertical timeline component showing: created→paying→paid with timestamps), 30-minute countdown timer (JS, reads data-expires-at attribute from th:data attribute set from order.createdAt+30min, updates every second, red when <5min), cancel button (visible if CREATED/PAYING), refund request button (visible if PAID + within 14 days OR exception status).
- [x] 4.6 Create templates/orders/checkout.html — checkout form: item summary, total amount, correlationId hidden field (generated UUID), payment confirmation button with loading spinner.
- [x] 4.7 Fill in src/test/java/com/registrarops/unit/OrderStateMachineTest.java — testCreatedToPayingValid(), testPayingToPaidValid(), testPaidToRefundedWithin14Days(), testPaidToRefundedAfter14DaysThrows(), testExceptionStatusOverridesRefundWindow(), testInvalidTransitionThrows(), testIdempotentOrderCreation(), testAutoCancel30MinExpiry()
- [x] 4.8 Fill in src/test/java/com/registrarops/api/OrderApiTest.java — testCreateOrderSuccess(), testCreateOrderIdempotent(), testCancelOrderSuccess(), testRefundWithin14DaysSuccess(), testRefundAfter14DaysFails(), testStudentCannotSeeOtherOrders()
- [x] 4.9 Fill in src/test/java/com/registrarops/unit/RefundRuleTest.java — testRefundAllowedBefore14Days(), testRefundBlockedAfter14Days(), testExceptionStatusAllowsLateRefund(), test14DayBoundaryExact()

**Phase 4 checkpoint: QA as student → creates order → sees 30-min countdown → completes payment → timeline shows paid → refund blocked after 14 days. Auto-cancel @Scheduled runs correctly.**

---

## PHASE 5 — Grade Engine & Evaluation Cycles
> Goal: Weighted grade calculation, GPA conversion, versioning, backtracking, evaluation workflow
> Complete all tasks continuously, then pause. Wait for "proceed".

- [x] 5.1 Create src/service/GradeEngineService.java — calculateGrade(studentId, courseId, ruleVersionId): load GradeRuleVersion.weights JSON; load GradeComponent scores for student; if retake_policy=highest_score: use max score per component; if latest_score: use most recent; compute weightedScore=sum(score/maxScore*weight); convertToGpa(score): 90-100→4.0, 85-89→3.7, 80-84→3.3, 75-79→3.0, 70-74→2.7, 60-69→2.0, <60→0.0; save StudentGrade with ruleVersionId. recalculateAll(courseId, newRuleVersionId): for each enrolled student → calculateGrade(studentId, courseId, newRuleVersionId).
- [x] 5.2 Create src/service/EvaluationService.java — openCycle(cycleId): status=draft→open; submitCycle(cycleId): status=open→submitted; detectOutliers(cycleId): for each indicator: compute mean + std_dev of all scores → return scores where |score - mean| > 2*std_dev; reviewerApprove(cycleId, comment): status=submitted→reviewed→closed; uploadEvidence(cycleId, file, userId): validate MIME (PDF/JPG/PNG/DOCX allowed), validate size <= 10MB (10485760 bytes), compute SHA-256 hash, store to /app/uploads/, save EvidenceAttachment entity, write audit log.
- [x] 5.3 Create src/controller/GradeController.java — GET /grades (faculty: courses to grade; student: my grade report), GET /grades/{courseId}/entry (faculty grade entry form), POST /grades/{courseId}/components (submit grade components), GET /grades/{courseId}/report (student grade report with GPA), GET /grades/rules (admin: rule versions), POST /grades/{courseId}/rules (admin: update weights → triggers recalculate).
- [x] 5.4 Create src/controller/EvaluationController.java — GET /evaluations (faculty: my cycles; reviewer: all submitted cycles), GET /evaluations/{cycleId} (detail), POST /evaluations/{cycleId}/open, POST /evaluations/{cycleId}/submit, POST /evaluations/{cycleId}/evidence (multipart upload), GET /evaluations/{cycleId}/review (reviewer audit view with outlier highlights), POST /evaluations/{cycleId}/approve.
- [x] 5.5 Create templates/grades/entry.html — faculty grade entry: table of enrolled students, per-student component inputs (coursework score, midterm score, final score with max values shown), save button per row + bulk save all, calculated preview (updates via HTMX when scores change — calls /api/grades/preview endpoint which returns weighted score + letter grade without saving).
- [x] 5.6 Create templates/grades/report.html — student grade report: table of courses with letter grade, GPA points, credits, weighted score; bottom summary: cumulative GPA, total credits. Clean academic report card aesthetic.
- [x] 5.7 Create templates/evaluations/cycle.html — evaluation cycle management: indicator list, upload evidence button (drag-drop or click, shows filename + size + hash after upload), status timeline, submit button (disabled until all indicators scored).
- [x] 5.8 Create templates/evaluations/review.html — reviewer audit page: indicator scores table, outlier rows highlighted in amber/red with standard deviation shown, approve or "request adjustment" buttons, reviewer comments textarea.
- [x] 5.9 Fill in src/test/java/com/registrarops/unit/GradeEngineServiceTest.java — testWeightedCalculation30_20_50(), testGpaConversion90to100(), testGpaConversionBoundaries(), testRetakePolicyHighestScore(), testRetakePolicyLatestScore(), testRecalculateAllOnRuleChange(), testRuleVersioningPreservesHistory()
- [x] 5.10 Fill in src/test/java/com/registrarops/api/GradeApiTest.java — testFacultyEntersGrade(), testStudentSeesOwnGrade(), testStudentCannotSeeOtherGrade(), testAdminUpdateRuleTriggersRecalculate(), testGpaCalculationCorrect()
- [x] 5.11 Fill in src/test/java/com/registrarops/api/EvaluationApiTest.java — testUploadEvidence(), testEvidenceExceeds10MBRejected(), testInvalidMimeTypeRejected(), testOutlierDetectionFlags(), testReviewerApprovesCycle()

**Phase 5 checkpoint: QA as faculty → enters grade components → sees weighted score preview → submits cycle. QA as reviewer → sees outlier highlighted → approves. QA as student → sees grade report with GPA.**

---

## PHASE 6 — Messaging, Notifications & Import/Export
> Goal: In-app notifications work, quiet hours, dedup threading, import/export, retry queue
> Complete all tasks continuously, then pause. Wait for "proceed".

- [x] 6.1 Create src/service/MessageService.java — send(recipientId, category, subject, body, relatedId, relatedType): (1) load MessagePreference for user; (2) check quiet hours: if current_hour >= pref.quietStart OR current_hour < pref.quietEnd → set deliver_at=next 7AM; (3) check muted categories: if category in pref.mutedCategories → skip; (4) dedup check: find existing message with same category+relatedId in last 1 hour → if found: append to thread (set thread_key if not set, link new message to thread); (5) insert Message. getUnreadCount(userId): COUNT WHERE recipient_id=? AND is_read=0 AND (deliver_at IS NULL OR deliver_at <= NOW()). markAllRead(userId): UPDATE is_read=1 for user. muteCategory(userId, category). updateQuietHours(userId, start, end).
- [x] 6.2 Create src/controller/api/NotificationApiController.java — GET /api/notifications/count (returns JSON {count:N} — for HTMX badge polling), GET /api/notifications/list (HTMX partial with recent unread messages), POST /api/notifications/mark-read (mark all read).
- [x] 6.3 Create templates/messages/index.html — notification center: tab for All/Unread/Muted, message list with category badge, subject, body, timestamp, mark-read button per item, thread view for consolidated duplicates, user preferences section (mute category checkboxes, quiet hours time pickers), empty state "All caught up!".
- [x] 6.4 Create templates/fragments/notification-badge.html — HTMX partial fragment: red badge showing count, hidden if 0. Used in topbar.
- [x] 6.5 Create src/service/ImportExportService.java — importCsv(file, entityType, fieldMapping): parse CSV with OpenCSV, validate each row against fieldMapping (required fields, type checks), collect validation errors, insert valid rows in batch, skip invalid rows with error report. getFailedJobsQueue(): list RetryJob. scheduleRetry(jobType, payload): insert RetryJob. processRetryQueue(): @Scheduled every 5 min: find pending RetryJob where next_retry_at <= NOW() AND attempt_count < max_attempts → execute job → if success: mark succeeded → if fail: increment attempts, set next_retry_at=NOW()+2^attempt*60s (exponential backoff) → if attempts=max: mark failed.
- [x] 6.6 Create src/controller/AdminController.java — GET /admin/users (list + create + deactivate), POST /admin/users, POST /admin/users/{id}/deactivate, GET /admin/import (import page), POST /admin/import/csv (upload CSV), GET /admin/import/result/{jobId} (show validation errors), GET /admin/audit (audit log viewer, paginated, NO edit/delete buttons anywhere), GET /admin/config (system config — GPA scale, refund window days, tolerance settings), POST /admin/config.
- [x] 6.7 Create templates/admin/import.html — CSV import page: file upload zone (drag-drop + click), entity type select (students/courses/grades), column mapping form (map CSV headers to system fields), submit, progress indicator, results table (imported: N, skipped: M, errors list with row number + message).
- [x] 6.8 Create templates/admin/audit.html — audit log table: timestamp, actor, action, entity type, entity ID, old value (masked), new value (masked). Filters by date range, action type, entity. Pagination. No edit/delete buttons anywhere — badge "IMMUTABLE AUDIT RECORD" in header.
- [x] 6.9 Fill in src/test/java/com/registrarops/unit/MessageServiceTest.java — testQuietHoursDefersMessage(), testMutedCategorySkips(), testDuplicateMessageThreads(), testUnreadCountCorrect(), testDeliverAtSetCorrectly()
- [x] 6.10 Fill in src/test/java/com/registrarops/api/AuditApiTest.java — testAuditLogCreatedOnAction(), testAuditLogHasNoUpdateEndpoint(), testAdminCanReadAuditLog(), testStudentCannotReadAuditLog(), testSensitiveFieldsMaskedInLog()

**Phase 6 checkpoint: QA → notification bell shows count → messages center shows order notifications → quiet hours defer delivery → import CSV shows validation errors → audit log shows all actions → no edit/delete on audit page.**

---

## PHASE 7 — Role Dashboards, Complete UI & REST APIs
> Goal: Every page premium, every role has full dashboard, REST APIs documented
> Complete all tasks continuously, then pause. Wait for "proceed".

- [ ] 7.1 Create templates/dashboard/index.html — role-specific dashboard (single template, conditionals by role):
       STUDENT: today's enrolled courses, pending orders with countdown badges, recent grades, unread notifications count
       FACULTY: courses to grade (with completion %), open evaluation cycles, recent activity
       REVIEWER: submitted cycles awaiting review (count badge), outlier summary
       ADMIN: system stats (user count, order volume, active courses), recent audit entries, pending retry jobs, import history
- [ ] 7.2 Create src/controller/DashboardController.java — GET / → loads role-specific dashboard data from services, adds to Model, renders dashboard/index.html
- [ ] 7.3 Complete templates/auth/login.html — ensure it's visually stunning: full-screen dark background, centered premium card, logo, gradient accent on submit button, copyright footer
- [ ] 7.4 Final UI pass — ALL pages must have:
       Consistent dark theme using CSS variables from CLAUDE.md (--bg-primary, --accent etc.)
       Status badges with correct colors per CLAUDE.md spec
       Skeleton loading for HTMX-loaded sections (CSS shimmer)
       Empty states with icon + helpful message + action button
       Flash messages (success in green, error in red, info in blue) auto-dismiss after 4 seconds
       Breadcrumb navigation in topbar
       Responsive layout (Bootstrap 5 grid, mobile-friendly sidebar collapse)
- [ ] 7.5 Create REST API endpoints for external integration (in api/ controllers):
       GET /api/v1/students (ADMIN + REVIEWER only, paginated)
       GET /api/v1/students/{id}/grades
       GET /api/v1/courses (all roles)
       GET /api/v1/courses/{id}/grades (FACULTY + ADMIN)
       GET /api/v1/reports/gpa-summary (ADMIN)
       All require session auth + role check via @PreAuthorize. Return JSON (Accept: application/json). CSRF not required for GET. POST APIs require CSRF token in X-XSRF-TOKEN header.
- [ ] 7.6 Verify: docker compose up --build → every page renders → no Thymeleaf template errors → every role sees correct sidebar items → UI looks premium throughout.

**Phase 7 checkpoint: QA logs in as each of 4 roles → correct sidebar → premium dashboard → every navigation link leads to functional page → UI is visually polished and consistent.**

---

## PHASE 8 — Complete Test Suite & Docker Verification
> Goal: Both test suites pass in Docker, static audit clean, end-to-end QA scenario verified
> Complete all tasks continuously, then pause. Wait for "proceed".

- [ ] 8.1 Audit: grep -r "createNativeQuery\|createQuery.*\"SELECT\|EntityManager" src/main/java/ → must return zero (use JPA repositories + @Query JPQL only)
- [ ] 8.2 Audit: grep -n "update\|delete" src/main/java/com/registrarops/repository/AuditLogRepository.java → must return zero methods with those names
- [ ] 8.3 Audit: grep -r "System.out.println\|e.printStackTrace" src/main/java/ → must be zero (use SLF4J logger only)
- [ ] 8.4 Audit: every @Controller POST method — verify th:action uses @{} (which auto-includes CSRF token via Thymeleaf Spring Security integration)
- [ ] 8.5 Audit: every @Service method that modifies data — verify auditService.log() is called. Add any missing calls.
- [ ] 8.6 Write final missing tests to ensure complete coverage of all 4 QA paths:
       Security: testCsrfMissingOnPostReturns403(), testLockedAccountRedirects(), testRoleBasedUrlAccess_StudentCannotAccessAdmin()
       Business: testGpaScaleBoundaries(), testOrderStateTransitionAll(), testEvidenceFileSizeValidation(), testRetryQueueExponentialBackoff()
       Privacy: testSensitiveFieldsMaskedInAuditLog(), testAccountDeletionSoftDelete()
- [ ] 8.7 Create AbstractIntegrationTest.java with @Testcontainers + @SpringBootTest(webEnvironment=RANDOM_PORT): start MySQL Testcontainer, @DynamicPropertySource to override datasource URL, shared across all api/ test classes.
- [ ] 8.8 Run: docker compose --profile test run --build test → fix ALL failures until output shows:
       ✅ Unit Tests PASSED
       ✅ API Tests PASSED
       ALL TESTS PASSED
       Exit code 0
- [ ] 8.9 Run: docker compose up --build → full end-to-end scenario:
       Login as admin → manage users → view audit log (immutable badge visible)
       Login as faculty → enter grades → open evaluation cycle → upload evidence → submit
       Login as reviewer → see submitted cycle → outlier highlighted → approve
       Login as student → search catalog (HTMX suggestions work) → create order → see 30-min countdown → complete payment → see timeline → submit messages notification visible
       Verify: notification bell updates count → messages center shows notifications → quiet hours preference saves
- [ ] 8.10 Final checks: grep -r "TODO\|FIXME\|placeholder\|stub" src/main/java/ src/main/resources/templates/ | grep -v "test\|\.md" → must be zero

**Phase 8 checkpoint: docker compose --profile test run test → ALL TESTS PASSED exit 0. Full end-to-end scenario passes for all 4 roles.**

---

## PHASE 9 — Documentation
> Final phase — generate docs from actual implemented code. No pause needed.

- [ ] 9.1 Create docs/design.md — from actual implemented code: ASCII architecture diagram (Browser→Thymeleaf templates←Spring Boot←MySQL), all JPA entities and relationships, security architecture (Spring Security config, CSRF, lockout, device binding), grade engine algorithm (weighted calculation, GPA scale, versioning), order state machine (all transitions, rules), message service (quiet hours, dedup, threading), import/export (retry queue with exponential backoff), audit log design (append-only, 7-year retention per prompt), Flyway migration list.
- [ ] 9.2 Create docs/api-spec.md — from actual implemented code: every REST endpoint (method, path, role required, CSRF required, request/response shape, error codes), external integration REST API (/api/v1/**), HTMX endpoints (search suggestions, notification count), import/export endpoints, standard error response format.

---

## Execution Notes for Claude

- Complete ALL tasks in a phase without stopping between tasks
- Mark [x] immediately then continue — never pause mid-phase
- Fix Java/Thymeleaf errors within the same task before marking [x]
- Only pause after entire phase checkpoint passes
- At each pause: brief summary (files created, checkpoint result)
- Thymeleaf rule: EVERY template extends layout/base.html via th:replace or Thymeleaf Layout Dialect. No standalone templates.
- HTMX rule: search suggestions and notification badge MUST use hx-get, hx-trigger, hx-target — not full page reload
- Security rule: EVERY form with th:action automatically includes CSRF token via Spring Security Thymeleaf integration — verify this is wired correctly in SecurityConfig
- Audit rule: NEVER add update/delete to AuditLogRepository
- Real data rule: NEVER use hardcoded lists in @Controller — always load from service → repository
