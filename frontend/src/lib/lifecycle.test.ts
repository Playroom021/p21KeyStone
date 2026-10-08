import test from 'node:test';
import assert from 'node:assert/strict';
import { canChangeParts, canLogTime, isOpenStatus, staffCapabilities, technicianActions } from './lifecycle';
import { WORK_ORDER_STATUSES } from '../types/domain';

test('technician actions follow the job lifecycle exactly', () => {
  assert.deepEqual(technicianActions('NEW'), []);
  assert.deepEqual(technicianActions('ASSIGNED'), ['start']);
  assert.deepEqual(technicianActions('IN_PROGRESS'), ['hold', 'complete']);
  assert.deepEqual(technicianActions('ON_HOLD'), ['resume']);
  for (const s of ['COMPLETED', 'CLOSED', 'CANCELLED'] as const) assert.deepEqual(technicianActions(s), []);
});

test('parts: only while IN_PROGRESS or ON_HOLD; time: also late entries on COMPLETED', () => {
  const parts = WORK_ORDER_STATUSES.filter(canChangeParts);
  assert.deepEqual(parts, ['IN_PROGRESS', 'ON_HOLD']);
  const time = WORK_ORDER_STATUSES.filter(canLogTime);
  assert.deepEqual(time, ['IN_PROGRESS', 'ON_HOLD', 'COMPLETED']);
});

test('open statuses', () => {
  assert.deepEqual(WORK_ORDER_STATUSES.filter(isOpenStatus), ['NEW', 'ASSIGNED', 'IN_PROGRESS', 'ON_HOLD']);
});

test('staff capabilities by status', () => {
  const m = (s: Parameters<typeof staffCapabilities>[1]) => staffCapabilities('MANAGER', s);
  const d = (s: Parameters<typeof staffCapabilities>[1]) => staffCapabilities('DISPATCHER', s);
  assert.deepEqual(m('NEW'), { assign: true, edit: true, cancel: true, close: false, remove: true });
  assert.deepEqual(d('NEW'), { assign: true, edit: true, cancel: true, close: false, remove: false });
  assert.deepEqual(m('ASSIGNED'), { assign: false, edit: true, cancel: true, close: false, remove: false });
  assert.deepEqual(m('IN_PROGRESS'), { assign: false, edit: true, cancel: true, close: false, remove: false });
  assert.deepEqual(m('COMPLETED'), { assign: false, edit: true, cancel: false, close: true, remove: false });
  assert.deepEqual(m('CLOSED'), { assign: false, edit: false, cancel: false, close: false, remove: false });
  assert.deepEqual(m('CANCELLED'), { assign: false, edit: false, cancel: false, close: false, remove: false });
});

test('technicians and customers get no staff capabilities', () => {
  for (const role of ['TECHNICIAN', 'CUSTOMER'] as const) {
    for (const s of WORK_ORDER_STATUSES) {
      assert.deepEqual(staffCapabilities(role, s), { assign: false, edit: false, cancel: false, close: false, remove: false });
    }
  }
});
