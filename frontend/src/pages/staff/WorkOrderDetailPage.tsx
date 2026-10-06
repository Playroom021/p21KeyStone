import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { deleteWorkOrder, getWorkOrder, setWorkOrderStatus, updateWorkOrder } from '../../api/workOrders';
import { useAuth } from '../../auth/AuthContext';
import AssignTechnicianModal from '../../components/forms/AssignTechnicianModal';
import WorkOrderForm from '../../components/forms/WorkOrderForm';
import PartUsagePanel from '../../components/workorder/PartUsagePanel';
import StatusHistory from '../../components/workorder/StatusHistory';
import TimeLogsPanel from '../../components/workorder/TimeLogsPanel';
import WorkOrderInfo from '../../components/workorder/WorkOrderInfo';
import { ConfirmDialog, ErrorState, LoadingState, Modal, PageHeader } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { useBasePath } from '../../hooks/useBasePath';
import { staffCapabilities } from '../../lib/lifecycle';

type Dialog = 'assign' | 'edit' | 'cancel' | 'close' | 'delete' | null;

/** Work-order detail for MANAGER / DISPATCHER: assign, edit, cancel, close (and delete for MANAGER). */
export default function WorkOrderDetailPage() {
  const { id } = useParams();
  const workOrderId = Number(id);
  const base = useBasePath();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [dialog, setDialog] = useState<Dialog>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const detail = useAsync(() => getWorkOrder(workOrderId), [workOrderId], 'Could not load the work order');

  if (!Number.isInteger(workOrderId) || workOrderId <= 0) {
    return <ErrorState message="That work order link is not valid." />;
  }
  if (detail.loading) return <LoadingState label="Loading work order…" />;
  if (detail.error || !detail.data || !user) {
    return (
      <>
        <ErrorState message={detail.error ?? 'Could not load the work order'} onRetry={detail.reload} />
        <p>
          <Link to={`${base}/work-orders`}>← Back to work orders</Link>
        </p>
      </>
    );
  }

  const wo = detail.data;
  const can = staffCapabilities(user.role, wo.status);

  return (
    <>
      <p className="crumb">
        <Link to={`${base}/work-orders`}>← Work orders</Link>
      </p>
      <PageHeader
        title={`${wo.code} · ${wo.title}`}
        actions={
          <>
            {can.assign && (
              <button type="button" className="btn" onClick={() => setDialog('assign')}>
                Assign technician
              </button>
            )}
            {can.edit && (
              <button type="button" className="btn btn-secondary" onClick={() => setDialog('edit')}>
                Edit
              </button>
            )}
            {can.close && (
              <button type="button" className="btn" onClick={() => setDialog('close')}>
                Close work order
              </button>
            )}
            {can.cancel && (
              <button type="button" className="btn btn-danger-outline" onClick={() => setDialog('cancel')}>
                Cancel work order
              </button>
            )}
            {can.remove && (
              <button type="button" className="btn btn-danger-outline" onClick={() => setDialog('delete')}>
                Delete
              </button>
            )}
          </>
        }
      />
      {notice && (
        <div className="alert alert-success" role="status">
          {notice}
        </div>
      )}

      <WorkOrderInfo wo={wo} />

      <section className="card">
        <h2>Status history</h2>
        <StatusHistory history={wo.history} />
      </section>
      <section className="card">
        <h2>Parts used</h2>
        <PartUsagePanel workOrderId={wo.id} canEdit={false} />
      </section>
      <section className="card">
        <h2>Time logs</h2>
        <TimeLogsPanel workOrderId={wo.id} canAdd={false} deletableTechnicianId={null} />
      </section>

      {dialog === 'assign' && (
        <AssignTechnicianModal
          workOrderId={wo.id}
          workOrderCode={wo.code}
          onClose={() => setDialog(null)}
          onAssigned={(updated) => {
            detail.setData(updated);
            setDialog(null);
            setNotice(`Assigned to ${updated.assignedTechnician ?? 'the technician'}.`);
          }}
        />
      )}
      {dialog === 'edit' && (
        <Modal title={`Edit ${wo.code}`} onClose={() => setDialog(null)}>
          <WorkOrderForm
            sites={[{ id: wo.siteId, label: `${wo.customerName} — ${wo.siteName}` }]}
            lockSite
            initial={{ siteId: wo.siteId, title: wo.title, description: wo.description, priority: wo.priority }}
            submitLabel="Save changes"
            onCancel={() => setDialog(null)}
            onSubmit={async (input) => {
              await updateWorkOrder(wo.id, input);
              setDialog(null);
              setNotice('Work order updated.');
              detail.reload();
            }}
          />
        </Modal>
      )}
      {dialog === 'cancel' && (
        <ConfirmDialog
          title={`Cancel ${wo.code}`}
          danger
          confirmLabel="Cancel work order"
          noteLabel="Reason (optional)"
          message={<p>This ends the work order. A cancelled work order cannot be reopened.</p>}
          onCancel={() => setDialog(null)}
          onConfirm={async (note) => {
            detail.setData(await setWorkOrderStatus(wo.id, 'CANCELLED', note));
            setDialog(null);
            setNotice('Work order cancelled.');
          }}
        />
      )}
      {dialog === 'close' && (
        <ConfirmDialog
          title={`Close ${wo.code}`}
          confirmLabel="Close work order"
          noteLabel="Note (optional)"
          message={<p>Close this completed work order? A closed work order can no longer be edited.</p>}
          onCancel={() => setDialog(null)}
          onConfirm={async (note) => {
            detail.setData(await setWorkOrderStatus(wo.id, 'CLOSED', note));
            setDialog(null);
            setNotice('Work order closed.');
          }}
        />
      )}
      {dialog === 'delete' && (
        <ConfirmDialog
          title={`Delete ${wo.code}`}
          danger
          confirmLabel="Delete work order"
          message={<p>Delete this work order and its history? Only work orders still in NEW can be deleted.</p>}
          onCancel={() => setDialog(null)}
          onConfirm={async () => {
            await deleteWorkOrder(wo.id);
            navigate(`${base}/work-orders`, { replace: true });
          }}
        />
      )}
    </>
  );
}
