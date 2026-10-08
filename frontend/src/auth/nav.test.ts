import test from 'node:test';
import assert from 'node:assert/strict';
import { NAV_ITEMS } from './nav';
import { canAccessPath } from './roles';
import { ROLES } from '../types/auth';

test('every nav link of a role lives inside that role\'s area (and nobody else\'s)', () => {
  for (const role of ROLES) {
    assert.ok(NAV_ITEMS[role].length > 0, `${role} has navigation`);
    for (const item of NAV_ITEMS[role]) {
      assert.ok(canAccessPath(role, item.to), `${role} may open ${item.to}`);
      for (const other of ROLES.filter((r) => r !== role)) {
        assert.equal(canAccessPath(other, item.to), false, `${other} must not open ${item.to}`);
      }
    }
  }
});

test('spec pages are present per role', () => {
  const labels = (r: (typeof ROLES)[number]) => NAV_ITEMS[r].map((i) => i.label);
  assert.deepEqual(labels('MANAGER'), ['Dashboard', 'Customers', 'Sites', 'Work orders', 'Parts']);
  assert.deepEqual(labels('DISPATCHER'), ['Work orders', 'New work order', 'Customers', 'Sites']);
  assert.deepEqual(labels('TECHNICIAN'), ['My jobs']);
  assert.deepEqual(labels('CUSTOMER'), ['My work orders', 'My sites', 'Raise request']);
});

test('no two links of one role share a path (so only one can be active)', () => {
  for (const role of ROLES) {
    const paths = NAV_ITEMS[role].map((i) => i.to);
    assert.equal(new Set(paths).size, paths.length);
  }
});
