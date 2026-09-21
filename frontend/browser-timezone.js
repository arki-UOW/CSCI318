class BrowserTimezone {
  static detected(intl = Intl) {
    try {
      const zone = intl.DateTimeFormat().resolvedOptions().timeZone;
      if (!zone) return 'UTC';
      new intl.DateTimeFormat('en-AU', { timeZone: zone }).format();
      return zone;
    } catch {
      return 'UTC';
    }
  }

  static effective(account, intl = Intl) {
    const detected = BrowserTimezone.detected(intl);
    if (account?.timezoneAutomatic !== false) return detected;
    try {
      new intl.DateTimeFormat('en-AU', { timeZone: account.timezone }).format();
      return account.timezone;
    } catch {
      return detected;
    }
  }
}

if (typeof globalThis !== 'undefined') globalThis.BrowserTimezone = BrowserTimezone;
if (typeof module !== 'undefined') module.exports = BrowserTimezone;
