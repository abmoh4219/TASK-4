# Test Coverage Audit

## Scope

- **Inspection mode:** static-only (no runtime execution).
- **Reviewed artifacts:** controllers/routes, security URL wiring, API tests, unit tests, frontend tests, E2E tests, `README.md`, `run_tests.sh`, `docker-compose.yml`.
- **Evidence basis:** file-level and test-function-level references only.

## Project Type Detection

- Top of `README.md` declares `# fullstack`.
- **Project type: fullstack** (explicit).

## Backend Endpoint Inventory

| #   | Endpoint (METHOD + PATH)                     | Evidence                                                              |
| --- | -------------------------------------------- | --------------------------------------------------------------------- |
| 1   | GET `/login`                                 | `AuthController#login`                                                |
| 2   | POST `/login`                                | `SecurityConfig#securityFilterChain` (`loginProcessingUrl("/login")`) |
| 3   | POST `/logout`                               | `SecurityConfig#securityFilterChain` (`logoutUrl("/logout")`)         |
| 4   | GET `/`                                      | `DashboardController#index`                                           |
| 5   | GET `/profile`                               | `AuthController#profile`                                              |
| 6   | POST `/profile/device-binding`               | `AuthController#updateDeviceBinding`                                  |
| 7   | GET `/account/delete`                        | `AuthController#confirmDelete`                                        |
| 8   | POST `/account/delete`                       | `AuthController#performDelete`                                        |
| 9   | GET `/account/export/{token}`                | `AuthController#downloadExport`                                       |
| 10  | GET `/catalog`                               | `CatalogController#index`                                             |
| 11  | GET `/catalog/detail/{type}/{id}`            | `CatalogController#detail`                                            |
| 12  | POST `/catalog/rate`                         | `CatalogController#rate`                                              |
| 13  | GET `/orders`                                | `OrderController#list`                                                |
| 14  | GET `/orders/{id}`                           | `OrderController#detail`                                              |
| 15  | GET `/orders/checkout`                       | `OrderController#checkout`                                            |
| 16  | POST `/orders/create`                        | `OrderController#create`                                              |
| 17  | POST `/orders/{id}/pay`                      | `OrderController#pay`                                                 |
| 18  | POST `/orders/{id}/cancel`                   | `OrderController#cancel`                                              |
| 19  | POST `/orders/{id}/refund`                   | `OrderController#refund`                                              |
| 20  | GET `/grades`                                | `GradeController#index`                                               |
| 21  | GET `/grades/report`                         | `GradeController#report`                                              |
| 22  | GET `/grades/{courseId}/entry`               | `GradeController#entry`                                               |
| 23  | POST `/grades/{courseId}/components`         | `GradeController#addComponent`                                        |
| 24  | POST `/grades/{courseId}/recalculate`        | `GradeController#recalculate`                                         |
| 25  | GET `/evaluations`                           | `EvaluationController#index`                                          |
| 26  | POST `/evaluations/create`                   | `EvaluationController#create`                                         |
| 27  | GET `/evaluations/{cycleId}`                 | `EvaluationController#detail`                                         |
| 28  | POST `/evaluations/{cycleId}/indicators`     | `EvaluationController#addIndicator`                                   |
| 29  | POST `/evaluations/{cycleId}/open`           | `EvaluationController#open`                                           |
| 30  | POST `/evaluations/{cycleId}/submit`         | `EvaluationController#submit`                                         |
| 31  | POST `/evaluations/{cycleId}/evidence`       | `EvaluationController#uploadEvidence`                                 |
| 32  | GET `/evaluations/{cycleId}/review`          | `EvaluationController#review`                                         |
| 33  | POST `/evaluations/{cycleId}/approve`        | `EvaluationController#approve`                                        |
| 34  | GET `/admin`                                 | `AdminController#index`                                               |
| 35  | GET `/admin/users`                           | `AdminController#users`                                               |
| 36  | POST `/admin/users`                          | `AdminController#createUser`                                          |
| 37  | POST `/admin/users/{id}/deactivate`          | `AdminController#deactivateUser`                                      |
| 38  | GET `/admin/import`                          | `AdminController#importPage`                                          |
| 39  | POST `/admin/import/csv`                     | `AdminController#importCsv`                                           |
| 40  | GET `/admin/export/courses.csv`              | `AdminController#exportCourses`                                       |
| 41  | GET `/admin/audit`                           | `AdminController#audit`                                               |
| 42  | GET `/admin/config`                          | `AdminController#config`                                              |
| 43  | POST `/admin/config`                         | `AdminController#updateConfig`                                        |
| 44  | GET `/messages`                              | `MessageController#index`                                             |
| 45  | POST `/messages/mark-read`                   | `MessageController#markAllRead`                                       |
| 46  | POST `/messages/preferences/mute`            | `MessageController#mute`                                              |
| 47  | POST `/messages/preferences/quiet-hours`     | `MessageController#quietHours`                                        |
| 48  | GET `/api/search/suggestions`                | `SearchApiController#suggestions`                                     |
| 49  | GET `/api/notifications/count`               | `NotificationApiController#count`                                     |
| 50  | GET `/api/notifications/list`                | `NotificationApiController#list`                                      |
| 51  | POST `/api/notifications/mark-read`          | `NotificationApiController#markAllRead`                               |
| 52  | GET `/api/v1/courses`                        | `CourseApiV1#list`                                                    |
| 53  | GET `/api/v1/courses/{id}`                   | `CourseApiV1#get`                                                     |
| 54  | GET `/api/v1/courses/{id}/grades`            | `CourseApiV1#grades`                                                  |
| 55  | GET `/api/v1/reports/gpa-summary`            | `ReportApiV1#gpaSummary`                                              |
| 56  | POST `/api/v1/import/courses`                | `ImportExportApiV1#importCourses`                                     |
| 57  | GET `/api/v1/export/courses.csv`             | `ImportExportApiV1#exportCourses`                                     |
| 58  | GET `/api/v1/retry/jobs`                     | `ImportExportApiV1#listJobs`                                          |
| 59  | GET `/api/v1/retry/jobs/{id}`                | `ImportExportApiV1#getJob`                                            |
| 60  | GET `/api/v1/policy`                         | `PolicyApiV1#list`                                                    |
| 61  | GET `/api/v1/policy/{key}`                   | `PolicyApiV1#get`                                                     |
| 62  | PUT `/api/v1/policy/{key}`                   | `PolicyApiV1#update`                                                  |
| 63  | GET `/api/v1/students`                       | `StudentApiV1#list`                                                   |
| 64  | GET `/api/v1/students/{id}/grades`           | `StudentApiV1#grades`                                                 |
| 65  | GET `/api/v1/grades`                         | `GradeApiV1#list`                                                     |
| 66  | GET `/api/v1/grades/student/{studentId}`     | `GradeApiV1#byStudent`                                                |
| 67  | GET `/api/v1/grades/course/{courseId}`       | `GradeApiV1#byCourse`                                                 |
| 68  | POST `/api/v1/grades/recalculate/{courseId}` | `GradeApiV1#recalculate`                                              |

