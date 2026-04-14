# RegistrarOps — API Specification

Generated from the actual implementation under `repo/src/main/java/com/registrarops/controller/`.

All routes share the following baseline:

| | |
|---|---|
| Base URL | `http://localhost:8080` |
| Auth | Spring Session cookie (`JSESSIONID`) — log in via `POST /login` |
| CSRF | Required for every mutating request (POST/PUT/DELETE) on **non**-`/api/v1/**` paths. Token sent as form field `_csrf` OR header `X-XSRF-TOKEN` (read from `XSRF-TOKEN` cookie or the `<meta name="_csrf">` tag) |
| Role check | `@PreAuthorize` on the REST API methods, URL-rule-based on the page controllers |
| Error format | Spring Boot default — `{ "timestamp", "status", "error", "message", "path" }` for JSON; HTML error page for browser routes |

---

## 1. Authentication

| Method | Path | Auth | CSRF | Returns |
|---|---|---|---|---|
| GET  | `/login`                | public         | — | login page |
| POST | `/login`                | public         | yes | 302 → `/` on success, 302 → `/login?error` or `/login?locked` on failure |
| POST | `/logout`               | authenticated  | yes | 302 → `/login?logout`, invalidates session |
| GET  | `/profile`              | authenticated  | — | user profile page |
| GET  | `/account/delete`       | authenticated  | — | confirmation page |
| POST | `/account/delete`       | authenticated  | yes | exports user data, soft-deletes, redirects to download |
| GET  | `/account/export/{token}` | authenticated | — | downloads the JSON export file |

**Lockout policy**: 5 failed `POST /login` attempts in 15 minutes locks the
account. The next attempt redirects to `/login?locked` even if the password
is correct. Counter is cleared on a successful login.

---

## 2. Dashboard

| Method | Path | Auth | Renders |
|---|---|---|---|
| GET | `/` | authenticated | `dashboard/index.html` — content varies per role via `sec:authorize` blocks |

---

## 3. Catalog

| Method | Path | Auth | CSRF | Notes |
|---|---|---|---|---|
| GET  | `/catalog`                       | STUDENT, ADMIN | — | search bar, filters, results grid, fallback recommendations |
| GET  | `/catalog/detail/{type}/{id}`    | STUDENT, ADMIN | — | item detail with rating form |
| POST | `/catalog/rate`                  | STUDENT, ADMIN | yes | params: `itemType`, `itemId`, `score` (1-5) |
| GET  | `/api/search/suggestions`        | authenticated  | — | HTMX endpoint, returns `fragments/search-suggestions :: dropdown` partial; query param `q` |

---

## 4. Orders

| Method | Path | Auth | CSRF | Notes |
|---|---|---|---|---|
| GET  | `/orders`                  | STUDENT, ADMIN | — | list of student's orders |
| GET  | `/orders/{id}`             | STUDENT (own), ADMIN | — | order detail with timeline + 30-min countdown |
| GET  | `/orders/checkout`         | STUDENT, ADMIN | — | params: `type`, `id` — renders checkout form with generated `correlationId` |
| POST | `/orders/create`           | STUDENT, ADMIN | yes | params: `itemType`, `itemId`, `correlationId`. Idempotent within 10 min on `correlationId` |
| POST | `/orders/{id}/pay`         | STUDENT, ADMIN | yes | PAYING → PAID |
| POST | `/orders/{id}/cancel`      | STUDENT, ADMIN | yes | CREATED/PAYING → CANCELED |
| POST | `/orders/{id}/refund`      | STUDENT, ADMIN | yes | PAID → REFUNDED. Rejected if `paidAt > 14 days ago` and `exception_status` is false |

State transitions:
```
CREATED → PAYING → PAID → REFUNDED
   ↓        ↓
CANCELED  CANCELED   (also via @Scheduled cancelExpiredOrders after 30 min)
```

Any other transition raises `OrderStateException` (HTTP 500 → flash error
on redirect).

---

## 5. Grades

