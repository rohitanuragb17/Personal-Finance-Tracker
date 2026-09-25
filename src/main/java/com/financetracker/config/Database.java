package com.financetracker.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class Database {
    private Database() { }
    public static Connection getConnection() throws SQLException {
        String url = env("DB_URL", "jdbc:mysql://localhost:3306/finance_tracker?serverTimezone=UTC");
        return DriverManager.getConnection(url, env("DB_USER", "root"), env("DB_PASSWORD", ""));
    }
    private static String env(String name, String fallback) { String value = System.getenv(name); return value == null || value.isBlank() ? fallback : value; }
}