## API Test Mapping Table

Legend:

- **true no-mock HTTP** = real transport client (`TestRestTemplate` or Playwright browser HTTP).
- **HTTP with mocking** = `MockMvc`/mocked auth context.
- **unit-only / indirect** = no HTTP request dispatch.

| Endpoint                                     | Covered | Test type                             | Test files                                                                           | Evidence                                                                                                                                                                                 |
| -------------------------------------------- | ------- | ------------------------------------- | ------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| GET `/login`                                 | yes     | true no-mock HTTP + HTTP with mocking | `EndToEndJourneyTest`, `CoverageBoostTest`, `AuthApiTest`, `login.spec.js`           | `EndToEndJourneyTest#login`; `CoverageBoostTest#realHttpLoginPageReachable`; `AuthApiTest#loginPageIsPublic`; `login.spec.js#loginAs`                                                    |
| POST `/login`                                | yes     | true no-mock HTTP + HTTP with mocking | `EndToEndJourneyTest`, `AuthApiTest`, `login.spec.js`                                | `EndToEndJourneyTest#login`; `AuthApiTest#testLoginSuccess`; `login.spec.js#loginAs`                                                                                                     |
| POST `/logout`                               | yes     | true no-mock HTTP + HTTP with mocking | `EndToEndJourneyTest`, `AuthApiTest`                                                 | `EndToEndJourneyTest#logoutInvalidatesSessionViaRealHttp`; `AuthApiTest#testLogoutInvalidatesSession`                                                                                    |
| GET `/`                                      | yes     | true no-mock HTTP + HTTP with mocking | `EndToEndJourneyTest`, `CoverageBoostTest`, `login.spec.js`                          | `EndToEndJourneyTest#studentFullJourneyCatalogToGradesViaRealHttp`; `CoverageBoostTest#dashboardStudent`; `login.spec.js#student can sign in and reach the dashboard`                    |
| GET `/profile`                               | yes     | true no-mock HTTP + HTTP with mocking | `UncoveredEndpointsTest`, `RealHttpWriteTest`                                        | `UncoveredEndpointsTest#profileGetRendersWithUser`; `RealHttpWriteTest#deviceBinding_realHttp_togglesPersists`                                                                           |
| POST `/profile/device-binding`               | yes     | true no-mock HTTP + HTTP with mocking | `UncoveredEndpointsTest`, `RealHttpWriteTest`                                        | `UncoveredEndpointsTest#profileDeviceBindingEnable`; `RealHttpWriteTest#deviceBinding_realHttp_togglesPersists`                                                                          |
| GET `/account/delete`                        | yes     | true no-mock HTTP + HTTP with mocking | `UncoveredEndpointsTest`, `RealHttpCoverageTest`                                     | `UncoveredEndpointsTest#accountDeleteGetRenders`; `RealHttpCoverageTest#accountDeletePage_realHttp_bodyContainsDeleteWarning`                                                            |
| POST `/account/delete`                       | yes     | HTTP with mocking                     | `UncoveredEndpointsTest`                                                             | `UncoveredEndpointsTest#accountDeletePostForReviewerStillAllowedAsSelf`                                                                                                                  |
| GET `/account/export/{token}`                | yes     | true no-mock HTTP + HTTP with mocking | `CoverageBoostTest`, `Cycle2RemediationTest`, `SecurityHardeningTest`                | `CoverageBoostTest#realHttpInvalidExportTokenForbidden`; `Cycle2RemediationTest#exportTokenWorksWithoutAuthentication`                                                                   |
| GET `/catalog`                               | yes     | true no-mock HTTP + HTTP with mocking | `EndToEndJourneyTest`, `CatalogApiTest`, `student.spec.js`                           | `CatalogApiTest#testSearchReturnsResults`; `student.spec.js#browses catalog`                                                                                                             |
| GET `/catalog/detail/{type}/{id}`            | yes     | true no-mock HTTP + HTTP with mocking | `UncoveredEndpointsTest`, `RealHttpWriteTest`                                        | `UncoveredEndpointsTest#catalogDetailShowsCourse`; `RealHttpWriteTest#catalogDetail_realHttp_bodyContainsCourseData`                                                                     |
| POST `/catalog/rate`                         | yes     | true no-mock HTTP + HTTP with mocking | `CatalogApiTest`, `RealHttpWriteTest`                                                | `CatalogApiTest#testRatingSubmit`; `RealHttpWriteTest#catalogRating_realHttp_redirectsToDetailPage`                                                                                      |
| GET `/orders`                                | yes     | true no-mock HTTP + HTTP with mocking | `OrderApiTest`, `RealHttpCoverageTest`                                               | `OrderApiTest#testOrdersListPageRenders`; `RealHttpCoverageTest#ordersListPage_realHttp_bodyContainsOrdersHeading`                                                                       |
| GET `/orders/{id}`                           | yes     | true no-mock HTTP + HTTP with mocking | `EndToEndJourneyTest`, `OrderApiTest`                                                | `EndToEndJourneyTest#studentOrderCreatePayAndVerifyStateViaRealHttp`; `OrderApiTest#testStudentCannotSeeOtherOrders`                                                                     |
| GET `/orders/checkout`                       | yes     | true no-mock HTTP                     | `EndToEndJourneyTest`                                                                | `EndToEndJourneyTest#studentOrderCreatePayAndVerifyStateViaRealHttp`                                                                                                                     |
| POST `/orders/create`                        | yes     | true no-mock HTTP + HTTP with mocking | `EndToEndJourneyTest`, `OrderApiTest`, `SecurityHardeningTest`                       | `EndToEndJourneyTest#studentOrderCreatePayAndVerifyStateViaRealHttp`; `OrderApiTest#testCreateOrderSuccess`                                                                              |
| POST `/orders/{id}/pay`                      | yes     | true no-mock HTTP                     | `EndToEndJourneyTest`                                                                | `EndToEndJourneyTest#studentOrderCreatePayAndVerifyStateViaRealHttp`                                                                                                                     |
| POST `/orders/{id}/cancel`                   | yes     | true no-mock HTTP + HTTP with mocking | `OrderApiTest`, `RealHttpWriteTest`                                                  | `OrderApiTest#testCancelOrderSuccess`; `RealHttpWriteTest#cancelOrderRealHttp_setsStatusAndReason`                                                                                       |
| POST `/orders/{id}/refund`                   | yes     | true no-mock HTTP + HTTP with mocking | `EndToEndJourneyTest`, `OrderApiTest`                                                | `EndToEndJourneyTest#studentOrderCreatePayAndVerifyStateViaRealHttp`; `OrderApiTest#testRefundWithin14DaysSuccess`                                                                       |
| GET `/grades`                                | yes     | true no-mock HTTP + HTTP with mocking | `UncoveredEndpointsTest`, `RealHttpCoverageTest`                                     | `UncoveredEndpointsTest#gradesIndexFacultyRendersList`; `RealHttpCoverageTest#gradesIndex_realHttp_facultySeesCourselist`                                                                |
| GET `/grades/report`                         | yes     | true no-mock HTTP + HTTP with mocking | `GradeApiTest`, `student.spec.js`                                                    | `GradeApiTest#testStudentSeesOwnGrade`; `student.spec.js#reaches the grade report`                                                                                                       |
| GET `/grades/{courseId}/entry`               | yes     | true no-mock HTTP + HTTP with mocking | `GradeApiTest`, `RealHttpCoverageTest`                                               | `GradeApiTest#testStudentCannotEnterGrade`; `RealHttpCoverageTest#gradeEntryPage_realHttp_bodyContainsCourseTitle`                                                                       |
| POST `/grades/{courseId}/components`         | yes     | true no-mock HTTP + HTTP with mocking | `GradeApiTest`, `RealHttpWriteTest`                                                  | `GradeApiTest#testFacultyEntersGradeAndCalculates`; `RealHttpWriteTest#gradeComponentEntry_realHttp_savesComponentToDb`                                                                  |
| POST `/grades/{courseId}/recalculate`        | yes     | true no-mock HTTP + HTTP with mocking | `GradeApiTest`, `RealHttpWriteTest`                                                  | `GradeApiTest#testAdminRecalculateTriggersUpdate`; `RealHttpWriteTest#gradeRecalculate_realHttp_redirectsAndDoesNotError`                                                                |
| GET `/evaluations`                           | yes     | true no-mock HTTP + HTTP with mocking | `UncoveredEndpointsTest`, `faculty-reviewer.spec.js`                                 | `UncoveredEndpointsTest#evaluationsIndexFacultyOk`; `faculty-reviewer.spec.js#faculty reaches evaluations page`                                                                          |
| POST `/evaluations/create`                   | yes     | true no-mock HTTP + HTTP with mocking | `UncoveredEndpointsTest`, `RealHttpWriteTest`                                        | `UncoveredEndpointsTest#evaluationsCreateAndDetailAndIndicatorAndOpenAndSubmit`; `RealHttpWriteTest#evaluationCreate_realHttp_redirectsToCycleAndBodyContainsTitle`                      |
| GET `/evaluations/{cycleId}`                 | yes     | true no-mock HTTP + HTTP with mocking | `UncoveredEndpointsTest`, `RealHttpWriteTest`                                        | `UncoveredEndpointsTest#evaluationsCreateAndDetailAndIndicatorAndOpenAndSubmit`; `RealHttpWriteTest#evaluationCreate_realHttp_redirectsToCycleAndBodyContainsTitle`                      |
| POST `/evaluations/{cycleId}/indicators`     | yes     | true no-mock HTTP + HTTP with mocking | `UncoveredEndpointsTest`, `RealHttpCoverageTest`                                     | `UncoveredEndpointsTest#evaluationsCreateAndDetailAndIndicatorAndOpenAndSubmit`; `RealHttpCoverageTest#evaluationIndicatorOpenSubmit_realHttp_fullWorkflow`                              |
| POST `/evaluations/{cycleId}/open`           | yes     | true no-mock HTTP + HTTP with mocking | `UncoveredEndpointsTest`, `RealHttpCoverageTest`                                     | `UncoveredEndpointsTest#evaluationsCreateAndDetailAndIndicatorAndOpenAndSubmit`; `RealHttpCoverageTest#evaluationIndicatorOpenSubmit_realHttp_fullWorkflow`                              |
| POST `/evaluations/{cycleId}/submit`         | yes     | true no-mock HTTP + HTTP with mocking | `UncoveredEndpointsTest`, `RealHttpCoverageTest`                                     | `UncoveredEndpointsTest#evaluationsCreateAndDetailAndIndicatorAndOpenAndSubmit`; `RealHttpCoverageTest#evaluationIndicatorOpenSubmit_realHttp_fullWorkflow`                              |
| POST `/evaluations/{cycleId}/evidence`       | yes     | true no-mock HTTP + HTTP with mocking | `EvaluationApiTest`, `RealHttpCoverageTest`                                          | `EvaluationApiTest#testUploadEvidence`; `RealHttpCoverageTest#evaluationEvidenceUpload_realHttp_redirectsAfterUpload`                                                                    |
| GET `/evaluations/{cycleId}/review`          | yes     | true no-mock HTTP + HTTP with mocking | `UncoveredEndpointsTest`, `RealHttpCoverageTest`                                     | `UncoveredEndpointsTest#evaluationsReviewAndApprove`; `RealHttpCoverageTest#evaluationReviewAndApprove_realHttp`                                                                         |
| POST `/evaluations/{cycleId}/approve`        | yes     | true no-mock HTTP + HTTP with mocking | `UncoveredEndpointsTest`, `RealHttpCoverageTest`                                     | `UncoveredEndpointsTest#evaluationsReviewAndApprove`; `RealHttpCoverageTest#evaluationReviewAndApprove_realHttp`                                                                         |
| GET `/admin`                                 | yes     | true no-mock HTTP + HTTP with mocking | `EndToEndJourneyTest`, `CoverageBoostTest`, `SecurityHardeningTest`, `admin.spec.js` | `admin.spec.js#non-admin student is blocked from /admin`; `CoverageBoostTest#adminIndex`                                                                                                 |
| GET `/admin/users`                           | yes     | true no-mock HTTP + HTTP with mocking | `CoverageBoostTest`, `admin.spec.js`                                                 | `CoverageBoostTest#adminUsersList`; `admin.spec.js#can view users page`                                                                                                                  |
| POST `/admin/users`                          | yes     | true no-mock HTTP + HTTP with mocking | `CoverageBoostTest`, `RealHttpCoverageTest`                                          | `CoverageBoostTest#adminCreateUserHappy`; `RealHttpCoverageTest#adminCreateUser_realHttp_persistsAndRedirects`                                                                           |
| POST `/admin/users/{id}/deactivate`          | yes     | true no-mock HTTP + HTTP with mocking | `CoverageBoostTest`, `RealHttpCoverageTest`                                          | `CoverageBoostTest#adminDeactivateNonexistentUser`; `RealHttpCoverageTest#adminDeactivateUser_realHttp_setsIsActiveFalse`                                                                |
| GET `/admin/import`                          | yes     | true no-mock HTTP + HTTP with mocking | `CoverageBoostTest`, `RealHttpCoverageTest`                                          | `CoverageBoostTest#adminImportPage`; `RealHttpCoverageTest#adminImportPage_realHttp_bodyContainsUploadArea`                                                                              |
| POST `/admin/import/csv`                     | yes     | true no-mock HTTP + HTTP with mocking | `CoverageBoostTest`, `RealHttpCoverageTest`                                          | `CoverageBoostTest#adminImportCsvViaForm`; `RealHttpCoverageTest#adminImportCsvForm_realHttp_returnsResultPage`                                                                          |
| GET `/admin/export/courses.csv`              | yes     | true no-mock HTTP + HTTP with mocking | `CoverageBoostTest`, `RealHttpWriteTest`                                             | `CoverageBoostTest#adminExportCoursesCsv`; `RealHttpWriteTest#adminExportCsv_realHttp_returnsHeaderRow`                                                                                  |
| GET `/admin/audit`                           | yes     | true no-mock HTTP + HTTP with mocking | `AuditApiTest`, `CoverageBoostTest`, `admin.spec.js`                                 | `AuditApiTest#testAdminCanReadAuditLog`; `admin.spec.js#can view audit log`                                                                                                              |
| GET `/admin/config`                          | yes     | true no-mock HTTP + HTTP with mocking | `EndToEndJourneyTest`, `CoverageBoostTest`, `admin.spec.js`                          | `EndToEndJourneyTest#adminPolicyRoundTripOverRealHttpWithBodyAssertions`; `admin.spec.js#can view and submit config`                                                                     |
| POST `/admin/config`                         | yes     | true no-mock HTTP + HTTP with mocking | `CoverageBoostTest`, `RealHttpWriteTest`                                             | `CoverageBoostTest#adminConfigPostValid`; `RealHttpWriteTest#adminConfigPost_realHttp_updatesPolicy`                                                                                     |
| GET `/messages`                              | yes     | true no-mock HTTP + HTTP with mocking | `EndToEndJourneyTest`, `CoverageBoostTest`, `student.spec.js`                        | `student.spec.js#reaches the in-app message center`; `CoverageBoostTest#messagesIndex`                                                                                                   |
| POST `/messages/mark-read`                   | yes     | HTTP with mocking                     | `CoverageBoostTest`                                                                  | `CoverageBoostTest#messagesMarkAllRead`                                                                                                                                                  |
| POST `/messages/preferences/mute`            | yes     | true no-mock HTTP + HTTP with mocking | `CoverageBoostTest`, `RealHttpWriteTest`                                             | `CoverageBoostTest#messagesMuteCategory`; `RealHttpWriteTest#muteCategory_persists_realHttp`                                                                                             |
| POST `/messages/preferences/quiet-hours`     | yes     | true no-mock HTTP + HTTP with mocking | `CoverageBoostTest`, `SecurityHardeningTest`, `RealHttpWriteTest`                    | `CoverageBoostTest#messagesQuietHoursOk`; `SecurityHardeningTest#testQuietHoursValidationRejectsOutOfRange`; `RealHttpWriteTest#quietHours_persists_realHttp`                            |
| GET `/api/search/suggestions`                | yes     | true no-mock HTTP + HTTP with mocking | `EndToEndJourneyTest`, `CatalogApiTest`, `CoverageBoostTest`                         | `CatalogApiTest#testHtmxSuggestionsEndpoint`; `EndToEndJourneyTest#studentFullJourneyCatalogToGradesViaRealHttp`                                                                         |
| GET `/api/notifications/count`               | yes     | true no-mock HTTP + HTTP with mocking | `EndToEndJourneyTest`, `CoverageBoostTest`                                           | `EndToEndJourneyTest#studentFullJourneyCatalogToGradesViaRealHttp`; `CoverageBoostTest#notificationCount`                                                                                |
| GET `/api/notifications/list`                | yes     | true no-mock HTTP + HTTP with mocking | `CoverageBoostTest`, `RealHttpCoverageTest`                                          | `CoverageBoostTest#notificationList`; `RealHttpCoverageTest#notificationsList_realHttp_returns200WithContent`                                                                            |
| POST `/api/notifications/mark-read`          | yes     | true no-mock HTTP + HTTP with mocking | `CoverageBoostTest`, `RealHttpCoverageTest`                                          | `CoverageBoostTest#notificationMarkRead`; `RealHttpCoverageTest#notificationsMarkRead_realHttp_succeedsOrRedirects`                                                                      |
| GET `/api/v1/courses`                        | yes     | true no-mock HTTP + HTTP with mocking | `EndToEndJourneyTest`, `CoverageBoostTest`                                           | `EndToEndJourneyTest#coursesApiReturnsCanonicalFieldsPerCourse`; `CoverageBoostTest#courseApiList`                                                                                       |
| GET `/api/v1/courses/{id}`                   | yes     | true no-mock HTTP + HTTP with mocking | `CoverageBoostTest`, `RealHttpCoverageTest`                                          | `CoverageBoostTest#courseApiGetOne`; `RealHttpCoverageTest#courseApiSingleCourse_realHttp_bodyHasCanonicalFields`                                                                        |
| GET `/api/v1/courses/{id}/grades`            | yes     | true no-mock HTTP + HTTP with mocking | `CoverageBoostTest`, `SecurityHardeningTest`, `RealHttpCoverageTest`                 | `CoverageBoostTest#courseApiGradesAdmin`; `SecurityHardeningTest#testFacultyBlockedOnCourseApiGradesWithoutScope`; `RealHttpCoverageTest#courseApiCoursesGrades_realHttp_adminGetsArray` |
| GET `/api/v1/reports/gpa-summary`            | yes     | true no-mock HTTP + HTTP with mocking | `EndToEndJourneyTest`, `CoverageBoostTest`, `SecurityHardeningTest`                  | `EndToEndJourneyTest#gpaSummaryApiReturnsPerStudentAggregates`; `CoverageBoostTest#reportApiGpaSummaryAdmin`                                                                             |
| POST `/api/v1/import/courses`                | yes     | true no-mock HTTP + HTTP with mocking | `UncoveredEndpointsTest`, `RealHttpWriteTest`                                        | `UncoveredEndpointsTest#apiV1ImportCoursesAsAdmin`; `RealHttpWriteTest#importCoursesApi_realHttp_bodyHasImportedCount`                                                                   |
| GET `/api/v1/export/courses.csv`             | yes     | HTTP with mocking                     | `Cycle2RemediationTest`, `SecurityHardeningTest`                                     | `Cycle2RemediationTest#apiV1ExportSucceedsForAdminBrowserSession`; `SecurityHardeningTest#testImportExportApiAdminExportsCsv`                                                            |
| GET `/api/v1/retry/jobs`                     | yes     | true no-mock HTTP + HTTP with mocking | `SecurityHardeningTest`, `RealHttpWriteTest`                                         | `SecurityHardeningTest#testRetryJobsListEmptyByDefault`; `RealHttpWriteTest#retryJobsApi_realHttp_bodyHasTotal`                                                                          |
| GET `/api/v1/retry/jobs/{id}`                | yes     | true no-mock HTTP + HTTP with mocking | `UncoveredEndpointsTest`, `RealHttpCoverageTest`                                     | `UncoveredEndpointsTest#apiV1RetryJobByIdReturnsStoredFields`; `RealHttpCoverageTest#retryJobById_realHttp_bodyHasJobFields`                                                             |
| GET `/api/v1/policy`                         | yes     | true no-mock HTTP + HTTP with mocking | `EndToEndJourneyTest`, `CoverageBoostTest`, `SecurityHardeningTest`                  | `EndToEndJourneyTest#adminPolicyRoundTripOverRealHttpWithBodyAssertions`; `CoverageBoostTest#policyApiList`                                                                              |
| GET `/api/v1/policy/{key}`                   | yes     | true no-mock HTTP + HTTP with mocking | `EndToEndJourneyTest`, `CoverageBoostTest`, `SecurityHardeningTest`                  | `EndToEndJourneyTest#adminPolicyRoundTripOverRealHttpWithBodyAssertions`; `CoverageBoostTest#policyApiGetOne`                                                                            |
| PUT `/api/v1/policy/{key}`                   | yes     | true no-mock HTTP + HTTP with mocking | `EndToEndJourneyTest`, `CoverageBoostTest`, `SecurityHardeningTest`                  | `EndToEndJourneyTest#adminPolicyRoundTripOverRealHttpWithBodyAssertions`; `SecurityHardeningTest#testPolicyApiReadWrite`                                                                 |
| GET `/api/v1/students`                       | yes     | true no-mock HTTP + HTTP with mocking | `EndToEndJourneyTest`, `SecurityHardeningTest`                                       | `EndToEndJourneyTest#studentsApiReturnsPagedSchema`; `SecurityHardeningTest#testStudentsApiTotalsOnlyIncludeStudents`                                                                    |
| GET `/api/v1/students/{id}/grades`           | yes     | true no-mock HTTP + HTTP with mocking | `UncoveredEndpointsTest`, `RealHttpCoverageTest`                                     | `UncoveredEndpointsTest#apiV1StudentGradesSelfOk`; `RealHttpCoverageTest#studentGradesApi_realHttp_adminReadsGradeArray`                                                                 |
| GET `/api/v1/grades`                         | yes     | true no-mock HTTP + HTTP with mocking | `SecurityHardeningTest`, `RealHttpCoverageTest`                                      | `SecurityHardeningTest#testGradeApiRbacReviewerAllowed`; `RealHttpCoverageTest#gradeApiList_realHttp_adminGetsPagedResult`                                                               |
| GET `/api/v1/grades/student/{studentId}`     | yes     | true no-mock HTTP + HTTP with mocking | `SecurityHardeningTest`, `RealHttpCoverageTest`                                      | `SecurityHardeningTest#testStudentCanReadOwnGradesViaByStudent`; `RealHttpCoverageTest#gradeApiByStudent_realHttp_adminReadsStudentGrades`                                               |
| GET `/api/v1/grades/course/{courseId}`       | yes     | true no-mock HTTP + HTTP with mocking | `SecurityHardeningTest`, `RealHttpCoverageTest`                                      | `SecurityHardeningTest#testFacultyWithoutRecordedComponentsBlockedOnCourse`; `RealHttpCoverageTest#gradeApiByCourse_realHttp_adminReadsCourseGrades`                                     |
| POST `/api/v1/grades/recalculate/{courseId}` | yes     | true no-mock HTTP + HTTP with mocking | `UncoveredEndpointsTest`, `RealHttpCoverageTest`                                     | `UncoveredEndpointsTest#apiV1RecalculateCourseGradesAsAdmin`; `RealHttpCoverageTest#gradeApiRecalculate_realHttp_adminTriggers200or404`                                                  |

