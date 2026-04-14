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
[ $UNIT_FAILED -eq 0 ] && echo "Unit Tests PASSED" || echo "Unit Tests FAILED"

echo ""
echo "--- API / Integration Tests (src/test/java/.../api/) ---"
./mvnw test -Dtest="com.registrarops.api.*" \
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
