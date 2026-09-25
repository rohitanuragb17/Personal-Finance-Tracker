package com.financetracker.model;

import java.math.BigDecimal;

public class UserProfile {
    private final long id;
    private final String email;
    private final String name;
    private final BigDecimal monthlyBudget;

    public UserProfile(long id, String name, String email, BigDecimal monthlyBudget) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.monthlyBudget = monthlyBudget;
    }

    public long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public BigDecimal getMonthlyBudget() { return monthlyBudget; }
}
