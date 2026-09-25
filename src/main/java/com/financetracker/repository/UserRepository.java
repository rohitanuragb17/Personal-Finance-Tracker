package com.financetracker.repository;

import com.financetracker.config.Database;
import com.financetracker.model.UserProfile;
import com.financetracker.util.PasswordUtil;
import java.sql.*;
import java.math.BigDecimal;
import java.time.YearMonth;

public class UserRepository {
    public UserRecord findByEmail(String email) throws SQLException {
        String sql = "SELECT id, name, email, password_hash, monthly_budget FROM users WHERE email = ?";
        try (Connection connection = Database.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email.toLowerCase().trim());
            try (ResultSet result = statement.executeQuery()) { return result.next() ? read(result) : null; }
        }
    }
    public UserProfile createWithInitialBudget(String name, String email, String password, BigDecimal budget, YearMonth month) throws Exception {
        String userSql = "INSERT INTO users(name, email, password_hash, monthly_budget) VALUES (?, ?, ?, ?)";
        String budgetSql = "INSERT INTO monthly_budgets(user_id, budget_month, amount) VALUES (?, ?, ?)";
        String cleanedName = name.trim();
        String cleanedEmail = email.toLowerCase().trim();
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement userStatement = connection.prepareStatement(userSql, Statement.RETURN_GENERATED_KEYS)) {
                userStatement.setString(1, cleanedName); userStatement.setString(2, cleanedEmail); userStatement.setString(3, PasswordUtil.hash(password)); userStatement.setBigDecimal(4, budget); userStatement.executeUpdate();
                try (ResultSet keys = userStatement.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Could not create the account.");
                    long userId = keys.getLong(1);
                    try (PreparedStatement budgetStatement = connection.prepareStatement(budgetSql)) {
                        budgetStatement.setLong(1, userId); budgetStatement.setDate(2, Date.valueOf(month.atDay(1))); budgetStatement.setBigDecimal(3, budget); budgetStatement.executeUpdate();
                    }
                    connection.commit();
                    return new UserProfile(userId, cleanedName, cleanedEmail, budget);
                }
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }
    private UserRecord read(ResultSet result) throws SQLException { return new UserRecord(new UserProfile(result.getLong("id"), result.getString("name"), result.getString("email"), result.getBigDecimal("monthly_budget")), result.getString("password_hash")); }
    public record UserRecord(UserProfile profile, String passwordHash) { }
}
