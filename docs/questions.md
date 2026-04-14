# questions.md — RegistrarOps Academic Services Portal
# Format: Question → My Understanding → Solution Implemented

---

## 1. What GPA Scale Is Used for the 4.0 Conversion?

**Question:** The prompt requires "GPA conversion on a 4.0 scale" but does not specify the exact grade-to-GPA mapping. Different institutions use slightly different cutoffs.

**My Understanding:** A standard US academic GPA scale is the most natural interpretation for a school-focused system. The prompt does not specify plus/minus variants, so a clean band-based scale is appropriate. The scale should be configurable by Administrator since the prompt says policies are managed by Admin.

**Solution Implemented:** Default scale stored in system config: 90-100 → 4.0, 85-89 → 3.7, 80-84 → 3.3, 75-79 → 3.0, 70-74 → 2.7, 60-69 → 2.0, below 60 → 0.0. Administrators can adjust band thresholds via the System Config page. GradeEngineService loads the current config on each calculation. The GPA scale version is stored alongside the StudentGrade record for backtracking.

---

## 2. What Does "Pinyin/Synonym Support" Mean for Search?

**Question:** The prompt says the search bar supports "did you mean typo correction (including pinyin/synonym support for names and titles)." This implies Chinese character to pinyin romanization support, but the scope is unclear.

**My Understanding:** The system should handle searches where users type pinyin romanization (e.g., "zhangsan") to find records with Chinese characters in the name, and vice versa. This is common in Chinese academic institutions. A local mapping dictionary (not a cloud service) is required since the system is fully offline.

**Solution Implemented:** SearchService maintains a local pinyin dictionary (a Java Map<String, String> loaded from a bundled JSON resource file) mapping common Chinese characters and compound names to their pinyin romanization. Before matching, both the query and stored titles are normalized: Chinese characters → pinyin, then Levenshtein distance is applied. "Synonym support" is implemented as a configurable synonym table in the database (admin-managed), where searching "Math" also searches "Mathematics" and "Maths."

---

## 3. How Are "Bestsellers" and "New Arrivals" Rankings Computed?

**Question:** The prompt mentions "bestsellers/new-release rankings" as a sorting option. The prompt does not define the time window or the metric for "bestseller."

**My Understanding:** "Bestseller" is a sales/enrollment volume metric. "New arrivals" is a recency metric. Both should be computed from real data in the system rather than being hardcoded.

**Solution Implemented:** Bestsellers = top items ranked by (enrollment_count + order_count) over the last 30 calendar days, recalculated nightly by a @Scheduled task and cached in a catalog_rankings table. New arrivals = courses and materials where created_at is within the last 14 days. Both are filterable sorting options in CatalogService. The 30-day window and 14-day window are configurable by Administrator.

---

## 4. What Constitutes a "Duplicate Message" for Threading?

**Question:** The prompt says "consolidate duplicate messages into a single threaded entry." The definition of "duplicate" is not specified.

**My Understanding:** Two messages are considered duplicates if they have the same category (e.g., ORDER_STATUS), the same related entity (e.g., the same order ID), and were sent within a short time window. The intent is to prevent the notification center from being flooded with repeated status-change notifications for the same order.

**Solution Implemented:** MessageService considers a message a duplicate if: same recipient_id + same category + same related_id + sent within the last 1 hour. If a duplicate is detected, the existing message is updated to show a "thread" count (e.g., "3 updates") and the new content is appended to the message body rather than creating a new entry. A thread_key column links related messages together for the threaded view in the notification center.

---

## 5. What Is "Device Binding" and How Optional Is It?

**Question:** The prompt says "optional device binding per workstation." It is unclear what information constitutes a "device," how users opt in, and what "unusual login" means technically.

**My Understanding:** Device binding is an optional security feature where a user registers their usual workstation. An "unusual login" occurs when they log in from an unrecognized device. This is entirely local (no SMS, no email — the system is offline-first).

**Solution Implemented:** Device fingerprint = SHA-256 hash of (IP address + User-Agent string). On first login, users are prompted (not forced) to "Trust this device." If they accept, the fingerprint is stored in device_bindings. On subsequent logins: if fingerprint does not match any bound device AND the user has at least one bound device → create an in-app security notice message saying "New login from an unrecognized device at [timestamp]." Users can manage bound devices from their profile page.

---

## 6. What Exactly Triggers the 7-Day Soft Delete Window?

**Question:** The prompt says "account deletion triggers a local data export to a downloadable file and a 7-day soft-delete window." It is unclear whether the user can cancel during the 7 days, or what happens to data after the window expires.

**My Understanding:** The 7-day window is a grace period during which the account is disabled (cannot log in) but data is preserved. The user (or an admin) can restore the account within 7 days. After 7 days, the account is fully deactivated and PII is anonymized (not deleted, because audit trails must be retained).

**Solution Implemented:** AccountDeletionService.initiateDelete(userId): (1) generate data export ZIP containing user's orders, grades, and messages → save to /app/exports/user_{id}_{token}.zip → send in-app message with download link token; (2) set user.deletedAt = NOW(), user.isActive = false. A Quartz job runs daily: find users where deletedAt < NOW()-7days AND isActive=false → anonymize: set full_name="Deleted User", email="deleted_{id}@removed", password_hash="DELETED" → set isActive remains false. Audit logs referencing the user_id are preserved indefinitely with the anonymized username.

---

## 7. How Does "Backtracking Recalculation" Work When Rules Change?

**Question:** The prompt says grade rules support "backtracking recalculation when policies change." This means changing a grade rule must retroactively update grades for existing students. The question is what is preserved and what changes.

