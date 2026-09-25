# MoneyMap interview-readiness audit

Audit date: 25 September 2026. Scope: every source/configuration file in this repository, existing tests, live HTTP flows, and browser UI. This is an interview project review, not a penetration-test certificate.

## Verdict

The core implementation is suitable for a local interview demonstration after restarting with the final build and doing the short rehearsal below. It is not ready to advertise as a production financial service. The audit found and fixed concrete issues; verification limits remain explicit below.

Use the configured app on port 8080. At the start of this audit, an older process on 8081 served HTML but returned HTTP 503 for login. MySQL itself was running. After the user launched the configured instance on 8080, real registration, persistence, and authorization checks passed. Merely loading the home page is not a health check for the database.

Evidence: final `mvn -B verify` passed 13 JUnit tests and packaged the WAR. `node scripts/test-frontend.mjs` passed six groups of regression checks. The live MySQL-backed API suite passed 41 assertions on 8080 before the final runtime/security update. A separate fresh Jetty 12.0.38 process on 127.0.0.1:8082 successfully returned 401 for anonymous access, 404 for unknown API route, 415 for non-JSON login, 403 for cross-origin login, and 400 for invalid/oversized input. That temporary process was stopped afterward. The final live API suite now includes an additional non-JSON check and requires restarting the configured 8080 process. A full database-backed rerun on the final patched process remains pending; do not conflate the two test stages.

## Architecture and file study map

Browser form → fetch/JSON → same-origin filter → servlet → service/repository → JDBC → MySQL. The response contains the selected month's summary and transactions; JavaScript renders the dashboard.

| File / important functions | Responsibility and review conclusion |
| --- | --- |
| `src/main/webapp/index.html` | Login/register, monthly navigation, allocation, budget editor, ledger filters/pagination, add form, edit dialog, logout and print. Explicit labels, live messages, native form constraints. No SPA framework is needed for this scope. |
| `src/main/webapp/app.js`: `requestJson`, `loadDashboard`, `renderDashboard` | Fetch handling and display state. Fixed malformed-success responses, request overlap, and expired-session handling. UI values are JavaScript numbers; authoritative money calculation is on the backend. |
| `app.js`: `shiftMonth`, `moveMonth`, `dateForMonth` | Integer month navigation avoids end-of-month date rollover. The selected month is in memory; refresh deliberately returns to the current month. |
| `app.js`: `renderTransactions`, `renderSpending`, `escapeHtml` | Client-side search, filters, five-row pagination, safe HTML interpolation, category amounts/percentages and remaining budget. Print now expands all matching rows and restores pagination. |
| `app.js`: form handlers / `setBudgetEditing` | Add/edit/delete, login/register/logout and budget edit/save/discard. Clicking Edit budget again discards the unsaved amount. Zero clears a month's budget. |
| `src/main/webapp/styles.css` | Single design system, grid/flex layouts, responsive breakpoints, dialog/print styles, keyboard focus, reduced-motion support. Long text/money wrapping improved. |
| `web/FinanceServlet.java` | Session identity, request parsing, month validation, transaction/budget operations, HTTP/JSON responses. Month validation now occurs before writes. Unknown GET/POST API routes return 404. |
| `web/AuthServlet.java` | Registration/login/logout, duplicate email detection, session-ID rotation. Registration validates input, then delegates an atomic user/budget insert. Added login input bounds and sanitized failure logging. |
| `web/SameOriginFilter.java` | Rejects mismatched origins/cross-site writes, requires JSON for POST/PUT except logout, sets UTF-8 and no-store/nosniff headers. This is not a synchronizer-token CSRF implementation. |
| `web/JsonUtil.java` | Small flat-object JSON reader and response serializer. Fixed null semantics, duplicate-null keys, and bounded reading. Nested JSON is deliberately unsupported. A standard JSON library is preferable if the API grows. |
| `service/FinanceService.java` | Validates amounts/descriptions/dates/types, aggregates monthly income/expenses/categories with BigDecimal, uses zero for absent monthly budgets. Repository injection makes unit testing simple. |
| `repository/TransactionRepository.java` | Prepared SQL for month read/create/update/delete. Every update/delete scopes by both transaction ID and authenticated user ID. Inclusive month-end bound supports December 9999 without an invalid next year. |
| `repository/MonthlyBudgetRepository.java` | Read by user/month, atomic MySQL upsert. Missing row means zero budget; clearing stores zero. |
| `repository/UserRepository.java` | Email lookup and transactional user + initial-budget insertion. Rollback on failure. Removed unused `updateBudget`, which wrote the obsolete profile budget. |
| `model/Transaction.java`, `model/UserProfile.java` | Simple immutable data holders; no hidden persistence logic. Profile still has a legacy registration-budget field, discussed below. |
| `util/PasswordUtil.java` | Random salt, PBKDF2-HMAC-SHA256, encoded iteration/salt/hash, constant-time hash comparison. New hashes use 600,000 iterations; old encoded iteration counts remain supported. |
| `config/Database.java` | Environment-based JDBC configuration; one new connection per repository operation. No pool, no secrets file required. |
| `schema.sql` | Three tables, decimal money, foreign keys/cascades, unique email, composite monthly-budget key, transaction date index. |
| `WEB-INF/web.xml`, `pom.xml` | Explicit routing/filter mappings, 30-minute cookie-only HttpOnly sessions; Java 17 bytecode target, Servlet 6.0 consistent with Jetty EE10, WAR build. |
| `src/test/java/**`, `scripts/**` | Unit regression tests, standalone frontend logic checks, and opt-in live API audit. No new test library beyond existing JUnit; scripts use Node built-ins. |

