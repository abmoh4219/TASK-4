# CLAUDE.md — RegistrarOps Academic Services Portal
# Task ID: TASK-4-W2
# Read SPEC.md + CLAUDE.md + PLAN.md before every single response. No exceptions.

## Read Order (mandatory, every response)
1. SPEC.md — source of truth
2. CLAUDE.md — this file
3. PLAN.md — current execution state

## Project Identity

- Name: RegistrarOps Academic Services Portal
- Task ID: TASK-4-W2
- Stack: Spring Boot 3.x + Thymeleaf 3 + Bootstrap 5 + HTMX + MySQL 8
- Language: Java 17 (backend + templates — no separate frontend framework)
- Build: Maven (mvnw wrapper — works in Docker without local Maven)
- Testing: JUnit 5 + Mockito + Spring Boot Test + Testcontainers

> ARCHITECTURE NOTE: This is a MONOLITHIC server-side rendered application.
> Thymeleaf templates ARE the frontend — rendered by Spring Boot controllers.
> HTMX is used for real-time partial page updates (search suggestions, notifications).
> There is NO separate React/Vue/Angular app and NO separate API server.
> The Spring Boot app serves HTML pages AND provides REST endpoints for HTMX + import/export.

## QA Evaluation — BOTH TESTS MUST PASS

TEST 1 — Static audit: AI reads every .java and .html file looking for file:line evidence.
TEST 2 — Docker runtime: Human logs in as each of 4 roles, clicks every page, tests every feature.
Both must pass. One without the other = FAIL.

## Project Structure (all code inside repo/)

