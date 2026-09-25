package com.financetracker.web;

import com.financetracker.model.UserProfile;
import com.financetracker.repository.UserRepository;
import com.financetracker.util.PasswordUtil;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.YearMonth;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Map;

public class AuthServlet extends HttpServlet {
    private final UserRepository users = new UserRepository();
    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String action = request.getPathInfo();
        try {
            if ("/logout".equals(action)) { HttpSession session = request.getSession(false); if (session != null) session.invalidate(); send(response, Map.of("message", "Logged out"), 200); return; }
            Map<String, String> values = JsonUtil.readObject(request.getReader());
            if ("/register".equals(action)) register(values, request);
            else if ("/login".equals(action)) login(values, request);
            else throw new IllegalArgumentException("Unknown authentication action.");
            UserProfile profile = (UserProfile) request.getSession().getAttribute("user");
            send(response, Map.of("name", profile.getName(), "email", profile.getEmail()), 200);
        } catch (IllegalArgumentException exception) { send(response, Map.of("error", exception.getMessage()), 400); }
        catch (SQLException exception) {
            log("Authentication database failure: SQLState=" + exception.getSQLState() + ", code=" + exception.getErrorCode());
            if ("23000".equals(exception.getSQLState()) && "/register".equals(action)) send(response, Map.of("error", "An account with this email already exists."), 409);
            else send(response, Map.of("error", "Database is unavailable. Start MySQL, import schema.sql, and check DB_URL, DB_USER, and DB_PASSWORD."), 503);
        }
        catch (Exception exception) { log("Authentication failure: " + exception.getClass().getName()); send(response, Map.of("error", "Authentication failed."), 500); }
    }
    private void register(Map<String, String> values, HttpServletRequest request) throws Exception {
        String name = values.get("name"), email = values.get("email"), password = values.get("password");
        BigDecimal budget;
        try { budget = new BigDecimal(values.getOrDefault("monthlyBudget", "3000")); }
        catch (NumberFormatException | NullPointerException exception) { throw new IllegalArgumentException("Enter a valid monthly budget."); }
        if (name == null || name.isBlank() || name.trim().length() > 80 || email == null || email.length() > 190 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || password == null || password.length() < 8 || password.length() > 1024 || budget.signum() < 0 || budget.compareTo(new BigDecimal("100000000")) > 0 || budget.scale() > 2) throw new IllegalArgumentException("Enter a valid name, email, password (8-1024 characters), and budget between 0 and 100,000,000 with up to 2 decimals.");
        if (users.findByEmail(email) != null) throw new IllegalArgumentException("An account with this email already exists.");
        UserProfile profile = users.createWithInitialBudget(name, email, password, budget, YearMonth.now());
        startSession(request, profile);
    }
    private void login(Map<String, String> values, HttpServletRequest request) throws Exception {
        String email = values.get("email"), password = values.get("password");
        if (email == null || email.isBlank() || email.length() > 190 || password == null || password.isEmpty() || password.length() > 1024) throw new IllegalArgumentException("Invalid email or password.");
        UserRepository.UserRecord record = users.findByEmail(email);
        if (record == null || !PasswordUtil.matches(password, record.passwordHash())) throw new IllegalArgumentException("Invalid email or password.");
        startSession(request, record.profile());
    }
    private void startSession(HttpServletRequest request, UserProfile profile) {
        request.getSession(true);
        request.changeSessionId();
        request.getSession().setAttribute("user", profile);
    }
    private void send(HttpServletResponse response, Map<String, String> data, int status) throws IOException { response.setStatus(status); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8"); response.getWriter().write(JsonUtil.toJson(data)); }
}