| Method | Path | Auth | CSRF | Notes |
|---|---|---|---|---|
| GET  | `/grades`                          | STUDENT (→`/grades/report`), FACULTY, REVIEWER, ADMIN | — | course list to grade |
| GET  | `/grades/report`                   | STUDENT, FACULTY, ADMIN | — | student's report card with cumulative GPA |
| GET  | `/grades/{courseId}/entry`         | FACULTY, ADMIN | — | grade entry form |
| POST | `/grades/{courseId}/components`    | FACULTY, ADMIN | yes | params: `studentId`, `componentName`, `score`, `maxScore?`, `attemptNumber?`. Auto-recalculates affected student's `StudentGrade`. |
| POST | `/grades/{courseId}/recalculate`   | FACULTY, ADMIN | yes | recalculates every enrolled student's grade against the active rule |

---

## 6. Evaluation cycles

| Method | Path | Auth | CSRF | Notes |
|---|---|---|---|---|
| GET  | `/evaluations`                  | FACULTY, REVIEWER, ADMIN | — | reviewer sees SUBMITTED cycles; faculty sees own cycles |
| POST | `/evaluations/create`           | FACULTY, ADMIN | yes | params: `courseId`, `title` |
| GET  | `/evaluations/{cycleId}`        | FACULTY, ADMIN | — | cycle detail (indicators + evidence) |
| POST | `/evaluations/{cycleId}/indicators` | FACULTY, ADMIN | yes | params: `indicatorName`, `weight`, `score` |
| POST | `/evaluations/{cycleId}/open`   | FACULTY, ADMIN | yes | DRAFT → OPEN |
| POST | `/evaluations/{cycleId}/submit` | FACULTY, ADMIN | yes | OPEN → SUBMITTED; runs outlier detection |
| POST | `/evaluations/{cycleId}/evidence` | FACULTY, ADMIN | yes | multipart `file`. Validates MIME (PDF/JPG/PNG/DOCX) + size (≤10 MB), SHA-256 hash, stores under `${registrarops.upload-dir}` |
| GET  | `/evaluations/{cycleId}/review` | REVIEWER, ADMIN | — | reviewer audit page with outlier highlights |
| POST | `/evaluations/{cycleId}/approve` | REVIEWER, ADMIN | yes | params: `comment?`. SUBMITTED → CLOSED |

---

## 7. Messages & notifications

| Method | Path | Auth | CSRF | Notes |
|---|---|---|---|---|
| GET  | `/messages`                       | authenticated | — | notification center |
| POST | `/messages/mark-read`             | authenticated | yes | mark all delivered messages as read |
| POST | `/messages/preferences/mute`      | authenticated | yes | params: `category`. Adds to `muted_categories` (idempotent) |
| POST | `/messages/preferences/quiet-hours` | authenticated | yes | params: `startHour` (0-23), `endHour` (0-23) |
| GET  | `/api/notifications/count`        | authenticated | — | plain text unread count (HTMX-polled every 30 s) |
| GET  | `/api/notifications/list`         | authenticated | — | Thymeleaf fragment `fragments/notification-badge :: list` |
| POST | `/api/notifications/mark-read`    | authenticated | yes | mark all read |

**Quiet hours**: messages whose `deliver_at` is in the future are not
counted as unread until that time arrives. Wraps midnight (e.g. 22:00–07:00
is quiet at 22, 23, 00, 01, ..., 06 but not at 07 or 21).

**Duplicate dedup**: any message with the same
`(recipient, category, related_type, related_id)` within the last 1 hour
threads into the existing row instead of inserting a new one
(`thread_count` is incremented, `thread_key` is set).

---

## 8. Admin

| Method | Path | Auth | CSRF | Notes |
|---|---|---|---|---|
| GET  | `/admin`                          | ADMIN | — | dashboard with user/course/order/audit counts |
| GET  | `/admin/users`                    | ADMIN | — | user list + create form |
| POST | `/admin/users`                    | ADMIN | yes | params: `username`, `password`, `role`, `email?`, `fullName?`. Password validated against `PasswordComplexityValidator` |
| POST | `/admin/users/{id}/deactivate`    | ADMIN | yes | sets `is_active = false` |
| GET  | `/admin/import`                   | ADMIN | — | CSV upload page |
| POST | `/admin/import/csv`               | ADMIN | yes | multipart `file` (CSV with header `code,title,credits,price,category`). Returns rendered import result (imported, skipped, per-row errors) |
| GET  | `/admin/audit`                    | ADMIN | — | paginated audit log viewer (50/page, descending). **Read-only — no POST or DELETE on this resource anywhere in the codebase** |
| GET  | `/admin/config`                   | ADMIN | — | system config view (GPA scale, refund window, lockout policy etc.) |