```
TASK-4-W2/
├── SPEC.md
├── CLAUDE.md
├── PLAN.md
├── docs/
├── sessions/
├── metadata.json
└── repo/
    ├── pom.xml                          ← Spring Boot parent, all deps
    ├── mvnw + .mvn/                     ← Maven wrapper (chmod +x mvnw)
    ├── .env.example                     ← committed to git
    ├── .gitignore
    ├── README.md
    ├── run_tests.sh                     ← chmod +x, Docker-first
    ├── docker-compose.yml               ← single file
    ├── Dockerfile                       ← multi-stage: build + runtime
    └── src/
        ├── main/
        │   ├── java/com/registrarops/
        │   │   ├── RegistrarOpsApplication.java
        │   │   ├── config/
        │   │   │   ├── SecurityConfig.java      ← Spring Security, CSRF, role URLs
        │   │   │   ├── FlywayConfig.java
        │   │   │   └── SchedulerConfig.java
        │   │   ├── controller/
        │   │   │   ├── AuthController.java      ← /login, /logout, /register
        │   │   │   ├── DashboardController.java ← / (role-gated dashboard)
        │   │   │   ├── CatalogController.java   ← /catalog, /catalog/search
        │   │   │   ├── OrderController.java     ← /orders/**
        │   │   │   ├── GradeController.java     ← /grades/**
        │   │   │   ├── EvaluationController.java← /evaluations/**
        │   │   │   ├── MessageController.java   ← /messages/**
        │   │   │   ├── AdminController.java     ← /admin/**
        │   │   │   └── api/
        │   │   │       ├── SearchApiController.java  ← HTMX partials + REST
        │   │   │       ├── OrderApiController.java   ← REST for import/export
        │   │   │       ├── GradeApiController.java
        │   │   │       └── NotificationApiController.java
        │   │   ├── service/
        │   │   │   ├── AuthService.java
        │   │   │   ├── SearchService.java        ← suggestions, typo, pinyin, trending
        │   │   │   ├── CatalogService.java
        │   │   │   ├── OrderService.java         ← state machine, countdown, refunds
        │   │   │   ├── GradeEngineService.java   ← weighted calc, GPA, versioning
        │   │   │   ├── EvaluationService.java
        │   │   │   ├── MessageService.java       ← in-app only, quiet hours, dedup
        │   │   │   ├── ImportExportService.java  ← CSV/Excel, retry queue
        │   │   │   ├── AuditService.java         ← append-only
        │   │   │   └── AccountDeletionService.java
        │   │   ├── entity/
        │   │   │   ├── User.java
        │   │   │   ├── Role.java
        │   │   │   ├── DeviceBinding.java
        │   │   │   ├── LoginAttempt.java
        │   │   │   ├── Course.java
        │   │   │   ├── CourseMaterial.java
        │   │   │   ├── Enrollment.java
        │   │   │   ├── Order.java               ← state machine entity
        │   │   │   ├── OrderItem.java
        │   │   │   ├── OrderCorrelation.java    ← idempotency
        │   │   │   ├── GradeRule.java           ← versioned rules
        │   │   │   ├── GradeRuleVersion.java
        │   │   │   ├── GradeComponent.java
        │   │   │   ├── StudentGrade.java
        │   │   │   ├── EvaluationCycle.java
        │   │   │   ├── EvaluationIndicator.java
        │   │   │   ├── EvidenceAttachment.java
        │   │   │   ├── Message.java
        │   │   │   ├── MessagePreference.java
        │   │   │   ├── SearchTerm.java          ← trending terms tracking
        │   │   │   ├── RetryJob.java            ← failed-job queue
        │   │   │   └── AuditLog.java            ← NO @PreUpdate, NO delete
        │   │   ├── repository/
        │   │   │   └── ...                      ← JPA repos; AuditLogRepository has NO save(existing)
        │   │   └── security/
        │   │       ├── CustomUserDetailsService.java
        │   │       └── PasswordComplexityValidator.java
        │   └── resources/
        │       ├── application.yml
        │       ├── application-docker.yml
        │       ├── db/migration/               ← Flyway SQL migrations
        │       │   ├── V001__create_users.sql
        │       │   ├── V002__create_courses.sql
        │       │   └── ...
        │       ├── static/
        │       │   ├── css/
        │       │   │   └── app.css              ← premium dark SaaS custom styles
        │       │   └── js/
        │       │       ├── app.js               ← global JS (countdown, badges)
        │       │       └── search.js            ← search suggestions handler
        │       └── templates/
        │           ├── layout/
        │           │   └── base.html            ← main layout (sidebar + topbar)
        │           ├── auth/
        │           │   └── login.html
        │           ├── dashboard/
        │           │   └── index.html           ← role-specific dashboard
        │           ├── catalog/
        │           │   ├── index.html           ← search + browse
        │           │   └── detail.html          ← item detail page
        │           ├── orders/
        │           │   ├── list.html
        │           │   ├── detail.html          ← order timeline + countdown
        │           │   └── checkout.html
        │           ├── grades/
        │           │   ├── entry.html           ← faculty grade entry
        │           │   └── report.html          ← student grade report
        │           ├── evaluations/
        │           │   ├── cycle.html
        │           │   └── review.html          ← reviewer audit page
        │           ├── messages/
        │           │   └── index.html           ← notification center
        │           ├── admin/
        │           │   ├── users.html
        │           │   ├── import.html
        │           │   ├── audit.html
        │           │   └── config.html
        │           └── fragments/
        │               ├── sidebar.html         ← role-gated nav (th:if per role)
        │               ├── topbar.html
        │               ├── search-suggestions.html  ← HTMX partial
        │               └── notification-badge.html  ← HTMX partial
        └── test/
            └── java/com/registrarops/
                ├── unit/                        ← JUnit5 + Mockito, no DB
                │   ├── GradeEngineServiceTest.java
                │   ├── OrderStateMachineTest.java
                │   ├── SearchServiceTest.java
                │   ├── MessageServiceTest.java
                │   ├── PasswordValidatorTest.java
                │   └── RefundRuleTest.java
                └── api/                         ← Spring Boot Test + Testcontainers
                    ├── AuthApiTest.java
                    ├── CatalogApiTest.java
                    ├── OrderApiTest.java
                    ├── GradeApiTest.java
                    ├── EvaluationApiTest.java
                    └── AuditApiTest.java
```

## Non-Negotiable Rules

1. **Read SPEC.md + CLAUDE.md + PLAN.md first.** Every response, no exceptions.
2. **One task at a time.** Complete exactly the current PLAN.md task.
3. **Mark [x] then continue.** Update PLAN.md and move to next task immediately.
4. **All code in repo/.** Never create files outside repo/.
5. **Every Thymeleaf page must work.** QA clicks every page. No Thymeleaf template errors, no 500s.
6. **Sidebar is role-gated.** Each role sees ONLY their permitted nav items using th:if + sec:authorize.
7. **CSRF on every form.** Every th:action form includes th:field="*{_csrf}" or layout includes csrf meta tag.
8. **JPA only — no raw SQL.** Use @Query with JPQL or Spring Data methods. No EntityManager.createNativeQuery with string concatenation.
9. **AuditService is append-only.** AuditLogRepository ONLY has save() for new records. No update, no delete. Comment: "APPEND-ONLY by design."
10. **Testcontainers for integration tests.** Real MySQL in Docker. No H2. No in-memory DB.
11. **Pause at phase boundaries only.** Fix Java compile errors within same task.
12. **Premium SaaS UI.** Bootstrap 5 dark theme + custom CSS. Not default Bootstrap gray.
13. **HTMX for real-time features.** Search suggestions and notification badge use hx-get, hx-trigger, hx-target. Not full page reload.
14. **No hardcoded data in templates.** All th:text and th:each bound to real Model attributes from service layer.

