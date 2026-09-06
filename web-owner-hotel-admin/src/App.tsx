import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from './layout/AppShell'
import { AuthProvider } from './auth/AuthProvider'
import { LoginPage } from './auth/LoginPage'
import { RequireRole } from './auth/RequireRole'
import { AccountPage } from './pages/AccountPage'
import { OverviewPage } from './pages/OverviewPage'
import { AuditLogPage } from './pages/admin/AuditLogPage'
import { CommissionPage } from './pages/admin/CommissionPage'
import { ListingReviewPage } from './pages/admin/ListingReviewPage'
import { UsersPage } from './pages/admin/UsersPage'
import { InventoryPage } from './pages/hotel/InventoryPage'
import { BookingsPage } from './pages/owner/BookingsPage'
import { CalendarPage } from './pages/owner/CalendarPage'
import { EarningsPage } from './pages/owner/EarningsPage'
import { PropertiesPage } from './pages/owner/PropertiesPage'
import { PropertyEditorPage } from './pages/owner/PropertyEditorPage'
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
            path="properties/:propertyId"
            element={<RequireRole allow={['HOUSE_OWNER']}><PropertyEditorPage /></RequireRole>}
          />
          <Route
            path="properties/:propertyId/calendar"
            element={<RequireRole allow={['HOUSE_OWNER']}><CalendarPage /></RequireRole>}
          />
          <Route
            path="bookings"
            element={<RequireRole allow={['HOUSE_OWNER']}><BookingsPage /></RequireRole>}
          />
          <Route
            path="earnings"
            element={<RequireRole allow={['HOUSE_OWNER']}><EarningsPage /></RequireRole>}
          />
          <Route
            path="hotel/inventory"
            element={<RequireRole allow={HOTEL_SIDE}><InventoryPage /></RequireRole>}
          />
          <Route
            path="admin/listings"
            element={<RequireRole allow={['SUPER_ADMIN']}><ListingReviewPage /></RequireRole>}
          />
          <Route
            path="admin/commission"
            element={<RequireRole allow={['SUPER_ADMIN']}><CommissionPage /></RequireRole>}
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
