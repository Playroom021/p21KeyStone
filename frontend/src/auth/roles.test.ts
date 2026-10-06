import test from 'node:test';
import assert from 'node:assert/strict';
import { ROLE_HOME, canAccessPath, isRole, postLoginPath } from './roles';
import { ROLES } from '../types/auth';

test('every role has a distinct home path', () => {
  const homes = ROLES.map((r) => ROLE_HOME[r]);
  assert.equal(new Set(homes).size, ROLES.length);
});

test('isRole accepts only the four backend roles', () => {
  for (const r of ROLES) assert.ok(isRole(r));
  for (const bad of ['ADMIN', 'manager', '', null, undefined, 3]) assert.equal(isRole(bad), false);
});

test('canAccessPath: own area yes, other roles no, prefix tricks no', () => {
  assert.ok(canAccessPath('MANAGER', '/manager'));
  assert.ok(canAccessPath('MANAGER', '/manager/reports'));
  assert.equal(canAccessPath('MANAGER', '/dispatcher'), false);
  assert.equal(canAccessPath('MANAGER', '/managerial'), false);
  assert.equal(canAccessPath('CUSTOMER', '/technician'), false);
  assert.equal(canAccessPath('TECHNICIAN', '/login'), false);
});

test('postLoginPath honors an allowed redirect, otherwise role home', () => {
  assert.equal(postLoginPath('MANAGER', '/manager/x'), '/manager/x');
  assert.equal(postLoginPath('MANAGER', '/technician'), '/manager');
  assert.equal(postLoginPath('CUSTOMER', null), '/customer');
  assert.equal(postLoginPath('DISPATCHER'), '/dispatcher');
});