## Tech Stack Details

### pom.xml — Key Dependencies
```xml
<!-- Spring Boot 3.2.x parent -->
<dependencies>
  <!-- Web -->
  <dependency>spring-boot-starter-web</dependency>
  <dependency>spring-boot-starter-thymeleaf</dependency>
  <dependency>thymeleaf-extras-springsecurity6</dependency>

  <!-- Security -->
  <dependency>spring-boot-starter-security</dependency>

  <!-- Data -->
  <dependency>spring-boot-starter-data-jpa</dependency>
  <dependency>mysql-connector-j</dependency>
  <dependency>flyway-mysql</dependency>
  <dependency>flyway-core</dependency>

  <!-- Validation -->
  <dependency>spring-boot-starter-validation</dependency>

  <!-- Scheduling (order auto-cancel, retry queue) -->
  <dependency>spring-boot-starter-quartz</dependency>

  <!-- CSV/Excel import -->
  <dependency>opencsv:5.9</dependency>
  <dependency>apache-poi:5.x</dependency>

  <!-- Typo correction / edit distance -->
  <dependency>info.debatty:java-string-similarity:2.0.0</dependency>

  <!-- Testing -->
  <dependency scope="test">spring-boot-starter-test</dependency>
  <dependency scope="test">spring-security-test</dependency>
  <dependency scope="test">testcontainers:mysql</dependency>
  <dependency scope="test">testcontainers:junit-jupiter</dependency>
</dependencies>
```

### Security Architecture (all explicitly coded with comments)

```java
// SecurityConfig.java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    // CSRF enabled for all form posts (Thymeleaf auto-includes token)
    // Role-based URL authorization
    // Custom login page: /login
    // Session management: maximum 1 session per user
    // Password encoder: BCryptPasswordEncoder(12)
}

// PasswordComplexityValidator.java
// min 12 chars, must have: uppercase, lowercase, digit, special char
// Called on registration and password change

// AuthService.java
// trackFailedLogin(username): INSERT into login_attempts
// isLockedOut(username): COUNT where attempted_at > NOW()-15min >= 5
// detectUnusualLogin(userId, request): compare IP/user-agent to known devices
//   → if unknown: create in-app security notice message
```

### Order State Machine

```java
// OrderService.java — strict state transitions, no skipping states
// CREATED → PAYING (student confirms order)
// PAYING  → PAID    (payment/cost-center confirmed)
// PAYING  → CANCELED (30-min timeout via @Scheduled or Quartz)
// PAID    → REFUNDED (within 14 days, or exception status overrides)
// Any other transition → throws OrderStateException

// CorrelationId: UUID generated client-side, stored on Order
// Idempotency: if same correlationId within 10min → return existing order

// 30-minute auto-cancel:
// @Scheduled(fixedDelay = 60000)
// find all PAYING orders where createdAt < NOW() - 30min → cancelExpired()
```

### Grade Engine

```java
// GradeEngineService.java
// calculateGrade(studentId, courseId, ruleVersionId):
//   1. Load GradeRuleVersion (e.g., {coursework:30, midterm:20, final:50})
//   2. Load GradeComponent scores for student
//   3. weightedScore = sum(component.score * rule.weight / 100)
//   4. gpa = convertToGpa(weightedScore) — 4.0 scale
//   5. Apply makeup/retake policy:
//      HIGHEST_SCORE: use max(originalScore, retakeScore)
//      LATEST_SCORE: use most recent attempt
//   6. Save StudentGrade with ruleVersionId (for backtracking)
//
// recalculateAll(courseId, newRuleVersionId):
//   For all students in course: recalculate with new rules
//   Saves new StudentGrade records (keeps old ones with old ruleVersionId)
```

### Search Service

