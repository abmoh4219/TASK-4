#!/bin/sh
set -e

echo "========================================"
echo "  RegistrarOps Test Suite"
echo "========================================"

# This script is the single entry point for the test suite. It always
# runs the tests inside a Docker container — no Java, no Maven, no Node,
# no PHP, nothing else has to be installed on the host machine. Docker
# is the only required dependency.
#
# When you run ./run_tests.sh on the host:
#   1. We check Docker is installed and reachable.
#   2. We delegate to `docker compose --profile test run --rm --build test`,
#      which builds Dockerfile.test (Maven + JDK 17 image) and runs this
#      same script INSIDE the container.
#   3. Inside the container, we detect /.dockerenv and execute Maven
#      directly against the bundled JDK — that's where the real work
#      happens.
#
# The exit code propagates: 0 if all tests pass, non-zero on any failure.

# ── Inside-container path ───────────────────────────────────────────────────
# Docker creates /.dockerenv inside every container. We use that as the
# unambiguous signal that we are already running inside the test image,
# so we should run Maven directly rather than recursing into Docker.
if [ -f /.dockerenv ]; then
  echo ""
  echo "Running Maven inside the test container (Java 17 from the image)..."
  echo ""

  UNIT_FAILED=0
  API_FAILED=0

  echo "--- Unit Tests (src/test/java/.../unit/) ---"
  mvn test -Dtest='com.registrarops.unit.**' \
    -DfailIfNoTests=false \
    --no-transfer-progress \
    -Dspring.profiles.active=test 2>&1 || UNIT_FAILED=1
  [ $UNIT_FAILED -eq 0 ] && echo "Unit Tests PASSED" || echo "Unit Tests FAILED"

  echo ""
  echo "--- API / Integration Tests (src/test/java/.../api/) ---"
  mvn test -Dtest='com.registrarops.api.**' \
    -DfailIfNoTests=false \
    --no-transfer-progress \
    -Dspring.profiles.active=test 2>&1 || API_FAILED=1
  [ $API_FAILED -eq 0 ] && echo "API Tests PASSED" || echo "API Tests FAILED"

  echo ""
  echo "========================================"
  TOTAL=$((UNIT_FAILED + API_FAILED))
  if [ $TOTAL -eq 0 ]; then
    echo "  ALL TESTS PASSED"
    exit 0
  fi
  echo "  SOME TESTS FAILED"
  echo "  Unit Tests: $([ $UNIT_FAILED -eq 0 ] && echo PASS || echo FAIL)"
  echo "  API Tests:  $([ $API_FAILED -eq 0 ] && echo PASS || echo FAIL)"
  exit 1
fi

# ── Host path: delegate to Docker ───────────────────────────────────────────
if ! command -v docker > /dev/null 2>&1; then
  echo ""
  echo "ERROR: Docker is not installed."
  echo "This project's tests run entirely inside a container — Docker is the"
  echo "only required dependency on the host machine."
  echo ""
  echo "Install Docker: https://docs.docker.com/get-docker/"
  exit 1
fi

if ! docker info > /dev/null 2>&1; then
  echo ""
  echo "ERROR: Docker is installed but the daemon is not reachable."
  echo "Start Docker Desktop (or the Docker service) and try again."
  exit 1
fi

# Compose v2 (`docker compose`) is the modern entry point. Fall back to
# the legacy v1 binary (`docker-compose`) only if v2 is unavailable.
if docker compose version > /dev/null 2>&1; then
  COMPOSE="docker compose"
elif command -v docker-compose > /dev/null 2>&1; then
  COMPOSE="docker-compose"
else
  echo ""
  echo "ERROR: Docker Compose is not installed."
  echo "Install Docker Desktop (which bundles Compose v2):"
  echo "  https://docs.docker.com/compose/install/"
  exit 1
fi

echo ""
echo "Delegating to Docker (no local Java/Maven required)..."
echo ""

# Stay in the directory that holds docker-compose.yml so relative paths
# work even when the script is invoked from another working directory.
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
cd "$SCRIPT_DIR"

# `--profile test` activates the `test` service in docker-compose.yml.
# `run --rm --build` rebuilds the test image (so source changes are picked
# up), runs the container once, and removes it on exit. Inside the
# container, this same script re-enters the inside-container path above
# (via /.dockerenv detection) and runs mvn directly.
# TESTCONTAINERS_RYUK_DISABLED=true skips the Ryuk reaper sidecar, which
# can fail to be reached from inside our test container due to Docker
# bridge networking when the parent runs in a compose-managed network.
# Ryuk's only job is to clean up sibling containers if the parent JVM
# crashes — here `--rm` already removes the test container on exit, so
# the JVM's own MySQLContainer.stop() is enough.
set +e
$COMPOSE --profile test run --rm --build \
  -e TESTCONTAINERS_RYUK_DISABLED=true \
  test
EXIT_CODE=$?
set -e

echo ""
echo "========================================"
if [ $EXIT_CODE -eq 0 ]; then
  echo "  ALL TESTS PASSED"
else
  echo "  SOME TESTS FAILED (exit code $EXIT_CODE)"
fi
echo "========================================"
exit $EXIT_CODE
