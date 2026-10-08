import { Navigate, Route, Routes } from 'react-router-dom';
import AppLayout from './components/AppLayout';
import ProtectedRoute from './components/ProtectedRoute';
import RoleRoute from './components/RoleRoute';
import LoginPage from './pages/LoginPage';
import NotFoundPage from './pages/NotFoundPage';
import RootRedirect from './pages/RootRedirect';
import UnauthorizedPage from './pages/UnauthorizedPage';
import MyWorkOrderDetailPage from './pages/customer/MyWorkOrderDetailPage';
import MySitesPage from './pages/customer/MySitesPage';
import MyWorkOrdersPage from './pages/customer/MyWorkOrdersPage';
import RaiseRequestPage from './pages/customer/RaiseRequestPage';
import ManagerDashboardPage from './pages/manager/ManagerDashboardPage';
import CustomersPage from './pages/staff/CustomersPage';
import NewWorkOrderPage from './pages/staff/NewWorkOrderPage';
import PartsPage from './pages/staff/PartsPage';
import SitesPage from './pages/staff/SitesPage';
import WorkOrderDetailPage from './pages/staff/WorkOrderDetailPage';
import WorkOrdersPage from './pages/staff/WorkOrdersPage';
import TechnicianJobDetailPage from './pages/technician/TechnicianJobDetailPage';
import TechnicianJobsPage from './pages/technician/TechnicianJobsPage';
import { ROLE_HOME } from './auth/roles';

const M = ROLE_HOME.MANAGER;
const D = ROLE_HOME.DISPATCHER;
const T = ROLE_HOME.TECHNICIAN;
const C = ROLE_HOME.CUSTOMER;

/**
 * Route table. RoleRoute is a UX guard only; every API call is still authorised by the backend
 * (e.g. a DISPATCHER cannot delete a customer even if the button were forced into the page).
 */
export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<RootRedirect />} />

      {/* Everything below requires a signed-in user */}
      <Route element={<ProtectedRoute />}>
        <Route path="/unauthorized" element={<UnauthorizedPage />} />

        <Route element={<AppLayout />}>
          <Route element={<RoleRoute allowed={['MANAGER']} />}>
            <Route path={M} element={<ManagerDashboardPage />} />
            <Route path={`${M}/customers`} element={<CustomersPage canDelete />} />
            <Route path={`${M}/sites`} element={<SitesPage canDelete />} />
            <Route path={`${M}/work-orders`} element={<WorkOrdersPage />} />
            <Route path={`${M}/new-work-order`} element={<NewWorkOrderPage />} />
            <Route path={`${M}/work-orders/:id`} element={<WorkOrderDetailPage />} />
            <Route path={`${M}/parts`} element={<PartsPage />} />
          </Route>

          <Route element={<RoleRoute allowed={['DISPATCHER']} />}>
            <Route path={D} element={<Navigate to={`${D}/work-orders`} replace />} />
            <Route path={`${D}/customers`} element={<CustomersPage canDelete={false} />} />
            <Route path={`${D}/sites`} element={<SitesPage canDelete={false} />} />
            <Route path={`${D}/work-orders`} element={<WorkOrdersPage />} />
            <Route path={`${D}/new-work-order`} element={<NewWorkOrderPage />} />
            <Route path={`${D}/work-orders/:id`} element={<WorkOrderDetailPage />} />
          </Route>

          <Route element={<RoleRoute allowed={['TECHNICIAN']} />}>
            <Route path={T} element={<Navigate to={`${T}/jobs`} replace />} />
            <Route path={`${T}/jobs`} element={<TechnicianJobsPage />} />
            <Route path={`${T}/jobs/:id`} element={<TechnicianJobDetailPage />} />
          </Route>

          <Route element={<RoleRoute allowed={['CUSTOMER']} />}>
            <Route path={C} element={<Navigate to={`${C}/work-orders`} replace />} />
            <Route path={`${C}/work-orders`} element={<MyWorkOrdersPage />} />
            <Route path={`${C}/work-orders/:id`} element={<MyWorkOrderDetailPage />} />
            <Route path={`${C}/sites`} element={<MySitesPage />} />
            <Route path={`${C}/requests/new`} element={<RaiseRequestPage />} />
          </Route>
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