---

## 9. External integration REST API (`/api/v1/**`)

These endpoints return JSON via Jackson, do **not** require CSRF (excluded
in `SecurityConfig.csrf().ignoringRequestMatchers("/api/v1/**")`), and use
`@PreAuthorize` for role enforcement.

### Students

| Method | Path | Auth | Returns |
|---|---|---|---|
| GET | `/api/v1/students` | ADMIN, REVIEWER | paginated `{ page, size, total, items[] }` |
| GET | `/api/v1/students/{id}/grades` | ADMIN, REVIEWER, FACULTY | array of grade objects |

`GET /api/v1/students` example:
```bash
curl -b cj.txt http://localhost:8080/api/v1/students?page=0&size=20
```
```json
{
  "page": 0,
  "size": 20,
  "total": 4,
  "items": [
    {
      "id": 4,
      "username": "student",
      "fullName": "Aiko Tanaka",
      "role": "ROLE_STUDENT",
      "isActive": true
    }
  ]
}
```

`GET /api/v1/students/{id}/grades` example:
```json
[
  {
    "courseId": 1,
    "ruleVersionId": 1,
    "weightedScore": 83.00,
    "letterGrade": "B+",
    "gpaPoints": 3.30,
    "credits": 4.00,
    "calculatedAt": "2026-04-14T12:30:15.123"
  }
]
```

### Courses

| Method | Path | Auth | Returns |
|---|---|---|---|
| GET | `/api/v1/courses`             | authenticated | array of course objects |
| GET | `/api/v1/courses/{id}`        | authenticated | single course object |
| GET | `/api/v1/courses/{id}/grades` | FACULTY, ADMIN | array of student grades for that course |

`GET /api/v1/courses` example:
```json
[
  {
    "id": 1,
    "code": "MATH201",
    "title": "Calculus II",
    "category": "Mathematics",
    "credits": 4.00,
    "price": 0.00,
    "ratingAvg": 4.50,
    "isActive": true
  }
]
```

### Reports

| Method | Path | Auth | Returns |
|---|---|---|---|
| GET | `/api/v1/reports/gpa-summary` | ADMIN | array of cumulative-GPA summaries (one per student) |

`GET /api/v1/reports/gpa-summary` example:
```json
[
  {
    "studentId": 4,
    "username": "student",
    "cumulativeGpa": 3.30,
    "totalCredits": 4.00,
    "courseCount": 1
  }
]
```

---

## 10. Error responses

### HTML routes
Spring Boot's default error page or a flash-attribute redirect with
`flashError` rendered in the layout's red alert banner.

### `/api/v1/**`
Spring Boot default JSON error envelope:
```json
{
  "timestamp": "2026-04-14T11:16:18.704+00:00",
  "status": 403,
  "error": "Forbidden",
  "message": "Forbidden",
  "path": "/api/v1/students"
}
```

| Status | When |
|---|---|
| 200 | success |
| 302 | redirect after a successful form POST |
| 400 | invalid input (missing param, bad enum, BigDecimal parse failure, file too large, disallowed MIME) |
| 401 | unauthenticated request to a protected URL — Spring Security redirects to `/login` for browser, returns 401 for REST |
| 403 | authenticated but role / CSRF check fails |
| 404 | unknown order, evaluation cycle, course, etc. |
| 500 | unhandled internal exception |

---

## 11. Rate limiting / throttling

Not implemented. The unified search bar uses a 300 ms client-side debounce
in `static/js/search.js` to prevent excessive HTMX requests; there is no
server-side limiting.

## 12. Versioning

The integration REST API is versioned at the URL prefix (`/api/v1/`).
HTMX endpoints under `/api/notifications/` and `/api/search/` are
internal-only and not versioned.
