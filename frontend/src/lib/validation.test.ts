import test from 'node:test';
import assert from 'node:assert/strict';
import {
  blankToNull,
  hasErrors,
  validateCustomer,
  validateNote,
  validatePart,
  validatePartUsage,
  validateSite,
  validateTimeLog,
  validateWorkOrder,
} from './validation';

const part = {
  sku: 'BLT-01',
  name: 'Bolt',
  description: '',
  unit: 'each',
  quantityOnHand: '10',
  reorderLevel: '2',
  unitCost: '1.50',
};

test('customer: company required, email optional but must look like an email', () => {
  assert.equal(hasErrors(validateCustomer({ companyName: 'Acme', contactEmail: '' })), false);
  assert.equal(hasErrors(validateCustomer({ companyName: 'Acme', contactEmail: 'a@b.io' })), false);
  assert.ok(validateCustomer({ companyName: '   ', contactEmail: '' }).companyName);
  assert.ok(validateCustomer({ companyName: 'x'.repeat(151), contactEmail: '' }).companyName);
  assert.ok(validateCustomer({ companyName: 'Acme', contactEmail: 'not-an-email' }).contactEmail);
});

test('site: name required; customer required only when asked; length limits', () => {
  assert.equal(hasErrors(validateSite({ name: 'HQ', addressLine: '', city: '' }, false)), false);
  assert.ok(validateSite({ name: 'HQ', addressLine: '', city: '' }, true).customerId);
  assert.equal(hasErrors(validateSite({ customerId: '3', name: 'HQ', addressLine: '', city: '' }, true)), false);
  assert.ok(validateSite({ name: '', addressLine: '', city: '' }, false).name);
  assert.ok(validateSite({ name: 'HQ', addressLine: 'x'.repeat(256), city: '' }, false).addressLine);
  assert.ok(validateSite({ name: 'HQ', addressLine: '', city: 'x'.repeat(101) }, false).city);
});

test('work order: site, title and a known priority are required', () => {
  const ok = { siteId: '1', title: 'Leak', description: '', priority: 'HIGH' };
  assert.equal(hasErrors(validateWorkOrder(ok)), false);
  assert.ok(validateWorkOrder({ ...ok, siteId: '' }).siteId);
  assert.ok(validateWorkOrder({ ...ok, title: ' ' }).title);
  assert.ok(validateWorkOrder({ ...ok, title: 'x'.repeat(201) }).title);
  assert.ok(validateWorkOrder({ ...ok, description: 'x'.repeat(2001) }).description);
  assert.ok(validateWorkOrder({ ...ok, priority: 'URGENT' }).priority);
});

test('part: required fields, whole-number stock, money with at most 2 decimals', () => {
  assert.equal(hasErrors(validatePart(part)), false);
  assert.ok(validatePart({ ...part, sku: '' }).sku);
  assert.ok(validatePart({ ...part, unit: 'x'.repeat(21) }).unit);
  assert.ok(validatePart({ ...part, quantityOnHand: '-1' }).quantityOnHand);
  assert.ok(validatePart({ ...part, quantityOnHand: '1.5' }).quantityOnHand);
  assert.ok(validatePart({ ...part, reorderLevel: '' }).reorderLevel);
  assert.ok(validatePart({ ...part, reorderLevel: '100000001' }).reorderLevel);
  assert.ok(validatePart({ ...part, unitCost: '' }).unitCost);
  assert.ok(validatePart({ ...part, unitCost: '1.999' }).unitCost);
  assert.ok(validatePart({ ...part, unitCost: 'abc' }).unitCost);
  assert.equal(validatePart({ ...part, unitCost: '0' }).unitCost, undefined);
  assert.equal(validatePart({ ...part, unitCost: '12' }).unitCost, undefined);
});

test('part usage: part and positive whole quantity; stock check when known', () => {
  assert.equal(hasErrors(validatePartUsage({ partId: '1', quantity: '2', note: '' }, 5)), false);
  assert.ok(validatePartUsage({ partId: '', quantity: '2', note: '' }).partId);
  assert.ok(validatePartUsage({ partId: '1', quantity: '0', note: '' }).quantity);
  assert.ok(validatePartUsage({ partId: '1', quantity: '1.5', note: '' }).quantity);
  assert.ok(validatePartUsage({ partId: '1', quantity: '', note: '' }).quantity);
  assert.equal(validatePartUsage({ partId: '1', quantity: '6', note: '' }, 5).quantity, 'Only 5 in stock');
  assert.equal(validatePartUsage({ partId: '1', quantity: '5', note: '' }, 5).quantity, undefined);
  assert.ok(validatePartUsage({ partId: '1', quantity: '1', note: 'x'.repeat(501) }).note);
});