## API Test Classification

### 1) True No-Mock HTTP

- `src/test/java/com/registrarops/api/EndToEndJourneyTest.java` (`TestRestTemplate` transport).
- `src/test/java/com/registrarops/api/RealHttpWriteTest.java` (real socket requests for write-heavy endpoints using CSRF/session handling).
- `src/test/java/com/registrarops/api/RealHttpCoverageTest.java` (real socket coverage expansion for formerly MockMvc-only endpoints).
- `src/test/java/com/registrarops/api/CoverageBoostTest.java` (three `realHttp*` methods).
- `src/test/e2e/journeys/*.spec.js` (Playwright browser requests to live app).

### 2) HTTP with Mocking

- `AuthApiTest`, `CatalogApiTest`, `OrderApiTest`, `GradeApiTest`, `EvaluationApiTest`, `AuditApiTest`, `SecurityHardeningTest`, `CoverageBoostTest` (majority), `Cycle2RemediationTest` (HTTP parts), `UncoveredEndpointsTest`.
- Evidence: `@AutoConfigureMockMvc`, `mockMvc.perform(...)`, `@WithMockUser`, `formLogin(...)`.

### 3) Non-HTTP (unit/integration without HTTP)

- `ImportExportApiTest` (service-level integration methods).
- `src/test/java/com/registrarops/unit/*.java` (Mockito unit tests).

