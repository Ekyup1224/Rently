import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from './layout/AppShell'
import { AuthProvider } from './auth/AuthProvider'
import { LoginPage } from './auth/LoginPage'
import { RequireRole } from './auth/RequireRole'
import { AccountPage } from './pages/AccountPage'
import { OverviewPage } from './pages/OverviewPage'
import { AuditLogPage } from './pages/admin/AuditLogPage'
import { UsersPage } from './pages/admin/UsersPage'
import { InventoryPage } from './pages/hotel/InventoryPage'
import { PropertiesPage } from './pages/owner/PropertiesPage'
import type { Role } from './types'

const ALL_SIGNED_IN: Role[] = ['CLIENT', 'HOUSE_OWNER', 'HOTEL_MANAGER', 'HOTEL_STAFF', 'SUPER_ADMIN']
const HOTEL_SIDE: Role[] = ['HOTEL_MANAGER', 'HOTEL_STAFF']

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />

        <Route
          element={
            <RequireRole allow={ALL_SIGNED_IN}>
              <AppShell />
            </RequireRole>
          }
        >
          <Route index element={<OverviewPage />} />
          <Route path="account" element={<AccountPage />} />
          <Route
            path="properties"
            element={<RequireRole allow={['HOUSE_OWNER']}><PropertiesPage /></RequireRole>}
          />
          <Route
            path="hotel/inventory"
            element={<RequireRole allow={HOTEL_SIDE}><InventoryPage /></RequireRole>}
          />
          <Route
            path="admin/users"
            element={<RequireRole allow={['SUPER_ADMIN']}><UsersPage /></RequireRole>}
          />
          <Route
            path="admin/audit-logs"
            element={<RequireRole allow={['SUPER_ADMIN']}><AuditLogPage /></RequireRole>}
          />
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  )
}
