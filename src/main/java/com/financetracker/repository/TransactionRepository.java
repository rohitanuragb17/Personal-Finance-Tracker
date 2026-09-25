package com.financetracker.repository;

import com.financetracker.config.Database;
import com.financetracker.model.Transaction;
import java.sql.*;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public class TransactionRepository {
    public List<Transaction> findAll(long userId, YearMonth month) throws SQLException {
        List<Transaction> transactions = new ArrayList<>();
        String sql = "SELECT id, user_id, type, category, amount, description, transaction_date FROM transactions WHERE user_id = ? AND transaction_date >= ? AND transaction_date <= ? ORDER BY transaction_date DESC, id DESC";
        try (Connection connection = Database.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setDate(2, Date.valueOf(month.atDay(1)));
            statement.setDate(3, Date.valueOf(month.atEndOfMonth()));
            try (ResultSet result = statement.executeQuery()) { while (result.next()) transactions.add(read(result)); }
        }
        return transactions;
    }
    public boolean update(long userId, long transactionId, Transaction transaction) throws SQLException {
        String sql = "UPDATE transactions SET type = ?, category = ?, amount = ?, description = ?, transaction_date = ? WHERE id = ? AND user_id = ?";
        try (Connection connection = Database.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, transaction.getType().name()); statement.setString(2, transaction.getCategory()); statement.setBigDecimal(3, transaction.getAmount()); statement.setString(4, transaction.getDescription()); statement.setDate(5, Date.valueOf(transaction.getDate())); statement.setLong(6, transactionId); statement.setLong(7, userId);
            return statement.executeUpdate() == 1;
        }
    }
    public void save(Transaction transaction) throws SQLException {
        String sql = "INSERT INTO transactions(user_id, type, category, amount, description, transaction_date) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = Database.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, transaction.getUserId()); statement.setString(2, transaction.getType().name()); statement.setString(3, transaction.getCategory()); statement.setBigDecimal(4, transaction.getAmount()); statement.setString(5, transaction.getDescription()); statement.setDate(6, Date.valueOf(transaction.getDate())); statement.executeUpdate();
        }
    }
    public boolean delete(long userId, long transactionId) throws SQLException {
        String sql = "DELETE FROM transactions WHERE id = ? AND user_id = ?";
        try (Connection connection = Database.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, transactionId); statement.setLong(2, userId);
            return statement.executeUpdate() == 1;
        }
    }
    private Transaction read(ResultSet result) throws SQLException { return new Transaction(result.getLong("id"), result.getLong("user_id"), Transaction.Type.valueOf(result.getString("type")), result.getString("category"), result.getBigDecimal("amount"), result.getString("description"), result.getDate("transaction_date").toLocalDate()); }
}
