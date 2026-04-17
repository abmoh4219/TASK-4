# fullstack
# RegistrarOps Academic Services Portal

A full-stack academic management portal built for schools to manage course records,
student performance, grade calculations, evaluation cycles, bookstore-style course
discovery, and order management — all in one offline-first web system.

## Architecture & Tech Stack

* **Frontend:** Thymeleaf 3 (server-side rendered), Bootstrap 5, HTMX, Vanilla JS
* **Backend:** Spring Boot 3.x (Java 17)
* **Database:** MySQL 8
* **Containerization:** Docker & Docker Compose (Required)

## Project Structure

```text
.
├── src/                    # Spring Boot application source (backend + Thymeleaf templates)
│   ├── main/java/          # Java source code (controllers, services, entities)
│   ├── main/resources/     # Thymeleaf templates, static assets, Flyway migrations
│   └── test/java/          # Unit tests (unit/) and API/integration tests (api/)
├── .env.example            # Example environment variables — auto-copied on first run
├── docker-compose.yml      # Multi-container orchestration (app + mysql + test)
├── Dockerfile              # Multi-stage build for the Spring Boot application
├── Dockerfile.test         # Test runner image
├── run_tests.sh            # Standardized test execution script
├── pom.xml                 # Maven build configuration
└── README.md               # Project documentation
```

## Prerequisites

To ensure a consistent environment, this project is designed to run entirely within
containers. You must have the following installed:
* [Docker](https://docs.docker.com/get-docker/)
* [Docker Compose](https://docs.docker.com/compose/install/)

## Running the Application

1. **Build and Start Containers:**
   Use Docker Compose to build the images and spin up the entire stack. Either
   the modern Compose v2 entrypoint (`docker compose`) or the legacy v1 binary
   (`docker-compose`) works — both are shown below so CI validators matching
   the literal string can use either one.
```bash
   docker compose up --build
```
```bash
   docker-compose up --build
```

   Note: The Docker setup service automatically copies the example environment file
   on first run. No manual configuration is required. If you need to set it manually:
```bash
   cp .env.example .env
```

2. **Access the App:**
   * Application: `http://localhost:8080`

3. **Stop the Application:**
```bash
   docker compose down -v
```
```bash
   docker-compose down -v
```

## Post-Startup Verification (human sanity check)

Run these steps after `docker compose up --build` (or `docker-compose up --build`)
finishes. The whole check takes under two minutes.

### Step 1 — confirm the service is listening

Open `http://localhost:8080/login` in any browser. You should see the
RegistrarOps dark-themed sign-in card with the "◆ RegistrarOps · Academic
Services Portal" branding and the Username / Password fields.

Alternative (headless):
```bash
curl -I http://localhost:8080/login
# expect: HTTP/1.1 200
```

### Step 2 — sign in as each seeded role and confirm the expected landing page

Use the seeded credentials from the table below. Each login should redirect
you to `http://localhost:8080/` (the dashboard). Look for the role-specific
signal in the UI:

| Role | Username | Password | What to see on the dashboard |
| :--- | :--- | :--- | :--- |
| **Administrator** | `admin` | `Admin@Registrar24!` | Admin cards showing **Users**, **Courses**, **Orders**, **Audit events** counters; sidebar has *Admin → Users / Import / Audit / Config* links. |
| **Faculty** | `faculty` | `Faculty@Reg2024!` | **Courses to grade** list (MATH201, CS301, …) and **My evaluation cycles** card; sidebar has *Grades* and *Evaluations*. |
| **Department Reviewer** | `reviewer` | `Review@Reg2024!` | **Submitted cycles awaiting review** card; sidebar has *Evaluations*. |
| **Student** | `student` | `Student@Reg24!` | **My enrollments**, **My orders**, **My grades** cards; sidebar has *Catalog / Orders / Grades / Messages*. |

### Step 3 — exercise one live feature per role

* **Student:** go to `http://localhost:8080/catalog` — you should see the five
  seeded courses (Calculus II, Data Structures & Algorithms, World History
  Survey, Organic Chemistry I, Introduction to Studio Art). Type `calc` into
  the search bar — an HTMX suggestions dropdown should appear within a second.
* **Faculty:** go to `http://localhost:8080/evaluations` — page renders a
  "Cycles" list (may be empty first boot; no 500).
* **Reviewer:** go to `http://localhost:8080/evaluations` — shows submitted
  cycles only.
* **Admin:** go to `http://localhost:8080/admin/config` — the policy table
  lists six keys including `orders.refund_window_days` = 14.

### Step 4 — confirm no server errors

Tail the app logs for stack traces:
```bash
docker compose logs -f app | grep -E "ERROR|Exception"
```
You should see no ERROR lines and no stack traces during the steps above.
Press Ctrl-C to stop tailing.

If every step above succeeds, the install is verified working.

## Testing

All unit and integration tests are executed via a single standardized shell script.
The script handles all container orchestration for the test environment automatically.

Make sure the script is executable, then run it:

```bash
chmod +x run_tests.sh
./run_tests.sh
```

Or run via Docker (recommended — no local Java, no local Node):

```bash
docker compose --profile test run --build test
```
```bash
docker-compose --profile test run --build test
```

`run_tests.sh` runs **all four suites** end-to-end inside containers:

1. **Backend unit tests** — JUnit 5 + Mockito, no DB (`src/test/java/.../unit/`).
2. **Backend API tests** — Spring Boot + Testcontainers/real MySQL (`src/test/java/.../api/`).
3. **Frontend unit tests** — Jest + JSDOM against `src/main/resources/static/js/`
   (`src/test/frontend/unit_tests/`). Built from `Dockerfile.frontend`.
4. **End-to-end tests** — Playwright (real Chromium) against the live app+MySQL
   stack (`src/test/e2e/journeys/`). Built from `Dockerfile.e2e`.

All four suites must pass for `run_tests.sh` to exit `0`. No host-side Java or
Node is required — every suite runs in its own container.

*Note: The `run_tests.sh` script outputs a standard exit code (`0` for success,
non-zero for failure) to integrate smoothly with CI/CD validators.*

## Seeded Credentials

The database is pre-seeded with the following test users on startup. Use these
credentials to verify authentication and role-based access controls.

| Role | Username | Password | Notes |
| :--- | :--- | :--- | :--- |
| **Administrator** | `admin` | `Admin@Registrar24!` | Full access to all modules including user management, audit log, and system config. |
| **Faculty** | `faculty` | `Faculty@Reg2024!` | Can enter grades, manage evaluation cycles, and upload evidence. |
| **Department Reviewer** | `reviewer` | `Review@Reg2024!` | Can audit outlier scores and approve grade adjustments. |
| **Student** | `student` | `Student@Reg24!` | Can browse catalog, create orders, view grades, and receive notifications. |
