# MoneyMap — Personal Finance Tracker

A Java Servlet and MySQL personal-finance application. It supports account registration, monthly budgets, income and expense tracking, category insights, transaction editing and deletion, search, filters, and pagination.

## Before the demo

Install Java 17, MySQL 8+, and Maven. Confirm MySQL is running before starting the app.

Create the database and tables from PowerShell:

```powershell
Get-Content .\schema.sql -Raw | mysql -u root -p
```

Set database settings in the same PowerShell window. Keep the real password private.

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/finance_tracker?serverTimezone=UTC"
$env:DB_USER="root"
$env:DB_PASSWORD="YOUR_MYSQL_PASSWORD"
```

Run the checks and start the app:

```powershell
mvn test
mvn "-Djetty.http.port=8080" jetty:run
```

Open [http://localhost:8080](http://localhost:8080). If port 8080 is occupied, choose an unused port and use that number in both the command and browser address. The development connector listens on `127.0.0.1` only.

After Java, `pom.xml`, or `web.xml` changes, stop Jetty with `Ctrl+C` and rerun the command in the same terminal. An existing process does not automatically load those changes. A page loading successfully does not prove the database connection works: log in to verify it.

## Audit checks

The optional audit scripts require Node.js 18 or newer. Node is not required to run MoneyMap itself.

```powershell
mvn verify
node scripts/test-frontend.mjs
# With the app and MySQL running:
node scripts/audit-api.mjs
```

The live API audit creates two disposable `audit-...@example.invalid` accounts per run. It removes its transactions, clears its tested monthly budgets, and logs out. The account rows remain because the app has no account-deletion feature. It never uses an existing account. Set `AUDIT_URL` to use a different local port.

See [INTERVIEW_AUDIT.md](INTERVIEW_AUDIT.md) for findings, verification limits, and interview preparation.

## Interview demo checklist

1. Create a fresh account with a monthly budget.
2. Add an income and two expenses in separate categories.
3. Show the monthly totals, category allocation, and budget progress.
4. Change month, set a different budget, then return to show that each month remains separate.
5. Search, edit, and delete an expense.
6. Log out and log back in to show that data is persistent.

Stop Jetty with `Ctrl+C` in the PowerShell window running it.
