package com.financetracker.service;

import com.financetracker.model.Transaction;
import com.financetracker.model.UserProfile;
import com.financetracker.repository.TransactionRepository;
import com.financetracker.repository.MonthlyBudgetRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FinanceService {
    private final TransactionRepository repository;
    private final MonthlyBudgetRepository budgetRepository;

    public FinanceService(TransactionRepository repository, MonthlyBudgetRepository budgetRepository) {
        this.repository = repository;
        this.budgetRepository = budgetRepository;
    }

    public Transaction addTransaction(long userId, String type, String category, BigDecimal amount, String description, String date) throws Exception {
        Transaction transaction = validatedTransaction(userId, type, category, amount, description, date);
        repository.save(transaction);
        return transaction;
    }

    public Transaction updateTransaction(long userId, long transactionId, String type, String category, BigDecimal amount, String description, String date) throws Exception {
        Transaction updated = validatedTransaction(userId, type, category, amount, description, date);
        if (!repository.update(userId, transactionId, updated)) throw new IllegalArgumentException("Transaction was not found.");
        return updated;
    }

    private Transaction validatedTransaction(long userId, String type, String category, BigDecimal amount, String description, String date) {
        if (type == null || (!type.equals("INCOME") && !type.equals("EXPENSE"))) throw new IllegalArgumentException("Type must be income or expense.");
        if (category == null || category.isBlank() || category.trim().length() > 60) throw new IllegalArgumentException("Category must be 1 to 60 characters.");
        validateAmount(amount);
        if (description == null || description.isBlank() || description.trim().length() > 160) throw new IllegalArgumentException("Description must be 1 to 160 characters.");
        try {
            LocalDate parsedDate = LocalDate.parse(date);
            if (parsedDate.getYear() < 1000 || parsedDate.getYear() > 9999) throw new IllegalArgumentException();
            return new Transaction(userId, Transaction.Type.valueOf(type), category.trim(), amount, description.trim(), parsedDate);
        }
        catch (Exception exception) { throw new IllegalArgumentException("Date must use YYYY-MM-DD format."); }
    }

    public Map<String, Object> dashboard(long userId, UserProfile profile, YearMonth month) throws Exception {
        BigDecimal income = BigDecimal.ZERO, expenses = BigDecimal.ZERO;
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        List<Transaction> transactions = repository.findAll(userId, month);
        BigDecimal savedBudget = budgetRepository.find(userId, month);
        // A missing month-specific row means no budget was set for that month.
        // Do not fall back to the registration budget, or a cleared budget reappears.
        BigDecimal monthlyBudget = savedBudget == null ? BigDecimal.ZERO : savedBudget;
        for (Transaction transaction : transactions) {
            if (transaction.getType() == Transaction.Type.INCOME) income = income.add(transaction.getAmount());
            else { expenses = expenses.add(transaction.getAmount()); byCategory.merge(transaction.getCategory(), transaction.getAmount(), BigDecimal::add); }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profile", Map.of("name", profile.getName(), "monthlyBudget", monthlyBudget));
        result.put("month", month.toString());
        result.put("income", income);
        result.put("expenses", expenses);
        result.put("balance", income.subtract(expenses));
        result.put("spendingByCategory", byCategory);
        result.put("transactions", transactions);
        return result;
    }

    public void deleteTransaction(long userId, long transactionId) throws Exception {
        if (transactionId <= 0 || !repository.delete(userId, transactionId)) throw new IllegalArgumentException("Transaction was not found.");
    }

    public void saveBudget(long userId, YearMonth month, BigDecimal amount) throws Exception {
        if (amount == null || amount.signum() < 0 || amount.compareTo(new BigDecimal("100000000")) > 0 || amount.scale() > 2) throw new IllegalArgumentException("Budget must be between 0 and 100,000,000 with up to 2 decimal places.");
        budgetRepository.save(userId, month, amount);
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.compareTo(new BigDecimal("100000000")) > 0 || amount.scale() > 2) throw new IllegalArgumentException("Amount must be between 0.01 and 100,000,000 with up to 2 decimal places.");
    }
}