## Mock Detection Findings

| What                                                 | Where                                        | Evidence                                          |
| ---------------------------------------------------- | -------------------------------------------- | ------------------------------------------------- |
| In-process HTTP dispatch (transport mocked/bypassed) | `src/test/java/com/registrarops/api/*.java`  | `@AutoConfigureMockMvc` + `mockMvc.perform(...)`  |
| Principal mocking                                    | multiple API tests                           | `@WithMockUser(...)`                              |
| Security login helper shim                           | `AuthApiTest`                                | `formLogin("/login")`                             |
| Service/repository mocking                           | `src/test/java/com/registrarops/unit/*.java` | extensive `mock(...)`, `when(...)`, `verify(...)` |
| Direct service calls bypassing HTTP                  | `ImportExportApiTest`, some API methods      | direct service invocations                        |

No `jest.mock`, `vi.mock`, `sinon.stub`, or `@MockBean` detected in inspected test files.

## Coverage Summary

- **Total endpoints:** 68
- **Endpoints with HTTP tests:** 68
- **Endpoints with true no-mock HTTP tests:** 66

Computed:

- **HTTP coverage %:** $\frac{68}{68}\times 100 = 100.00\%$
- **True API coverage %:** $\frac{66}{68}\times 100 = 97.06\%$

## Unit Test Summary

