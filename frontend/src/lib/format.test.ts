import test from 'node:test';
import assert from 'node:assert/strict';
import { formatAmount, formatMinutes, localInputToIso, priorityLabel, slaLabel, statusLabel, toLocalInputValue } from './format';

test('formatMinutes', () => {
  assert.equal(formatMinutes(0), '0m');
  assert.equal(formatMinutes(45), '45m');
  assert.equal(formatMinutes(60), '1h');
  assert.equal(formatMinutes(125), '2h 5m');
  assert.equal(formatMinutes(25 * 60), '1d 1h');
  assert.equal(formatMinutes(null), '—');
  assert.equal(formatMinutes(-5), '0m');
});

test('formatAmount', () => {
  assert.equal(formatAmount(1.5), '1.50');
  assert.equal(formatAmount(0), '0.00');
  assert.equal(formatAmount(undefined), '—');
});

test('labels fall back to the raw value for unknown input', () => {
  assert.equal(statusLabel('IN_PROGRESS'), 'In progress');
  assert.equal(statusLabel('WEIRD'), 'WEIRD');
  assert.equal(priorityLabel('CRITICAL'), 'Critical');
  assert.equal(slaLabel('AT_RISK'), 'At risk');
});

test('datetime-local round trip', () => {
  const d = new Date(2026, 9, 4, 9, 5);
  assert.equal(toLocalInputValue(d), '2026-10-04T09:05');
  assert.equal(localInputToIso('2026-10-04T09:05'), d.toISOString());
  assert.equal(localInputToIso(''), null);
  assert.equal(localInputToIso('nope'), null);
});