```java
// SearchService.java
// getSuggestions(query): full-text LIKE search on title/author/tags
//   + trending terms (SearchTerm table, sorted by count)
//   + "did you mean": if results < 3, find closest term via Levenshtein distance
//   + pinyin support: normalize CJK characters to pinyin before matching
// getTrendingTerms(): top 10 terms from SearchTerm by search_count DESC
// recordSearch(term): upsert SearchTerm count
// getFallbackRecommendations(): when query has 0 results
//   → return recent (last 30 days) + popular (top rated) items
```

### HTMX Integration

```html
<!-- Search suggestions — real-time via HTMX -->
<input type="text" name="q"
       hx-get="/api/search/suggestions"
       hx-trigger="keyup changed delay:300ms"
       hx-target="#suggestions-dropdown"
       hx-include="[name='q']">
<div id="suggestions-dropdown"></div>

<!-- Notification badge — polls every 30 seconds -->
<span hx-get="/api/notifications/count"
      hx-trigger="every 30s"
      hx-swap="outerHTML"
      class="notification-badge">
```

### Message Service

```java
// MessageService.java
// send(userId, type, content, relatedId):
//   1. Check quiet hours (10PM–7AM local time) → if quiet, defer to 7AM
//   2. Check user.mutedCategories → skip if muted
//   3. Check for duplicate: same type+relatedId in last 1 hour → thread into existing
//   4. Save Message (never call external service — offline-only)
// getUnread(userId): count unread messages
// markRead(messageId, userId): update readAt
```

## UI Design Standards (Premium Academic SaaS)

```css
/* app.css — CSS custom properties */
:root {
  --bg-primary:   #0D1117;   /* GitHub-dark inspired deep background */
  --bg-secondary: #161B22;   /* sidebar + card surface */
  --bg-card:      #1C2128;   /* elevated cards */
  --bg-hover:     #222831;   /* hover states */
  --border:       #30363D;   /* subtle borders */
  --accent:       #2F81F7;   /* primary blue accent (academic trustworthy) */
  --accent-green: #3FB950;   /* success / paid status */
  --accent-amber: #D29922;   /* warning / pending */
  --accent-red:   #F85149;   /* danger / canceled */
  --accent-purple:#8957E5;   /* faculty / evaluation */
  --text-primary:  #E6EDF3;
  --text-secondary:#8B949E;
  --text-muted:    #484F58;
}
```

Role badge colors:
- Student: blue (`--accent`)
- Faculty: purple (`--accent-purple`)
- Reviewer: amber (`--accent-amber`)
- Admin: green (`--accent-green`)

Order status badges:
- created: gray, paying: amber+pulse, paid: green, canceled: red, refunded: blue

Every Thymeleaf page must have:
- Loading skeleton (CSS animation) for HTMX-loaded sections
- Empty state with icon + message + action button
- Flash message area for success/error feedback
- Breadcrumb navigation

## Docker Architecture (single docker-compose.yml)

```yaml
services:
  setup:
    image: alpine:3.18
    volumes: [".:/workspace"]
    command: >
      sh -c "[ ! -f /workspace/.env ] &&
             cp /workspace/.env.example /workspace/.env &&
             echo 'Created .env from .env.example' || echo '.env exists'"
    restart: "no"

  mysql:
    image: mysql:8.0
    env_file: [{ path: .env, required: false }]
    environment:
      MYSQL_DATABASE: ${MYSQL_DATABASE:-registrarops}
      MYSQL_USER: ${MYSQL_USER:-registrar}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD:-registrar_pass}
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-root_pass}
    volumes: [mysql-data:/var/lib/mysql]
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 10
    depends_on:
      setup:
        condition: service_completed_successfully

  app:
    build: { context: ., dockerfile: Dockerfile }
    ports: ["8080:8080"]
    env_file: [{ path: .env, required: false }]
    environment:
      SPRING_DATASOURCE_URL: ${SPRING_DATASOURCE_URL:-jdbc:mysql://mysql:3306/registrarops}
      SPRING_DATASOURCE_USERNAME: ${MYSQL_USER:-registrar}
      SPRING_DATASOURCE_PASSWORD: ${MYSQL_PASSWORD:-registrar_pass}
      SPRING_PROFILES_ACTIVE: docker
    depends_on:
      mysql: { condition: service_healthy }

  test:
    profiles: [test]
    build: { context: ., dockerfile: Dockerfile.test }
    env_file: [{ path: .env, required: false }]
    environment:
      SPRING_PROFILES_ACTIVE: test
    command: ["sh", "run_tests.sh"]
    depends_on:
      setup: { condition: service_completed_successfully }

volumes:
  mysql-data:
```