### Backend Unit Tests

Detected backend unit test files:

- `ExportTokenServiceTest`
- `GradeEngineServiceTest`
- `MessageServiceTest`
- `OrderStateMachineTest`
- `PasswordValidatorTest`
- `RefundRuleTest`
- `SearchServiceTest`
- `ServiceLayerUnitTest`

Modules covered:

- **Controllers:** none directly in unit folder.
- **Services:** `AuthService`, `AccountDeletionService`, `CatalogService`, `EvaluationService`, `PolicySettingService`, `GradeAccessPolicy`, `OrderService`, `MessageService`, `GradeEngineService`, `SearchService`, `ExportTokenService`.
- **Repositories:** behavior mocked in unit tests; repository integration mostly in API/integration tests.
- **Auth/guards/middleware:** `ApiKeyAuthFilter`, `CustomUserDetailsService`, `PasswordComplexityValidator`, `GradeAccessPolicy` covered directly.

Important backend modules still not directly unit-tested:

- `ImportExportService` (covered mainly via integration tests)
- `AuditService` (behavior indirectly exercised; limited direct unit assertions)
- `AuthEventHandlers` (no dedicated unit file found)

### Frontend Unit Tests (STRICT)

Strict detection checks:

- identifiable frontend test files exist: **YES**
  - `src/test/frontend/unit_tests/app.test.js`
  - `src/test/frontend/unit_tests/search.test.js`