test('time log: needs both times, end after start, 1 min to 24 h, not in the future', () => {
  const now = new Date('2026-10-04T12:00:00');
  const ok = { startedAt: '2026-10-04T10:00', endedAt: '2026-10-04T11:00', note: '' };
  assert.equal(hasErrors(validateTimeLog(ok, now)), false);
  assert.ok(validateTimeLog({ ...ok, startedAt: '' }, now).startedAt);
  assert.ok(validateTimeLog({ ...ok, endedAt: '' }, now).endedAt);
  assert.ok(validateTimeLog({ ...ok, endedAt: '2026-10-04T10:00' }, now).endedAt); // equal
  assert.ok(validateTimeLog({ ...ok, endedAt: '2026-10-04T09:00' }, now).endedAt); // before
  assert.ok(validateTimeLog({ startedAt: '2026-10-03T10:00', endedAt: '2026-10-04T11:00', note: '' }, now).endedAt); // 25 h
  assert.equal(validateTimeLog({ startedAt: '2026-10-03T12:00', endedAt: '2026-10-04T12:00', note: '' }, now).endedAt, undefined); // exactly 24 h
  assert.ok(validateTimeLog({ startedAt: '2026-10-04T11:00', endedAt: '2026-10-04T13:00', note: '' }, now).endedAt); // future
  assert.ok(validateTimeLog({ ...ok, note: 'x'.repeat(501) }, now).note);
});

test('note and blankToNull helpers', () => {
  assert.equal(validateNote('fine'), null);
  assert.ok(validateNote('x'.repeat(501)));
  assert.equal(blankToNull('   '), null);
  assert.equal(blankToNull('  hi '), 'hi');
});

// ---- Sign in / Sign up ----
import { buildSignupPayload, mapSignupError, validateLogin, validateSignup } from './validation';
import type { SignupForm } from './validation';

const okSignup: SignupForm = {
  fullName: 'Asha Rao',
  email: 'asha@example.com',
  password: 'secret1',
  confirmPassword: 'secret1',
  role: 'TECHNICIAN',
  companyName: '',
};

test('login requires email and password only', () => {
  assert.deepEqual(validateLogin({ email: 'a@b.co', password: 'x' }), {});
  assert.deepEqual(Object.keys(validateLogin({ email: ' ', password: '' })).sort(), ['email', 'password']);
});

test('valid signup passes for every non-customer role', () => {
  for (const role of ['MANAGER', 'DISPATCHER', 'TECHNICIAN']) {
    assert.deepEqual(validateSignup({ ...okSignup, role }), {});
  }
});

test('signup: required fields, invalid email, short password, mismatch', () => {
  const empty = validateSignup({ fullName: '', email: '', password: '', confirmPassword: '', role: '', companyName: '' });
  assert.deepEqual(Object.keys(empty).sort(), ['confirmPassword', 'email', 'fullName', 'password', 'role']);
  assert.equal(validateSignup({ ...okSignup, email: 'not-an-email' }).email, 'Enter a valid email address');
  assert.equal(validateSignup({ ...okSignup, email: 'a@b' }).email, 'Enter a valid email address');
  assert.match(validateSignup({ ...okSignup, password: '12345', confirmPassword: '12345' }).password ?? '', /at least 6/);
  assert.equal(validateSignup({ ...okSignup, confirmPassword: 'secret2' }).confirmPassword, 'Passwords do not match');
  assert.ok(validateSignup({ ...okSignup, password: 'x'.repeat(73), confirmPassword: 'x'.repeat(73) }).password);
  assert.ok(validateSignup({ ...okSignup, fullName: 'n'.repeat(101) }).fullName);
});

test('signup: company name is required only for CUSTOMER', () => {
  assert.equal(validateSignup({ ...okSignup, role: 'CUSTOMER', companyName: '  ' }).companyName, 'Company name is required');
  assert.deepEqual(validateSignup({ ...okSignup, role: 'CUSTOMER', companyName: 'Acme Ltd' }), {});
  assert.equal(validateSignup({ ...okSignup, role: 'TECHNICIAN', companyName: '' }).companyName, undefined);
  assert.ok(validateSignup({ ...okSignup, role: 'CUSTOMER', companyName: 'c'.repeat(151) }).companyName);
});

test('signup payload: trimmed, confirm password never sent, companyName only for CUSTOMER', () => {
  const p = buildSignupPayload({ ...okSignup, fullName: ' Asha ', email: ' asha@example.com ', companyName: 'ignored' });
  assert.deepEqual(p, { fullName: 'Asha', email: 'asha@example.com', password: 'secret1', role: 'TECHNICIAN' });
  const c = buildSignupPayload({ ...okSignup, role: 'CUSTOMER', companyName: ' Acme ' });
  assert.equal(c.companyName, 'Acme');
  assert.equal('confirmPassword' in c, false);
});

test('backend signup errors map to the right field', () => {
  assert.deepEqual(mapSignupError('email: Email must be valid'), { fieldErrors: { email: 'Email must be valid' }, banner: null });
  assert.deepEqual(mapSignupError('password: Password must be at least 6 characters').fieldErrors, {
    password: 'Password must be at least 6 characters',
  });
  const dup = mapSignupError('An account with this email already exists');
  assert.equal(dup.fieldErrors.email, 'An account with this email already exists');
  assert.equal(dup.banner, 'An account with this email already exists');
  assert.ok(mapSignupError('Company name is required for a customer account').fieldErrors.companyName);
  assert.deepEqual(mapSignupError('Cannot reach the server.'), { fieldErrors: {}, banner: 'Cannot reach the server.' });
});