## Feature verification

PASS means the stated evidence passed; it does not imply every possible failure was exercised.

| Feature | Result | Evidence / boundary |
| --- | --- | --- |
| Registration with starting budget | PASS | Live API and browser account creation; stored budget retrieved. |
| Login / incorrect password / duplicate email | PASS | HTTP tests; case-insensitive email lookup; duplicate rejected. |
| Logout and authentication required | PASS | API logout invalidates session; anonymous reads/writes rejected. Failed-logout UI regression test added. |
| Add income and expense | PASS | Real database writes and monthly totals; browser expense additions. |
| Edit amount/category/date | PASS | Browser amount/category update; API moved an expense between months. |
| Delete transaction | PASS at API; UI partially verified | Real deletion and recomputation passed. Browser confirmation caused a browser-automation timeout; final native-confirm interaction was not verified. |
| User data isolation | PASS | Second account cannot read, update, or delete first account's transactions. |
| Monthly totals, net balance and categories | PASS | Decimal calculation tests and live month separation. Net balance is selected-month income minus expenses, not an all-time bank balance. |
| Previous / next / Today | PASS in inspected code and targeted checks | Browser next/previous observed; pure-function year/month boundary tests pass. Today uses the same loader with the local current month. |
| Budget save, edit toggle, clear, independent months | PASS | Browser discard/clear and refresh; live upsert and separate-month readback. |
| Over-budget / no-budget / empty states | PASS for calculations and rendered states inspected | No division by zero; remaining amount may be negative above budget, with explicit over-budget message. |
| Description search and type/category filters | PASS | Browser filters and empty results. Filters affect the ledger list, not monthly summary totals. |
| Pagination | PASS | Six browser entries, next-page boundary and search reset. This is client-side pagination. |
| Refresh persistence | PASS | Browser refresh retains stored entries/budget and authenticated session. Month/filter state resets. |
| Invalid/null/negative/large/overprecise inputs | PASS for tested cases | Server rejects tested inputs, malformed dates, null fields and out-of-range months. Frontend native constraints supplement server validation. |
| HTML-like descriptions | PASS for tested payload | Rendered as literal text, not executable markup. |
| Print | PARTIAL | Regression verifies all filtered rows included and pagination restored. Physical print/PDF layout not verified. |
| Responsive layout | PASS for inspected viewport samples | Desktop, tablet and narrow mobile inspected; measured no horizontal document overflow. Not every browser/device was tested. |
| Console errors | No errors observed during completed UI checks | Browser tooling later stalled on native delete confirmation; that is a verification limitation. |
| Database outage | PARTIAL | Actual 503 from misconfigured process observed. Mid-write database loss was not injected. |