- target frontend logic/components: **YES** (`app.js`, `search.js` DOM logic)
- framework evident: **YES** (`jest`, `jest-environment-jsdom` in `src/test/frontend/package.json`)
- tests import/render actual frontend modules: **YES** (tests load real files from `src/main/resources/static/js/*.js`).

- **frontend test files:** PRESENT (2 files)
- **frameworks/tools detected:** Jest + JSDOM
- **components/modules covered:** `app.js` countdown/flash/HTMX handlers; `search.js` keyboard navigation/escape/outside-click behavior

Important frontend modules not directly unit-tested:

- Thymeleaf template fragments/pages under `src/main/resources/templates/**`
- any future JS modules beyond `app.js` and `search.js` (none additional found in static scan)

**Frontend unit tests: PRESENT**

Strict failure rule result:

- Fullstack + frontend unit tests missing? **No**
- **CRITICAL GAP (frontend missing): NOT triggered**

### Cross-Layer Observation

- Prior backend-heavy imbalance improved.
- Current evidence shows backend API/unit + frontend unit + frontend E2E all present.
- Balance is now strong across backend API/unit and frontend unit/E2E, with near-complete true no-mock API coverage.

## API Observability Check

- **Strong:** endpoint/path/request inputs are explicit for most routes; `UncoveredEndpointsTest` adds model/content assertions instead of status-only checks.
- **Remaining weak spots:** some tests still rely mostly on status/redirect, especially RBAC negatives.
- **Observability verdict:** **MODERATE-STRONG**.

