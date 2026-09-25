package com.financetracker.web;

import org.junit.jupiter.api.Test;
import java.time.YearMonth;
import static org.junit.jupiter.api.Assertions.*;

class FinanceServletTest {
    @Test void validatesDatabaseCompatibleMonths() {
        assertEquals(YearMonth.of(2026, 9), FinanceServlet.parseMonth("2026-09"));
        assertEquals(YearMonth.of(9999, 12), FinanceServlet.parseMonth("9999-12"));
        for (String value : new String[]{"0999-12", "2026-13", "2026-9", "+10000-01", "nonsense"}) {
            assertThrows(IllegalArgumentException.class, () -> FinanceServlet.parseMonth(value));
        }
    }
}
