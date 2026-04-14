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
   Use Docker Compose to build the images and spin up the entire stack.
```bash
   docker compose up --build
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

## Testing

All unit and integration tests are executed via a single standardized shell script.
The script handles all container orchestration for the test environment automatically.

Make sure the script is executable, then run it:

```bash
chmod +x run_tests.sh
./run_tests.sh
```

Or run via Docker (recommended — no local Java required):

```bash
docker compose --profile test run --build test
```

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
