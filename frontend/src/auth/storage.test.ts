import test, { beforeEach } from 'node:test';
import assert from 'node:assert/strict';

// Minimal in-memory localStorage for Node.
const store = new Map<string, string>();
(globalThis as unknown as { localStorage: Storage }).localStorage = {
  getItem: (k: string) => (store.has(k) ? store.get(k)! : null),
  setItem: (k: string, v: string) => void store.set(k, String(v)),
  removeItem: (k: string) => void store.delete(k),
  clear: () => store.clear(),
  key: () => null,
  length: 0,
} as Storage;

import { TOKEN_KEY, USER_KEY, clearSession, getStoredUser, getToken, saveSession } from './storage';
import type { AuthUser } from '../types/auth';

const user: AuthUser = { id: 7, fullName: 'Ada', email: 'a@b.co', role: 'MANAGER', companyName: null };

beforeEach(() => store.clear());

test('saveSession then read back', () => {
  saveSession('tok', user);
  assert.equal(getToken(), 'tok');
  assert.deepEqual(getStoredUser(), user);
});

test('clearSession removes token and user', () => {
  saveSession('tok', user);
  clearSession();
  assert.equal(getToken(), null);
  assert.equal(getStoredUser(), null);
});

test('corrupt or tampered stored user is ignored', () => {
  store.set(USER_KEY, '{not json');
  assert.equal(getStoredUser(), null);
  store.set(USER_KEY, JSON.stringify({ ...user, role: 'ADMIN' }));
  assert.equal(getStoredUser(), null);
  store.set(USER_KEY, JSON.stringify({ fullName: 'x' }));
  assert.equal(getStoredUser(), null);
  assert.equal(store.has(TOKEN_KEY), false);
});
