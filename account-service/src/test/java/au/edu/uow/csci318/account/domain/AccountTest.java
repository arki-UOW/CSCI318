package au.edu.uow.csci318.account.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class AccountTest {
  @Test
  void normalisesUsernamesAndStoresProfileTheme() {
    Account account = new Account("Student.One", "bcrypt-hash", "Student One", "Australia/Sydney");
    assertTrue(account.isTimezoneAutomatic());
    account.useDetectedTimezone("America/New_York");
    assertEquals("America/New_York", account.getTimezone());
    account.updateProfile(
        "Arkajit", "UOW", "CSCI318", "Finish the semester strongly", "Australia/Sydney");
    account.updateTheme("#123456", "#abcdef", "#f4f4f4", "#ffffff", "#111111", "#654321");
    account.updateNavigationOrder("dashboard,subjects");
    account.updateProfilePicture("data:image/png;base64,aGVsbG8=");
    assertEquals("student.one", account.getUsername());
    assertEquals("UOW", account.getInstitution());
    assertEquals("#123456", account.getPrimaryColor());
    assertEquals("#654321", account.getNavigationColor());
    assertEquals("dashboard,subjects", account.getNavigationOrder());
    assertEquals("data:image/png;base64,aGVsbG8=", account.getProfilePicture());
    assertFalse(account.isTimezoneAutomatic());
    account.useDetectedTimezone("America/New_York");
    assertEquals("Australia/Sydney", account.getTimezone());
  }

  @Test
  void rejectsWeakIdentityFields() {
    assertThrows(
        IllegalArgumentException.class, () -> new Account("x", "hash", "Name", "Australia/Sydney"));
    Account account = new Account("valid_user", "hash", "Name", "Australia/Sydney");
    assertThrows(
        IllegalArgumentException.class,
        () -> account.updateTheme("green", "#abcdef", "#ffffff", "#ffffff", "#111111"));
    assertThrows(
        IllegalArgumentException.class,
        () -> account.updateProfilePicture("data:text/html;base64,PHNjcmlwdD4="));
  }

  @Test
  void acceptsProfilePicturesUpToTenMegabytes() {
    Account account = new Account("valid_user", "hash", "Name", "Australia/Sydney");
    String maximum =
        "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(new byte[10 * 1024 * 1024]);
    account.updateProfilePicture(maximum);
    assertEquals(maximum, account.getProfilePicture());

    String tooLarge =
        "data:image/jpeg;base64,"
            + Base64.getEncoder().encodeToString(new byte[10 * 1024 * 1024 + 1]);
    assertThrows(IllegalArgumentException.class, () -> account.updateProfilePicture(tooLarge));
  }
}
