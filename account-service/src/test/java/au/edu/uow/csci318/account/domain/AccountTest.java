package au.edu.uow.csci318.account.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountTest {
    @Test
    void normalisesUsernamesAndStoresProfileTheme() {
        Account account = new Account("Student.One", "bcrypt-hash", "Student One", "Australia/Sydney");
        account.updateProfile("Arkajit", "UOW", "CSCI318", "Finish the semester strongly", "Australia/Sydney");
        account.updateTheme("#123456", "#abcdef", "#f4f4f4", "#ffffff", "#111111", "#654321");
        account.updateNavigationOrder("dashboard,subjects");
        account.updateProfilePicture("data:image/png;base64,aGVsbG8=");
        assertEquals("student.one", account.getUsername());
        assertEquals("UOW", account.getInstitution());
        assertEquals("#123456", account.getPrimaryColor());
        assertEquals("#654321", account.getNavigationColor());
        assertEquals("dashboard,subjects", account.getNavigationOrder());
        assertEquals("data:image/png;base64,aGVsbG8=", account.getProfilePicture());
    }

    @Test
    void rejectsWeakIdentityFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new Account("x", "hash", "Name", "Australia/Sydney"));
        Account account = new Account("valid_user", "hash", "Name", "Australia/Sydney");
        assertThrows(IllegalArgumentException.class,
                () -> account.updateTheme("green", "#abcdef", "#ffffff", "#ffffff", "#111111"));
        assertThrows(IllegalArgumentException.class,
                () -> account.updateProfilePicture("data:text/html;base64,PHNjcmlwdD4="));
    }
}
