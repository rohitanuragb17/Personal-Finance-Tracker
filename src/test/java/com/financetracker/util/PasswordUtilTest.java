package com.financetracker.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordUtilTest {
    @Test void saltsHashesAndChecksPasswords() throws Exception {
        String first = PasswordUtil.hash("Audit password 123!");
        assertNotEquals(first, PasswordUtil.hash("Audit password 123!"));
        assertTrue(PasswordUtil.matches("Audit password 123!", first));
        assertFalse(PasswordUtil.matches("incorrect", first));
    }
}