## CRITICAL — fix before the interview

Fixed in source:

1. **Mutation before invalid-month rejection:** create/delete/edit could commit and then return a validation failure while calculating the response. Validate the month before calling the service. Live invalid-month create/delete checks confirmed no write occurred.
2. **Mixed-month UI from overlapping requests:** the GET request counter did not cover mutation responses. Serialize dashboard requests and mark the dashboard inert/busy while one is active; month navigation is ignored until completion. Regression checks cover pending navigation.
3. **Dependency/runtime mismatch and old Jetty:** Servlet 6.1 was declared with EE10 (Servlet 6.0). Aligned API and descriptor to 6.0; updated Jetty 12.0.16 to 12.0.38 and bound the dev connector to localhost. Explicit port binding preserves `-Djetty.http.port`.
4. **JSON null treated as text:** a literal null description/name could become the string `null`. Null now remains null, duplicate null keys are rejected, and login rejects null input before database access.

Operational requirement: restart the final code in the terminal containing correct DB settings. A running Jetty process does not automatically reload Java changes. Do not demonstrate the broken 8081 instance.

## IMPORTANT — strongly recommended / understand the limits

Fixed:

- Logout used `event.currentTarget` after await; browsers clear it after event dispatch. Capture the button before await.
- Request bodies were fully concatenated before checking the size. Bounded Reader processing now stops after 16,384 characters.
- JSON-only writes strengthen cross-site request protection. Protected JSON responses use no-store and nosniff. Session cookie settings are explicit.
- Password work factor raised from 120,000 to 600,000 for new hashes. Old hashes remain readable and are not silently rewritten.
- Print previously contained only the visible page; it now includes all matching entries.
- Add and edit description lengths now both allow 160 characters. Large values and long labels wrap more safely.
- Removed the dead user-profile budget update method. Added `.env` exclusions.

Remaining trade-offs, especially before internet deployment:

- No login throttling, account lockout, MFA, password recovery or email verification. Generic invalid-login text does not replace rate limiting.
- Local HTTP is not transport encryption. Public deployment needs HTTPS, Secure/SameSite cookie configuration, proxy-origin review and a restricted DB account. Defaults still use MySQL root; never use that as the public-service account.
- A mutation commits before the follow-up dashboard read. If that later read/response fails, the caller may not know the write succeeded. Refresh before retrying; a future API should separate mutation acknowledgement/read refresh or use idempotency keys. No exactly-once guarantee.
- Same transaction edited in two tabs uses last-writer-wins. There is no optimistic version field or lost-update detection.
- Pagination/search load every transaction for the selected month. Fine for a small personal ledger; use SQL aggregation, server-side filtering and cursor pagination at scale.
- New connection per DAO call, no pool/timeouts configured by application. A larger deployment needs pooled bounded connections and measured query latency.
- SQL failures are usually reported as database unavailable, even when a different SQL error occurred. Auth logging gives SQLState/vendor code; finance failure logging remains sparse. No full production observability system exists.
- Category validation accepts any nonblank category up to 60 characters, while UI dropdowns contain a fixed set. Custom/legacy API categories display, but may not be selectable in the edit/filter UI. Demo with the offered categories; formalize a shared category policy before exposing the API externally.
- A dashboard refresh resets the quick-log date and budget editor. Save active drafts before changing month or other dashboard data.
- Low-contrast secondary text and small labels may be hard to read on a projector. Increase browser zoom for the interview; no formal WCAG contrast certification was performed.
- There are no Git commits in this workspace. The files are untracked. Do not claim commit history, CI or a verified GitHub deployment.

## Database review

`users.id` is the parent key. `transactions.user_id` and `monthly_budgets.user_id` reference it with ON DELETE CASCADE. Email has a uniqueness constraint; the `(user_id, budget_month)` primary key prevents duplicate monthly budget rows. `DECIMAL(12,2)` and backend BigDecimal avoid binary floating-point accounting arithmetic. Transactions have an amount-positive check; monthly budgets allow zero. Prepared statements parameterize user inputs. try-with-resources closes connections, statements and result sets.

