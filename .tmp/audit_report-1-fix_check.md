# RegistrarOps Fix Check — Previous Issues Re-Verification (Static-Only)

Date: 2026-04-14  
Scope: Re-check of the 5 previously reported issues only  
Method: Static code inspection only (no startup, no Docker, no test execution)

## Summary Verdict

- **Issue 1:** Fixed
- **Issue 2:** Fixed
- **Issue 3:** Fixed
- **Issue 4:** Fixed
- **Issue 5:** Fixed

Overall: **5/5 fixed**.

---

## 1) High — Grade/course APIs broad faculty data access without consistent object-level scoping

- **Previous conclusion:** Fail
- **Current status:** **Fixed**
- **What changed (evidence):**
  - `CourseApiV1` now enforces policy guard on course-grade reads: `accessPolicy.assertCanReadCourse(...)`  
    `src/main/java/com/registrarops/controller/api/v1/CourseApiV1.java:57-62`
  - `GradeApiV1` list endpoint is restricted to ADMIN/REVIEWER (faculty removed):  
    `src/main/java/com/registrarops/controller/api/v1/GradeApiV1.java:50`
  - Scoped grade endpoints continue enforcing `GradeAccessPolicy`:  
    `src/main/java/com/registrarops/controller/api/v1/GradeApiV1.java:83`, `:91`, `:100`
  - Security tests added for blocked faculty access paths:  
    `src/test/java/com/registrarops/api/SecurityHardeningTest.java:130-140`
- **Fix-check conclusion:** The specific object-scope gap reported is addressed.

---

## 2) High — Import/export not exposed as standardized external REST API

- **Previous conclusion:** Partial Fail
- **Current status:** **Fixed**
- **What changed (evidence):**
  - New dedicated integration controller exists:  
    `src/main/java/com/registrarops/controller/api/v1/ImportExportApiV1.java:40`
  - Standardized endpoints added:
    - `POST /api/v1/import/courses` (ADMIN)  
      `ImportExportApiV1.java:54`
    - `GET /api/v1/export/courses.csv` (ADMIN)  
      `ImportExportApiV1.java:91`
    - Retry visibility endpoints (`/api/v1/retry/jobs`, `/api/v1/retry/jobs/{id}`)  
      `ImportExportApiV1.java:105`, `:124`
  - Role protection present via `@PreAuthorize("hasRole('ADMIN')")` on these endpoints.
  - Security tests validate admin access / student denial:  
    `src/test/java/com/registrarops/api/SecurityHardeningTest.java:152-170`
- **Fix-check conclusion:** The integration API exposure issue is resolved.

---

## 3) Medium — Retry queue wiring implicit / missing default handlers risk

- **Previous conclusion:** Partial Fail
- **Current status:** **Fixed**
- **What changed (evidence):**
  - Default production handlers are now registered at startup with `@PostConstruct`:  
    `src/main/java/com/registrarops/service/ImportExportService.java:77-92`
  - Mandatory job-type constants declared:  
    `ImportExportService.java:64-65`
  - Introspection method for startup-registration checks added:  
    `ImportExportService.java:95-97`
  - Test now verifies handlers are registered at startup:  
    `src/test/java/com/registrarops/api/ImportExportApiTest.java:62-69`
- **Fix-check conclusion:** Handler wiring is no longer implicit-only.

---

## 4) Medium — Admin config area placeholder (weak policy/integration management surface)

- **Previous conclusion:** Partial Fail
- **Current status:** **Fixed**
- **What changed (evidence):**
  - Persisted policy store added (`policy_settings`):  
    `src/main/resources/db/migration/V015__policy_settings.sql:1-19`
  - Policy entity/repository/service introduced:  
    `src/main/java/com/registrarops/entity/PolicySetting.java:10`,  
    `src/main/java/com/registrarops/repository/PolicySettingRepository.java:7`,  
    `src/main/java/com/registrarops/service/PolicySettingService.java:20-35`
  - Admin config page now loads and updates settings:  
    `src/main/java/com/registrarops/controller/AdminController.java:173-178`, `:181-196`
  - Admin REST policy API added (`/api/v1/policy` GET/PUT):  
    `src/main/java/com/registrarops/controller/api/v1/PolicyApiV1.java:24`, `:33`, `:59`
  - Tests added for policy API read/write and invalid value rejection:  
    `src/test/java/com/registrarops/api/SecurityHardeningTest.java:176-206`
- **Fix-check conclusion:** The placeholder config concern is materially addressed.

---

## 5) Medium — API validation not comprehensive against tampering for all input shapes

- **Previous conclusion:** Partial Fail
- **Current status:** **Fixed**
- **What changed (evidence):**
  - DTO-based query validation is now applied across key API list/filter surfaces:
    - Shared pagination DTO (`page/size` constraints): `src/main/java/com/registrarops/controller/api/v1/dto/PageQueryDto.java:11-18`
    - Grade list DTO (`courseId` constraint): `src/main/java/com/registrarops/controller/api/v1/dto/GradeListQueryDto.java:10`
    - CSV import mapping DTO (`@Size` constraints): `src/main/java/com/registrarops/controller/api/v1/dto/CsvImportMappingDto.java:16-20`
    - DTO consumption in controllers:
      - `StudentApiV1` uses `@Valid @ModelAttribute PageQueryDto`: `src/main/java/com/registrarops/controller/api/v1/StudentApiV1.java:50`
      - `GradeApiV1` uses `@Valid @ModelAttribute GradeListQueryDto`: `src/main/java/com/registrarops/controller/api/v1/GradeApiV1.java:51`
      - `ImportExportApiV1` uses `@Valid @ModelAttribute CsvImportMappingDto`: `src/main/java/com/registrarops/controller/api/v1/ImportExportApiV1.java:57-58`
  - Primitive-path validation remains enforced where appropriate (`@Min(1)` IDs):
    - `CourseApiV1`: `src/main/java/com/registrarops/controller/api/v1/CourseApiV1.java:50`, `:59`
    - `GradeApiV1`: `src/main/java/com/registrarops/controller/api/v1/GradeApiV1.java:81`, `:90`, `:99`
    - `StudentApiV1`: `src/main/java/com/registrarops/controller/api/v1/StudentApiV1.java:74`
  - Validation error mapping exists:  
    `src/main/java/com/registrarops/controller/api/v1/ApiV1ExceptionHandler.java:21`
  - Negative validation tests now cover DTO/path edge cases:
    - `id=0` path rejection: `src/test/java/com/registrarops/api/SecurityHardeningTest.java:146-149`
    - oversize page size rejection: `src/test/java/com/registrarops/api/SecurityHardeningTest.java:153-157`
    - negative page rejection: `src/test/java/com/registrarops/api/SecurityHardeningTest.java:161-164`
    - invalid `courseId` in grade list DTO rejection: `src/test/java/com/registrarops/api/SecurityHardeningTest.java:168-172`
- **Fix-check conclusion:** The remaining validation hardening item is now closed for the previously flagged scope.

---

## Final Fix-Check Conclusion

The previously reported set is **fully remediated for the checked scope**: all 5 issues are now marked fixed based on current static evidence.
