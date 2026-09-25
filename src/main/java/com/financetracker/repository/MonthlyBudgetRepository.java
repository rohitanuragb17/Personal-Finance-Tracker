package com.financetracker.repository;

import com.financetracker.config.Database;
import java.sql.*;
import java.time.YearMonth;
import java.math.BigDecimal;

public class MonthlyBudgetRepository {
    public BigDecimal find(long userId, YearMonth month) throws SQLException {
        try (Connection connection = Database.getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT amount FROM monthly_budgets WHERE user_id = ? AND budget_month = ?")) {
            statement.setLong(1, userId); statement.setDate(2, Date.valueOf(month.atDay(1)));
            try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getBigDecimal("amount") : null; }
        }
    }
    public void save(long userId, YearMonth month, BigDecimal amount) throws SQLException {
        String sql = "INSERT INTO monthly_budgets(user_id, budget_month, amount) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE amount = VALUES(amount)";
        try (Connection connection = Database.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId); statement.setDate(2, Date.valueOf(month.atDay(1))); statement.setBigDecimal(3, amount); statement.executeUpdate();
        }
    }
}