The `(user_id, transaction_date)` index fits the scoped date-range query. No join is necessary because the dashboard retrieves the signed-in user's data and aggregates one month's records. Registration uses one connection and an explicit transaction for account plus initial budget. A monthly budget upsert is one atomic statement.

The legacy `users.monthly_budget` column duplicates the initial budget and is no longer the source of truth. Keep it for compatibility for now; a future migration can remove it and the unused profile field/getter after testing existing data. No migration framework is present. `CREATE TABLE IF NOT EXISTS` does not upgrade an existing table. Source DDL was reviewed; the exact live DDL, database grants, collation, query plans, cascades and direct constraint enforcement were not independently inspected with administrator credentials.

Server writes normalize budget_month to the first of the month, but DDL does not enforce that day. User name/email normalization could use Locale.ROOT and a clearer email policy. Repeated identical finance transactions are permitted intentionally; the API has no duplicate-submission key. Deletion is permanent and there is no audit trail.

## GOOD — keep as is

- Plain HTML/CSS/JavaScript, servlets, service, repositories and immutable models are enough for this scope.
- BigDecimal money arithmetic and MySQL DECIMAL.
- SQL ownership conditions, prepared statements, explicit registration transaction, and resource cleanup.
- Separate per-month budgets rather than falling back to the registration budget.
- Random salted password hashing, constant-time comparison, session-ID rotation, logout invalidation.
- Output escaping/textContent, form labels, keyboard focus, native dialog, reduced-motion support.
- Injected repositories for service tests. No need to add Spring, an ORM, React, queues, Redis or Docker just for an interview.

## OPTIONAL — nice improvements

Server-side pagination, login throttling, optimistic locking, idempotency keys and connection pooling become important with deployment/traffic. For a local portfolio demo, avoid adding them without time to understand and verify them. Smaller cleanup options: expand compressed one-line Java methods, unify duplicated validation/response code, replace the limited JSON parser with a standard library if nested payloads are needed, and remove the legacy budget column through a real migration. Do not claim any of these already exist.

## Honest claims and interview answers

**Tell me about the project.** “MoneyMap is a personal finance ledger built with Java Servlets, JDBC, MySQL and plain JavaScript. Each account can track income and expenses, maintain separate monthly budgets, and review a monthly dashboard. I used BigDecimal for calculations and scoped queries by the authenticated user.” Only say “I implemented” for work you personally understand and can reproduce; describe assisted work honestly.

**Why this stack?** “The app is small enough that servlets and JDBC make the request and database flow explicit. There are few dependencies and I can explain the SQL. Spring and an ORM would help at larger scope, but introduce conventions this project doesn't need.” Follow-up: what manual work do you handle? Routing, validation, serialization, resource management and configuration.

**What happens when I add an expense?** “The browser validates the form and sends JSON. The filter checks the request; the servlet takes the user identity from the session and validates the month. The service validates amount/date/text, the repository inserts with a prepared statement, and the server returns recomputed monthly totals.” Follow-up: what if response delivery fails? The write may already have committed; no idempotency guarantee exists.

**How do you prevent another user deleting my entry?** “The user ID comes from the server session, and DELETE includes both id and user_id. A second account test was rejected.” Hiding buttons or trusting a posted user ID would not be authorization. Follow-up: does every read also scope by user? Yes, the month query and budget queries do.

**Why BigDecimal?** “Decimal money needs exact decimal arithmetic. We parse decimal text into BigDecimal, enforce two decimal places, and store DECIMAL in MySQL. JavaScript formats the response but is not the accounting authority.” Follow-up: why not construct BigDecimal from double? That preserves the binary approximation.

**How are passwords stored?** “A random salt and PBKDF2-HMAC-SHA256 derive a hash. The stored format includes iterations, salt and hash. We compare derived hashes, not decrypted passwords.” Follow-up: is that sufficient? No: TLS, throttling, session hardening and recovery policy also matter. Existing hashes keep their recorded iteration count.

**Why a separate budget table?** “Budget is keyed by user and month, so changing October cannot overwrite September. Clearing stores zero and no fallback restores the registration amount.” Follow-up: why a composite key? It enforces one budget per user/month and supports lookup/upsert.

