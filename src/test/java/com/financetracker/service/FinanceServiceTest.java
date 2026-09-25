package com.financetracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.financetracker.model.Transaction;
import com.financetracker.model.UserProfile;
import com.financetracker.repository.MonthlyBudgetRepository;
import com.financetracker.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FinanceServiceTest {
    @Test void rejectsEmptyFieldsInvalidDatesAndInvalidBudgets() {
        FinanceService service = new FinanceService(new FakeTransactionRepository(), new FakeBudgetRepository());
        for (String date : new String[]{"2026-02-30", "0999-01-01", "", null}) {
            assertThrows(IllegalArgumentException.class, () -> service.addTransaction(1, "EXPENSE", "Food", BigDecimal.ONE, "Lunch", date));
        }
        assertThrows(IllegalArgumentException.class, () -> service.addTransaction(1, "EXPENSE", "Food", BigDecimal.ONE, null, "2026-09-01"));
        assertThrows(IllegalArgumentException.class, () -> service.addTransaction(1, "EXPENSE", "Food", BigDecimal.ONE, "   ", "2026-09-01"));
        for (BigDecimal value : new BigDecimal[]{null, new BigDecimal("-1"), new BigDecimal("100000001"), new BigDecimal("0.001")}) {
            assertThrows(IllegalArgumentException.class, () -> service.saveBudget(1, YearMonth.of(2026, 9), value));
        }
    }

    @Test void clearingBudgetDoesNotRestoreRegistrationBudget() throws Exception {
        FinanceService service = new FinanceService(new FakeTransactionRepository(), new FakeBudgetRepository());
        YearMonth month = YearMonth.of(2026, 9);
        service.saveBudget(1, month, new BigDecimal("45000"));
        service.saveBudget(1, month, BigDecimal.ZERO);
        Map<?, ?> profile = (Map<?, ?>) service.dashboard(1, new UserProfile(1, "Audit", "audit@example.invalid", new BigDecimal("45000")), month).get("profile");
        assertEquals(BigDecimal.ZERO, profile.get("monthlyBudget"));
    }
    @Test
    void dashboardUsesOnlyTheSelectedMonthsBudget() throws Exception {
        FakeTransactionRepository transactions = new FakeTransactionRepository();
        FakeBudgetRepository budgets = new FakeBudgetRepository();
        YearMonth september = YearMonth.of(2026, 9);
        YearMonth october = YearMonth.of(2026, 10);
        budgets.save(1, september, new BigDecimal("45000"));
        budgets.save(1, october, new BigDecimal("50000"));

        FinanceService service = new FinanceService(transactions, budgets);
        UserProfile profile = new UserProfile(1, "Rohit", "rohit@example.com", new BigDecimal("3000"));

        Map<String, Object> result = service.dashboard(1, profile, september);
        Map<?, ?> resultProfile = (Map<?, ?>) result.get("profile");
        assertEquals(new BigDecimal("45000"), resultProfile.get("monthlyBudget"));

        result = service.dashboard(1, profile, YearMonth.of(2026, 11));
        resultProfile = (Map<?, ?>) result.get("profile");
        assertEquals(BigDecimal.ZERO, resultProfile.get("monthlyBudget"));
    }

    @Test
    void dashboardCalculatesMonthlyTotalsAndCategories() throws Exception {
        FakeTransactionRepository transactions = new FakeTransactionRepository();
        transactions.items.add(new Transaction(1, Transaction.Type.INCOME, "Salary", new BigDecimal("50000"), "September salary", LocalDate.of(2026, 9, 1)));
        transactions.items.add(new Transaction(1, Transaction.Type.EXPENSE, "Food", new BigDecimal("250.50"), "Groceries", LocalDate.of(2026, 9, 2)));
        transactions.items.add(new Transaction(1, Transaction.Type.EXPENSE, "Food", new BigDecimal("99.50"), "Lunch", LocalDate.of(2026, 9, 3)));

        FinanceService service = new FinanceService(transactions, new FakeBudgetRepository());
        Map<String, Object> result = service.dashboard(1, new UserProfile(1, "Rohit", "rohit@example.com", BigDecimal.ZERO), YearMonth.of(2026, 9));

        assertEquals(new BigDecimal("50000"), result.get("income"));
        assertEquals(new BigDecimal("350.00"), result.get("expenses"));
        assertEquals(new BigDecimal("49650.00"), result.get("balance"));
        assertEquals(new BigDecimal("350.00"), ((Map<?, ?>) result.get("spendingByCategory")).get("Food"));
    }

    @Test
    void rejectsNegativeAndOverPreciseTransactionAmounts() {
        FinanceService service = new FinanceService(new FakeTransactionRepository(), new FakeBudgetRepository());

        assertThrows(IllegalArgumentException.class, () -> service.addTransaction(1, "EXPENSE", "Food", new BigDecimal("-1"), "Lunch", "2026-09-01"));
        assertThrows(IllegalArgumentException.class, () -> service.addTransaction(1, "EXPENSE", "Food", new BigDecimal("1.999"), "Lunch", "2026-09-01"));
    }

    @Test
    void preventsAUserFromEditingOrDeletingAnotherUsersTransaction() {
        FakeTransactionRepository transactions = new FakeTransactionRepository();
        transactions.owners.put(10L, 2L);
        FinanceService service = new FinanceService(transactions, new FakeBudgetRepository());

        assertThrows(IllegalArgumentException.class, () -> service.deleteTransaction(1, 10));
        assertThrows(IllegalArgumentException.class, () -> service.updateTransaction(1, 10, "EXPENSE", "Food", new BigDecimal("100"), "Lunch", "2026-09-01"));
    }

    private static class FakeTransactionRepository extends TransactionRepository {
        private final List<Transaction> items = new ArrayList<>();
        private final Map<Long, Long> owners = new HashMap<>();

        @Override public List<Transaction> findAll(long userId, YearMonth month) {
            return items.stream().filter(item -> item.getUserId() == userId && YearMonth.from(item.getDate()).equals(month)).toList();
        }

        @Override public void save(Transaction transaction) { items.add(transaction); }

        @Override public boolean update(long userId, long transactionId, Transaction transaction) { return owners.getOrDefault(transactionId, userId).equals(userId); }

        @Override public boolean delete(long userId, long transactionId) { return owners.getOrDefault(transactionId, userId).equals(userId); }
    }

    private static class FakeBudgetRepository extends MonthlyBudgetRepository {
        private final Map<String, BigDecimal> values = new HashMap<>();

        @Override public BigDecimal find(long userId, YearMonth month) { return values.get(userId + ":" + month); }

        @Override public void save(long userId, YearMonth month, BigDecimal amount) { values.put(userId + ":" + month, amount); }
    }
}
