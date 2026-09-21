const { test } = require('node:test');
const assert = require('node:assert/strict');
const BrowserTimezone = require('../browser-timezone.js');

function fakeIntl(detected, valid = new Set([detected])) {
  return {
    DateTimeFormat: function DateTimeFormat(locale, options = {}) {
      return {
        resolvedOptions: () => ({ timeZone: detected }),
        format: () => {
          if (options.timeZone && !valid.has(options.timeZone)) throw new RangeError('Invalid zone');
          return 'date';
        }
      };
    }
  };
}

test('automatic accounts use the browser timezone instead of stored UTC', () => {
  const intl = fakeIntl('Australia/Sydney', new Set(['Australia/Sydney', 'UTC']));
  assert.equal(
    BrowserTimezone.effective({ timezone: 'UTC', timezoneAutomatic: true }, intl),
    'Australia/Sydney'
  );
});

test('a manually saved valid timezone remains respected', () => {
  const intl = fakeIntl('Australia/Sydney', new Set(['Australia/Sydney', 'Europe/Paris']));
  assert.equal(
    BrowserTimezone.effective(
      { timezone: 'Europe/Paris', timezoneAutomatic: false },
      intl
    ),
    'Europe/Paris'
  );
});

test('invalid or unavailable browser timezone falls back safely', () => {
  assert.equal(BrowserTimezone.detected(fakeIntl('', new Set())), 'UTC');
});
