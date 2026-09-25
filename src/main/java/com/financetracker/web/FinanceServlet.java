package com.financetracker.web;

import com.financetracker.model.UserProfile;
import com.financetracker.repository.TransactionRepository;
import com.financetracker.repository.MonthlyBudgetRepository;
import com.financetracker.service.FinanceService;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.YearMonth;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Map;

public class FinanceServlet extends HttpServlet {
    private FinanceService service;
    @Override public void init() { service = new FinanceService(new TransactionRepository(), new MonthlyBudgetRepository()); }

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!dashboardPath(request)) { sendJson(response, "{\"error\":\"Endpoint not found.\"}", 404); return; }
        try { UserProfile user = currentUser(request); sendJson(response, JsonUtil.toJson(service.dashboard(user.getId(), user, requestedMonth(request))), 200); }
        catch (IllegalArgumentException exception) { sendJson(response, JsonUtil.toJson(Map.of("error", exception.getMessage())), 400); }
        catch (IllegalStateException exception) { sendJson(response, JsonUtil.toJson(Map.of("error", exception.getMessage())), 401); }
        catch (SQLException exception) { sendJson(response, JsonUtil.toJson(Map.of("error", "Database is unavailable. Start MySQL, import schema.sql, and check DB_URL, DB_USER, and DB_PASSWORD.")), 503); }
        catch (Exception exception) { sendJson(response, JsonUtil.toJson(Map.of("error", "Unable to load dashboard.")), 500); }
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!dashboardPath(request)) { sendJson(response, "{\"error\":\"Endpoint not found.\"}", 404); return; }
        try {
            UserProfile user = currentUser(request);
            YearMonth month = requestedMonth(request);
            Map<String, String> values = JsonUtil.readObject(request.getReader());
            service.addTransaction(user.getId(), values.get("type"), values.get("category"), amount(values.get("amount")), values.get("description"), values.get("date"));
            sendJson(response, JsonUtil.toJson(service.dashboard(user.getId(), user, month)), 201);
        } catch (IllegalArgumentException exception) {
            sendJson(response, JsonUtil.toJson(Map.of("error", exception.getMessage())), 400);
        } catch (IllegalStateException exception) { sendJson(response, JsonUtil.toJson(Map.of("error", exception.getMessage())), 401);
        } catch (SQLException exception) { sendJson(response, JsonUtil.toJson(Map.of("error", "Database is unavailable. Start MySQL, import schema.sql, and check DB_URL, DB_USER, and DB_PASSWORD.")), 503);
        } catch (Exception exception) {
            sendJson(response, JsonUtil.toJson(Map.of("error", "Unable to save transaction.")), 500);
        }
    }

    @Override protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            UserProfile user = currentUser(request);
            String path = request.getPathInfo();
            YearMonth month = requestedMonth(request);
            long transactionId = parseTransactionId(path);
            service.deleteTransaction(user.getId(), transactionId);
            sendJson(response, JsonUtil.toJson(service.dashboard(user.getId(), user, month)), 200);
        } catch (IllegalArgumentException exception) { sendJson(response, JsonUtil.toJson(Map.of("error", exception.getMessage())), 400); }
        catch (IllegalStateException exception) { sendJson(response, JsonUtil.toJson(Map.of("error", exception.getMessage())), 401); }
        catch (SQLException exception) { sendJson(response, JsonUtil.toJson(Map.of("error", "Database is unavailable. Start MySQL, import schema.sql, and check DB_URL, DB_USER, and DB_PASSWORD.")), 503); }
        catch (Exception exception) { sendJson(response, JsonUtil.toJson(Map.of("error", "Unable to delete transaction.")), 500); }
    }

    @Override protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            UserProfile user = currentUser(request);
            YearMonth month = requestedMonth(request);
            Map<String, String> values = JsonUtil.readObject(request.getReader());
            if ("/budget".equals(request.getPathInfo())) {
                BigDecimal budget = amount(values.get("monthlyBudget"));
                service.saveBudget(user.getId(), month, budget);
                sendJson(response, JsonUtil.toJson(service.dashboard(user.getId(), user, month)), 200);
            } else {
                long transactionId = parseTransactionId(request.getPathInfo());
                service.updateTransaction(user.getId(), transactionId, values.get("type"), values.get("category"), amount(values.get("amount")), values.get("description"), values.get("date"));
                sendJson(response, JsonUtil.toJson(service.dashboard(user.getId(), user, month)), 200);
            }
        } catch (IllegalArgumentException exception) { sendJson(response, JsonUtil.toJson(Map.of("error", exception.getMessage())), 400); }
        catch (IllegalStateException exception) { sendJson(response, JsonUtil.toJson(Map.of("error", exception.getMessage())), 401); }
        catch (SQLException exception) { sendJson(response, JsonUtil.toJson(Map.of("error", "Database is unavailable. Start MySQL, import schema.sql, and check DB_URL, DB_USER, and DB_PASSWORD.")), 503); }
        catch (Exception exception) { sendJson(response, JsonUtil.toJson(Map.of("error", "Unable to update the requested data.")), 500); }
    }

    private boolean dashboardPath(HttpServletRequest request) { return request.getPathInfo() == null || "/".equals(request.getPathInfo()); }
    private UserProfile currentUser(HttpServletRequest request) { var session = request.getSession(false); UserProfile user = session == null ? null : (UserProfile) session.getAttribute("user"); if (user == null) throw new IllegalStateException("Please log in first."); return user; }
    private BigDecimal amount(String value) {
        try { return new BigDecimal(value); }
        catch (NumberFormatException | NullPointerException exception) { throw new IllegalArgumentException("Enter a valid amount."); }
    }
    static YearMonth parseMonth(String value) {
        try {
            if (value == null) return YearMonth.now();
            if (!value.matches("[0-9]{4}-[0-9]{2}")) throw new IllegalArgumentException();
            YearMonth month = YearMonth.parse(value);
            if (month.getYear() < 1000 || month.getYear() > 9999) throw new IllegalArgumentException();
            return month;
        } catch (Exception exception) { throw new IllegalArgumentException("Month must use YYYY-MM format, with a year from 1000 to 9999."); }
    }
    private YearMonth requestedMonth(HttpServletRequest request) { return parseMonth(request.getParameter("month")); }
    private long parseTransactionId(String path) { if (path == null || !path.startsWith("/transactions/")) throw new IllegalArgumentException("Transaction path is invalid."); try { return Long.parseLong(path.substring("/transactions/".length())); } catch (NumberFormatException exception) { throw new IllegalArgumentException("Transaction ID is invalid."); } }

    private void sendJson(HttpServletResponse response, String body, int status) throws IOException {
        response.setStatus(status); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8"); response.getWriter().write(body);
    }
}
