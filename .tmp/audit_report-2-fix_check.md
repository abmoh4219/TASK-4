# audit_report-2 Fix Check (Static-Only)

Date: 2026-04-14  
Scope: `/home/abdelah/Documents/eaglepoint/TASK-4-W2/repo`  
Method: Static code inspection only (no runtime execution, no tests run)

## Overall Result

- **5 / 6 issues appear fixed by static evidence**
- **1 / 6 is partially fixed / cannot fully confirm statically**

---

## Issue-by-Issue Verification

### 1) High — Account export download coupled to authenticated principal after soft-delete

**Previous status:** Fail  
**Fix check conclusion:** **Fixed (static evidence)**

**Why:**

- Export endpoint is now explicitly unauthenticated and token-validated:
  - `src/main/java/com/registrarops/controller/AuthController.java:98`
  - `src/main/java/com/registrarops/controller/AuthController.java:100`
- Security config now permits `/account/export/**`:
  - `src/main/java/com/registrarops/config/SecurityConfig.java:77`
- Deletion flow now issues HMAC-signed token and logs user out immediately:
  - `src/main/java/com/registrarops/service/AccountDeletionService.java:136`
  - `src/main/java/com/registrarops/controller/AuthController.java:79`
- New token service added with expiry + signature verification:
  - `src/main/java/com/registrarops/service/ExportTokenService.java:23`
  - `src/main/java/com/registrarops/service/ExportTokenService.java:44`
- Regression test added for unauthenticated token-based download:
  - `src/test/java/com/registrarops/api/Cycle2RemediationTest.java:47`

---

### 2) High — Policy settings persisted but not authoritative at runtime

**Previous status:** Fail  
**Fix check conclusion:** **Fixed (static evidence)**

**Why:**

- `OrderService` now reads authoritative policy values through `PolicySettingService.getInt(...)`:
  - `src/main/java/com/registrarops/service/OrderService.java:100`
  - `src/main/java/com/registrarops/service/OrderService.java:104`
  - `src/main/java/com/registrarops/service/OrderService.java:108`
- `MessageService` now reads quiet-hour policy values via policy service:
  - `src/main/java/com/registrarops/service/MessageService.java:84`
  - `src/main/java/com/registrarops/service/MessageService.java:85`
- `ImportExportService` now reads retry max attempts from policy service:
  - `src/main/java/com/registrarops/service/ImportExportService.java:142`
  - `src/main/java/com/registrarops/service/ImportExportService.java:309`
- Policy key naming is now consistent with migration seed values:
  - `src/main/resources/db/migration/V015__policy_settings.sql:14`
  - `src/main/resources/db/migration/V015__policy_settings.sql:17`
- Remediation tests added:
  - `src/test/java/com/registrarops/api/Cycle2RemediationTest.java:75`
  - `src/test/java/com/registrarops/api/Cycle2RemediationTest.java:100`

---

### 3) High — Failed import rows not queued for retry

**Previous status:** Partial Fail  
**Fix check conclusion:** **Fixed (static evidence)**

**Why:**

- Failed-row imports now persist artifact and enqueue retry job:
  - `src/main/java/com/registrarops/service/ImportExportService.java:237`
  - `src/main/java/com/registrarops/service/ImportExportService.java:238`
- New retry job type and handler for import replay:
  - `src/main/java/com/registrarops/service/ImportExportService.java:71`
  - `src/main/java/com/registrarops/service/ImportExportService.java:99`
  - `src/main/java/com/registrarops/service/ImportExportService.java:252`
- Retry max attempts now policy-driven at scheduling:
  - `src/main/java/com/registrarops/service/ImportExportService.java:309`
- Remediation test asserts retry job is enqueued on failed import:
  - `src/test/java/com/registrarops/api/Cycle2RemediationTest.java:117`
  - `src/test/java/com/registrarops/api/Cycle2RemediationTest.java:131`

---

### 4) Medium — REST integration auth model not machine-to-machine friendly

**Previous status:** Cannot Confirm Statistically / Suspected Risk  
**Fix check conclusion:** **Partially Fixed / Cannot Confirm Completely Statistically**

**Why (improved):**

- New API key filter introduced for `/api/v1/import/**` and `/api/v1/export/**`:
  - `src/main/java/com/registrarops/security/ApiKeyAuthFilter.java:18`
  - `src/main/java/com/registrarops/security/ApiKeyAuthFilter.java:44`
- Security chain now installs filter and exempts import/export paths from CSRF:
  - `src/main/java/com/registrarops/config/SecurityConfig.java:56`
  - `src/main/java/com/registrarops/config/SecurityConfig.java:69`

**Residual limitation (static):**

- Key defaults to empty unless configured; behavior under deployment config cannot be proven statically:
  - `src/main/java/com/registrarops/security/ApiKeyAuthFilter.java:37`
- Added tests verify unauthenticated denial and admin-session success, but do not explicitly prove X-API-Key positive path in this file:
  - `src/test/java/com/registrarops/api/Cycle2RemediationTest.java:140`
  - `src/test/java/com/registrarops/api/Cycle2RemediationTest.java:148`

---

### 5) Medium — Reminder/exception notification paths not clearly implemented

**Previous status:** Partial Fail  
**Fix check conclusion:** **Fixed (static evidence)**

**Why:**

- Payment reminder scheduled flow added:
  - `src/main/java/com/registrarops/service/OrderService.java:317`
  - `src/main/java/com/registrarops/service/OrderService.java:328`
- Exception notification flow added:
  - `src/main/java/com/registrarops/service/OrderService.java:344`
  - `src/main/java/com/registrarops/service/OrderService.java:351`

**Note:** Invocation coverage of `markException(...)` from controller/API surface is not evident in inspected controller paths; implementation exists at service layer.

---

### 6) Low — Comment/config mismatch around CSRF for `/api/v1/**`

**Previous status:** Fail  
**Fix check conclusion:** **Fixed (static evidence)**

**Why:**

- `StudentApiV1` comment now aligns with security behavior: browser-session CSRF enforced generally, import/export exempted and API-key authenticated:
  - `src/main/java/com/registrarops/controller/api/v1/StudentApiV1.java:30`
- `SecurityConfig` comments + config reflect the same behavior:
  - `src/main/java/com/registrarops/config/SecurityConfig.java:62`
  - `src/main/java/com/registrarops/config/SecurityConfig.java:69`

---

## Final Summary

- **Fixed:** #1, #2, #3, #5, #6
- **Partially fixed / cannot fully confirm statically:** #4

Given the static-only boundary, the remaining uncertainty is operational configuration/activation of API-key auth in target deployment (not code presence).