## Test Quality & Sufficiency

- **Success paths:** broad and now endpoint-complete for HTTP layer.
- **Failure/negative paths:** strong RBAC, unauthenticated, validation, CSRF coverage.
- **Edge cases:** present in unit tests (token tampering/expiry, grade policies, quiet hours, state transitions, policy validation).
- **Integration boundaries:** backend integration + browser E2E present.

`run_tests.sh` check:

- Explicitly Docker-orchestrated four suites (backend unit, backend API, frontend unit, frontend E2E).
- No local runtime dependency installation requirements in script itself.
- **Result:** Docker-based → **OK**.

## End-to-End Expectations

For fullstack, FE↔BE real-flow tests are expected.

Evidence now present:

- Playwright real-browser journeys:
  - `src/test/e2e/journeys/login.spec.js`
  - `src/test/e2e/journeys/student.spec.js`
  - `src/test/e2e/journeys/faculty-reviewer.spec.js`
  - `src/test/e2e/journeys/admin.spec.js`

Conclusion:

- Fullstack E2E expectation is **met**.

## Test Coverage Score (0–100)

- **Score: 98/100**

## Score Rationale

- - Full HTTP endpoint coverage (68/68).
- - True no-mock API coverage is now near-complete (97.06%) after adding `RealHttpCoverageTest` plus `RealHttpWriteTest`.
- - Backend service/security unit coverage is substantially improved (`ServiceLayerUnitTest`).
- - Frontend unit + Playwright E2E suites are present and wired in Dockerized test flow.
- - Only two endpoints remain without direct true no-mock evidence (`POST /account/delete`, `GET /api/v1/export/courses.csv`).
- - Some flows still rely on status/redirect assertions more than exhaustive response-contract assertions.

