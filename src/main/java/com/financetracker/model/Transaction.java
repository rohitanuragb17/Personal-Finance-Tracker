package com.financetracker.model;

import java.time.LocalDate;
import java.math.BigDecimal;

public class Transaction {
    public enum Type { INCOME, EXPENSE }

    private final long id;
    private final long userId;
    private final Type type;
    private final String category;
    private final BigDecimal amount;
    private final String description;
    private final LocalDate date;

    public Transaction(long userId, Type type, String category, BigDecimal amount, String description, LocalDate date) {
        this(0, userId, type, category, amount, description, date);
    }

    public Transaction(long id, long userId, Type type, String category, BigDecimal amount, String description, LocalDate date) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.category = category;
        this.amount = amount;
        this.description = description;
        this.date = date;
    }

    public long getId() { return id; }
    public long getUserId() { return userId; }
    public Type getType() { return type; }
    public String getCategory() { return category; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public LocalDate getDate() { return date; }
}
