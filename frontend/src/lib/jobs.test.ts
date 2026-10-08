import test from 'node:test';
import assert from 'node:assert/strict';
import { dueLabel, mergeActiveJobs } from './jobs';
import type { WorkOrder } from '../types/domain';

function wo(id: number, slaDueAt: string | null): WorkOrder {
  return {
    id,
    code: `WO-${1000 + id}`,
    title: 't',
    customerId: 1,
    customerName: 'c',
    siteId: 1,
    siteName: 's',
    priority: 'MEDIUM',
    status: 'ASSIGNED',
    assignedTechnicianId: 5,
    assignedTechnician: 'Tess',
    slaDueAt,
    createdAt: '2026-10-01T00:00:00Z',
  };
}

test('mergeActiveJobs: soonest due first, no due last, id breaks ties', () => {
  const merged = mergeActiveJobs([
    [wo(3, '2026-10-05T10:00:00Z'), wo(1, null)],
    [wo(2, '2026-10-04T10:00:00Z'), wo(4, '2026-10-05T10:00:00Z')],
  ]);
  assert.deepEqual(merged.map((w) => w.id), [2, 3, 4, 1]);
});

test('mergeActiveJobs: empty input', () => {
  assert.deepEqual(mergeActiveJobs([[], [], []]), []);
});

test('dueLabel: future, past, none, finished work', () => {
  const now = new Date('2026-10-04T12:00:00Z');
  assert.equal(dueLabel('2026-10-04T15:00:00Z', 'ASSIGNED', now), 'Due in 3h');
  assert.equal(dueLabel('2026-10-04T11:15:00Z', 'IN_PROGRESS', now), 'Overdue by 45m');
  assert.equal(dueLabel(null, 'ASSIGNED', now), '');
  assert.equal(dueLabel('2026-10-04T15:00:00Z', 'COMPLETED', now), '');
  assert.equal(dueLabel('garbage', 'ASSIGNED', now), '');
});