## Dockerfile (multi-stage)
```dockerfile
# Stage 1: Build
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
```

## Dockerfile.test
```dockerfile
FROM eclipse-temurin:17-jdk-alpine
RUN apk add --no-cache docker-cli
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q
COPY src/ src/
COPY run_tests.sh ./
RUN chmod +x run_tests.sh
CMD ["sh", "run_tests.sh"]
```

## run_tests.sh

```bash
#!/bin/sh
set -e
echo "========================================"
echo "  RegistrarOps Test Suite"
echo "========================================"

# Uses ./mvnw (Maven wrapper — no local Maven needed)
# Docker-first: run via: docker compose --profile test run --build test
# Also runnable locally if Java 17 is installed

UNIT_FAILED=0
API_FAILED=0

echo ""
echo "--- Unit Tests (src/test/java/.../unit/) ---"
./mvnw test -Dtest="com.registrarops.unit.*" \
  --no-transfer-progress \
  -Dspring.profiles.active=test 2>&1 || UNIT_FAILED=1
[ $UNIT_FAILED -eq 0 ] && echo "✅ Unit Tests PASSED" || echo "❌ Unit Tests FAILED"

echo ""
echo "--- API / Integration Tests (src/test/java/.../api/) ---"
./mvnw test -Dtest="com.registrarops.api.*" \
  --no-transfer-progress \
  -Dspring.profiles.active=test 2>&1 || API_FAILED=1
[ $API_FAILED -eq 0 ] && echo "✅ API Tests PASSED" || echo "❌ API Tests FAILED"

echo ""
echo "========================================"
TOTAL=$((UNIT_FAILED + API_FAILED))
[ $TOTAL -eq 0 ] && echo "  ALL TESTS PASSED" && exit 0
echo "  SOME TESTS FAILED"
echo "  Unit Tests: $([ $UNIT_FAILED -eq 0 ] && echo PASS || echo FAIL)"
echo "  API Tests:  $([ $API_FAILED -eq 0 ] && echo PASS || echo FAIL)"
exit 1
```

## .env.example (committed to git)

```
SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/registrarops?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
MYSQL_DATABASE=registrarops
MYSQL_USER=registrar
MYSQL_PASSWORD=registrar_pass
MYSQL_ROOT_PASSWORD=root_pass
APP_ENCRYPTION_KEY=registrarops-aes-256-key-32bytes!
SPRING_PROFILES_ACTIVE=docker
```

## .gitignore

```
target/
.env
*.log
mysql-data/
uploads/
.DS_Store
.idea/
*.iml
```

## README (minimal)

```markdown
# RegistrarOps Academic Services Portal

## Run
```bash
docker compose up --build
```
Open http://localhost:8080

## Test
```bash
docker compose --profile test run --build test
```

## Stop
```bash
docker compose down
```

## Login
| Role | Username | Password |
|---|---|---|
| Administrator | admin | Admin@Registrar24! |
| Faculty | faculty | Faculty@Reg2024! |
| Reviewer | reviewer | Review@Reg2024! |
| Student | student | Student@Reg24! |
```

## Open Questions & Clarifications (from business prompt only)

[ ] GPA 4.0 scale: 90-100=4.0, 85-89=3.7, 80-84=3.3, 75-79=3.0, 70-74=2.7, 60-69=2.0, <60=0.0 (configurable by Admin)
[ ] "Pinyin support": normalize Chinese characters to their pinyin romanization before matching — uses ICU4J or a local pinyin dictionary map
[ ] Bestsellers ranking: computed from enrollment count + purchase count over last 30 days
[ ] New arrivals: courses/materials created within last 14 days
[ ] Order idempotency: correlationId (UUID) stored on Order; same correlationId within 10 min returns existing order
[ ] Exception status override for refund: Admin can manually set order.exceptionStatus=true to allow refund after 14 days
[ ] Device binding: optional per-workstation; stores IP+user-agent hash in device_bindings table; unusual login = different hash than all registered devices
[ ] Account deletion: generate ZIP with user's data (orders, grades, messages) → downloadable link → set deletedAt = now, hide after 7 days
[ ] Quiet hours: stored as user preference (preferredQuietStart=22, preferredQuietEnd=7); MessageService checks current local hour before delivering
[ ] Evaluation cycle: Faculty opens cycle → enters scores → attaches evidence → submits; Reviewer audits outliers (score outside mean±2σ) → approves or requests adjustment
