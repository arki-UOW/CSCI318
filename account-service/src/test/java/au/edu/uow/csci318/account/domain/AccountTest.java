package au.edu.uow.csci318.account.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountTest {
    @Test
    void normalisesUsernamesAndStoresProfileTheme() {
        Account account = new Account("Student.One", "bcrypt-hash", "Student One", "Australia/Sydney");
        account.updateProfile("Arkajit", "UOW", "CSCI318", "Finish the semester strongly", "Australia/Sydney");
        account.updateTheme("#123456", "#abcdef", "#f4f4f4", "#ffffff", "#111111");
        assertEquals("student.one", account.getUsername());
        assertEquals("UOW", account.getInstitution());
        assertEquals("#123456", account.getPrimaryColor());
    }

    @Test
    void rejectsWeakIdentityFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new Account("x", "hash", "Name", "Australia/Sydney"));
        Account account = new Account("valid_user", "hash", "Name", "Australia/Sydney");
        assertThrows(IllegalArgumentException.class,
                () -> account.updateTheme("green", "#abcdef", "#ffffff", "#ffffff", "#111111"));
    }
}
