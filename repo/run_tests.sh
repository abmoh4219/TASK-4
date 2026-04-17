#!/bin/sh
set -e

echo "========================================"
echo "  RegistrarOps Test Suite"
echo "========================================"

# Single entry point for all four test suites:
#   1. Backend unit tests      (JUnit + Mockito, no DB)
#   2. Backend API tests       (Spring Boot + Testcontainers / real MySQL)
#   3. Frontend unit tests     (Jest + JSDOM, static/js/*.js)
#   4. Frontend end-to-end     (Playwright against the running app+MySQL)
#
# Everything runs inside Docker — the host only needs Docker + Compose.

# ── Inside-container path (backend suites only) ────────────────────────────
# Docker creates /.dockerenv inside every container. The `test` service in
# compose sets SUITE=backend and this branch runs Maven there.
if [ -f /.dockerenv ] && [ "${SUITE:-backend}" = "backend" ]; then
  echo ""
  echo "Running Maven inside the backend test container..."
  echo ""

  UNIT_FAILED=0
  API_FAILED=0

  echo "--- 1. Backend Unit Tests (src/test/java/.../unit/) ---"
  mvn test -Dtest='com.registrarops.unit.**' \
    -DfailIfNoTests=false \
    --no-transfer-progress \
    -Dspring.profiles.active=test 2>&1 || UNIT_FAILED=1
  [ $UNIT_FAILED -eq 0 ] && echo "Unit Tests PASSED" || echo "Unit Tests FAILED"

  echo ""
  echo "--- 2. Backend API / Integration Tests (src/test/java/.../api/) ---"
  mvn test -Dtest='com.registrarops.api.**' \
    -DfailIfNoTests=false \
    --no-transfer-progress \
    -Dspring.profiles.active=test 2>&1 || API_FAILED=1
  [ $API_FAILED -eq 0 ] && echo "API Tests PASSED" || echo "API Tests FAILED"

  TOTAL=$((UNIT_FAILED + API_FAILED))
  if [ $TOTAL -eq 0 ]; then
    echo ""
    echo "Backend suites PASSED inside container."
    exit 0
  fi
  echo ""
  echo "Backend suites FAILED inside container."
  echo "  Unit Tests: $([ $UNIT_FAILED -eq 0 ] && echo PASS || echo FAIL)"
  echo "  API Tests:  $([ $API_FAILED -eq 0 ] && echo PASS || echo FAIL)"
  exit 1
fi

# ── Host path: orchestrate all four suites via Docker Compose ──────────────
if ! command -v docker > /dev/null 2>&1; then
  echo ""
  echo "ERROR: Docker is not installed."
  echo "This project's tests run entirely inside containers — Docker is the"
  echo "only required dependency on the host machine."
  exit 1
fi

if ! docker info > /dev/null 2>&1; then
  echo ""
  echo "ERROR: Docker daemon is not reachable. Start Docker and try again."
  exit 1
fi

if docker compose version > /dev/null 2>&1; then
  COMPOSE="docker compose"
elif command -v docker-compose > /dev/null 2>&1; then
  COMPOSE="docker-compose"
else
  echo ""
  echo "ERROR: Docker Compose is not installed."
  exit 1
fi

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
cd "$SCRIPT_DIR"

BACKEND_EXIT=0
FRONTEND_EXIT=0
E2E_EXIT=0

echo ""
echo "========================================"
echo "  1/4 + 2/4: Backend unit + API tests"
echo "========================================"
set +e
$COMPOSE --profile test run --rm --build \
  -e TESTCONTAINERS_RYUK_DISABLED=true \
  test
BACKEND_EXIT=$?
set -e

echo ""
echo "========================================"
echo "  3/4: Frontend unit tests (Jest + JSDOM)"
echo "========================================"
set +e
$COMPOSE --profile frontend run --rm --build frontend-test
FRONTEND_EXIT=$?
set -e

echo ""
echo "========================================"
echo "  4/4: End-to-end tests (Playwright)"
echo "========================================"
# E2E needs the live app + MySQL running on the compose network. Bring them
# up, wait for health, then run the e2e service, then tear down.
set +e
$COMPOSE up -d --build app mysql
$COMPOSE --profile e2e run --rm --build e2e
E2E_EXIT=$?
$COMPOSE down
set -e

echo ""
echo "========================================"
TOTAL=$((BACKEND_EXIT + FRONTEND_EXIT + E2E_EXIT))
echo "  Backend (unit+API): $([ $BACKEND_EXIT -eq 0 ] && echo PASS || echo FAIL)"
echo "  Frontend unit:      $([ $FRONTEND_EXIT -eq 0 ] && echo PASS || echo FAIL)"
echo "  Frontend e2e:       $([ $E2E_EXIT -eq 0 ] && echo PASS || echo FAIL)"
echo "========================================"
if [ $TOTAL -eq 0 ]; then
  echo "  ALL FOUR SUITES PASSED"
  exit 0
fi
echo "  ONE OR MORE SUITES FAILED"
exit 1