**What is atomic during registration?** “The user insert and initial monthly-budget insert share a JDBC connection with auto-commit disabled. Either both commit or failure triggers rollback.” Follow-up: what about two requests using the same email? The unique index is the final authority even if both prechecks pass.

**How are errors handled?** “Validation errors return JSON with 400, unauthenticated requests 401, cross-site writes 403, and database failures 503. The UI shows messages and restores controls in finally.” Follow-up: what needs improvement? More accurate SQL error classification, correlated logs, timeouts and fault-injection tests.

**How did you test it?** “JUnit covers validation/calculations/password/JSON/month parsing. A separate Node script tests live HTTP and database persistence with two disposable accounts. Browser checks cover forms, filters, edits, budgets and responsive layout. Some browser confirmation/printing paths remain manual.” Unit fakes alone do not prove JDBC authorization.

**What if two users act simultaneously?** “Different users are scoped independently. Unique keys prevent duplicate budgets/emails. Two edits of the same transaction in the same account are last-writer-wins; I would add a version column if concurrent editing mattered.”

**How would you scale to 100,000 users?** “I would measure first, move summaries/filtering/pagination into SQL, introduce a bounded connection pool, rate-limit auth, add query and error monitoring, and use a supported deployment server behind HTTPS. Sessions currently live in one JVM, so multiple instances need a session strategy.” Do not claim load testing, distributed sessions, caching or queues.

**How would you debug a demo failure?** “Check the actual URL/port and HTTP response, verify the configured server process and DB settings, inspect the server log, then reproduce the smallest request. A 404 is routing/deployment; 401 is session; 400 is input; 503 usually means database access in this app. Restart after compiled Java changes.”

## Recommended 5–7 minute demo

1. Start MySQL and one configured app instance. Log in to a fresh demo account.
2. Set a monthly budget and add salary, food and housing entries. Explain income minus expenses versus budget minus expenses.
3. Edit an amount and show category totals update. Show a rejected invalid amount.
4. Change month, set a different budget, and return. Demonstrate clear-to-zero and persistence if time permits.
5. Search/filter, use pagination with six entries, and delete one disposable entry after checking the native confirmation manually.
6. Refresh, log out, log back in. Explain sessions versus persistent MySQL data.
7. End with two honest trade-offs: client-side pagination and last-writer-wins. Keep printing out of the main demo until print preview has been rehearsed.

## Final checklist and limits

- Restart final build in the terminal with valid DB settings; use that exact port.
- Run `mvn verify`, `node scripts/test-frontend.mjs`, then `node scripts/audit-api.mjs` against the restarted app.
- Rehearse native delete confirmation and print preview manually. Browser tooling timed out while trying to interact with the confirm dialog; the API deletion itself passed.
- Source and WAR use patched Jetty/Servlet settings; verify the runtime's startup version rather than assuming a prior process updated.
- Prepare ordinary demonstration data; avoid personal finance details and real passwords on screen.
- Review FinanceServlet, FinanceService, all repositories, PasswordUtil, SameOriginFilter, app.js and schema.sql before the interview.
- Do not claim CSV export, date-range filtering, banking integration, recurring transactions, account recovery, email verification, audit history, server-side pagination, deployment, CI, high-traffic scalability or full production security. None was implemented here.
- No direct live-schema/grants review, cross-browser suite, load test, real HTTPS/proxy deployment, 30-minute session expiry wait, mid-transaction failure injection, full dependency CVE scan, physical printing, or all-device accessibility audit was performed.
- The compiler targets Java 17; tests ran on the installed JDK 25. A separate Java 17 runtime execution was not verified.
- API fixtures clean up their own transactions/budgets, but leave disposable account rows. The six synthetic browser-test transactions were also removed through the API and its session logged out. Five disposable account rows remain from this audit (two API runs and one browser account); no existing user's finance records were touched. Removed test transactions have no restore feature and contained only generated data.

Security references: [Jetty advisories](https://jetty.org/security.html) and [OWASP password storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html). Raising the work factor and updating one server dependency are not a complete security certification.