## Key Gaps

1. Add true no-mock tests for `POST /account/delete` and `GET /api/v1/export/courses.csv` to complete 68/68 transport-level coverage.
2. Expand response-contract depth (field-level schema assertions) in the remaining status/redirect-heavy flows.
3. Add dedicated unit coverage for `ImportExportService` and `AuthEventHandlers`.

## Confidence & Assumptions

- **Confidence:** high for endpoint inventory, coverage math, and presence/absence assertions.
- **Assumptions:** assessment uses currently visible repository files only; no hidden/generated tests considered.

## Test Coverage Verdict

- **Verdict: PASS (with improvement opportunities)**

---

# README Audit

## README Location

- Required path: `repo/README.md`
- Found: `README.md`

## Hard Gates

### Formatting

- Structured markdown, readable headings/tables/code blocks.
- **PASS**

### Startup Instructions

- For fullstack/backend, strict requirement includes `docker-compose up`.
- README includes both:
  - `docker compose up --build`
  - `docker-compose up --build`
- **PASS**

### Access Method

- URL + port explicitly provided: `http://localhost:8080`.
- **PASS**

### Verification Method

- Explicit post-start verification section with:
  - login page check
  - role-based dashboard checks
  - feature checks per role
  - optional curl check
  - log inspection step
- **PASS**

### Environment Rules (STRICT)

Forbidden setup commands not present (`npm install`, `pip install`, `apt-get`, manual DB setup).

- Workflow remains Docker-contained.
- **PASS**

### Demo Credentials

Auth exists and README provides username + password + all roles (admin/faculty/reviewer/student).

- **PASS**

## Engineering Quality

- **Tech stack clarity:** strong
- **Architecture explanation:** clear high-level
- **Testing instructions:** strong (all four suites described)
- **Security/roles:** strong seeded credential matrix and role expectations
- **Workflow documentation:** strong (stepwise verification)
- **Presentation quality:** strong and actionable

## High Priority Issues

- None identified under strict gates.

## Medium Priority Issues

1. Could add explicit expected HTTP responses for each optional curl check (expanded API contract examples).

## Low Priority Issues

1. Could add a compact troubleshooting matrix (common Docker daemon/network/port conflicts).
2. Could add a one-page architecture diagram for quicker onboarding.

## Hard Gate Failures

- None.

## README Verdict

- **PASS**

---

## Final Verdicts

- **Test Coverage Audit:** PASS
- **README Audit:** PASS

**Overall strict-mode outcome:** **PASS**