**My Understanding:** When an administrator updates grade weights (e.g., changes final exam from 50% to 60%), the system should recalculate grades for all enrolled students using the new weights, but preserve a history of what grades were under the old rules. This is the "versioning" and "backtracking" requirement.

**Solution Implemented:** GradeRule has a version integer that increments on each change. The old rule is kept in grade_rule_history with a snapshot of the old weights. When new weights are saved, GradeEngineService.recalculateAll(courseId, newRuleVersionId) is called asynchronously: for each enrolled student, it computes the new weighted grade and upserts StudentGrade with the new ruleVersionId. Old StudentGrade records with the previous ruleVersionId are kept (not deleted) for audit and comparison. The grade report shows the current grade prominently and a "rule version history" expandable section showing all past calculations.

---

## 8. What Are "Exception Requests" in the Order Context?

**Question:** The prompt mentions "no refunds after 14 days from purchase unless the order is in exception status." The prompt does not define what creates an "exception status" or who grants it.

**My Understanding:** Exception status is an administrative override for unusual circumstances (e.g., a faculty error, a system issue, or a documented special case). Only an Administrator should be able to grant exception status to an order, enabling a refund outside the normal 14-day window.

**Solution Implemented:** Order entity has an exception_status boolean (default false). Only ROLE_ADMIN can set it via POST /admin/orders/{id}/exception. When exception_status=true, OrderService.refundOrder() skips the 14-day window check. The action is audit-logged with the admin's username, timestamp, and reason. The order detail page shows an "EXCEPTION" badge if exception_status is true, visible only to Admin and the student.

---

## 9. How Are "Outlier Scores" Defined for Reviewer Audit?

**Question:** The prompt says "Department Reviewers audit outlier scores." It does not define what constitutes an outlier — whether it is statistical (standard deviation), or a configurable threshold.

**My Understanding:** A statistical definition (mean ± N standard deviations) is the most defensible and academically standard approach. The prompt's context (academic evaluation cycles with indicators) strongly implies statistical outlier detection.

**Solution Implemented:** EvaluationService.detectOutliers(cycleId): for each indicator, compute mean(score) and standard_deviation(score) across all student scores. Any student score where |score - mean| > 2 * std_dev is flagged as an outlier. The multiplier (default 2.0) is configurable per evaluation cycle by Faculty. Outlier rows are highlighted in amber on the reviewer's audit page with the deviation amount shown (e.g., "+2.3σ above mean"). The reviewer can approve the score as-is or request an adjustment with a comment.

---

## 10. What Fields Does the CSV Import Support and How Is "Field Mapping" Done?

**Question:** The prompt says import/export supports "field mapping, validation error reports." The prompt does not specify what the CSV format is or how field mapping works.

**My Understanding:** Different institutions export data in different column orders. Field mapping means the system does not assume a fixed column order — instead, the user maps their CSV headers to the system's expected fields. This is done via a UI form after uploading the CSV.

**Solution Implemented:** ImportExportService: (1) read first row of CSV as headers; (2) return headers list to the import UI; (3) UI presents a mapping form (dropdown per system field showing which CSV header it maps to); (4) user submits mapping config as JSON; (5) service re-reads CSV applying the mapping to extract values. Validation checks: required fields not empty, date fields in correct format, numeric fields are valid numbers, foreign key references exist (e.g., course_id exists). Validation errors returned as a list of {row_number, field, error_message}. Valid rows are batch-inserted; invalid rows are skipped with errors shown.

---

## 11. What Qualifies as a "Failed Job" for the Retry Queue?

**Question:** The prompt specifies "a failed-job retry queue that retries up to 3 times with exponential backoff." It does not specify which operations go through this queue.

**My Understanding:** The retry queue is most relevant for operations that are likely to fail transiently and need reliable completion: CSV import batch processing, data export generation, grade recalculation for large cohorts, and future integration with external systems (even though currently disabled for offline use).

**Solution Implemented:** RetryJob entity covers: CSV_IMPORT (large file processing), DATA_EXPORT (account deletion export), GRADE_RECALCULATE (bulk recalculation when rules change), AUDIT_EXPORT (admin-requested audit log export). Each job stores a payload JSON, attempt_count, max_attempts=3, and next_retry_at. The @Scheduled(fixedDelay=300000) task (every 5 minutes) picks up pending jobs where next_retry_at <= NOW(). On failure: attempt_count++, next_retry_at = NOW() + 2^attempt_count * 60 seconds (1min, 2min, 4min). After 3 failures: status=failed, Admin is notified via in-app message. Succeeded jobs are retained for 30 days then purged.

---

## 12. What Does "Reconciliation Support via Correlation IDs" Mean Specifically?

**Question:** The prompt says orders have "reconciliation support via unique correlation IDs that persist across retries and exports." This is an idempotency concept but the exact mechanism needs clarification.

**My Understanding:** A correlation ID is a client-generated UUID that uniquely identifies an order creation intent. If the same request is submitted multiple times (network retry, double-click), the system returns the existing order rather than creating a duplicate. "Persists across exports" means the correlation ID appears in CSV exports and can be used to match exported records back to source orders.

**Solution Implemented:** Order.correlationId is a UUID VARCHAR(36) with a UNIQUE constraint. The checkout form generates a UUID on page load (stored in a hidden field). On POST /orders/create: if an order with the same correlationId already exists AND was created within 10 minutes → return the existing order (HTTP 200 with existing order data). If older than 10 minutes → treat as a new order. The correlationId is included in all order CSV exports and in API responses, enabling downstream systems to match records without relying on internal database IDs.