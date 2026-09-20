import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from './layout/AppShell'
import { AuthProvider } from './auth/AuthProvider'
import { LoginPage } from './auth/LoginPage'
import { RequireRole } from './auth/RequireRole'
import { AccountPage } from './pages/AccountPage'
import { OverviewPage } from './pages/OverviewPage'
import { MessagesPage } from './pages/MessagesPage'
import { AuditLogPage } from './pages/admin/AuditLogPage'
import { CommissionPage } from './pages/admin/CommissionPage'
import { HostApplicationsPage } from './pages/admin/HostApplicationsPage'
import { AnalyticsPage } from './pages/admin/AnalyticsPage'
import { PaymentsPage } from './pages/admin/PaymentsPage'
import { ModerationPage } from './pages/admin/ModerationPage'
import { PayoutBatchesPage } from './pages/admin/PayoutBatchesPage'
import { TrustPage } from './pages/admin/TrustPage'
import { HotelReviewPage } from './pages/admin/HotelReviewPage'
import { ListingReviewPage } from './pages/admin/ListingReviewPage'
import { UsersPage } from './pages/admin/UsersPage'
import { HotelEditorPage } from './pages/hotel/HotelEditorPage'
import { HotelsPage } from './pages/hotel/HotelsPage'
import { InventoryPage } from './pages/hotel/InventoryPage'
import { ReservationsPage } from './pages/hotel/ReservationsPage'
import { BookingsPage } from './pages/owner/BookingsPage'
import { CalendarPage } from './pages/owner/CalendarPage'
import { EarningsPage } from './pages/owner/EarningsPage'
import { PayoutsPage } from './pages/owner/PayoutsPage'
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
          <Route path="messages" element={<MessagesPage />} />
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
            path="payouts"
            element={<RequireRole allow={['HOUSE_OWNER']}><PayoutsPage /></RequireRole>}
          />
          <Route
            path="earnings"
            element={<RequireRole allow={['HOUSE_OWNER']}><EarningsPage /></RequireRole>}
          />
          <Route
            path="hotel/hotels"
            element={<RequireRole allow={HOTEL_SIDE}><HotelsPage /></RequireRole>}
          />
          <Route
            path="hotel/hotels/:hotelId"
            element={<RequireRole allow={HOTEL_SIDE}><HotelEditorPage /></RequireRole>}
          />
          <Route
            path="hotel/hotels/:hotelId/inventory"
            element={<RequireRole allow={HOTEL_SIDE}><InventoryPage /></RequireRole>}
          />
          <Route
            path="hotel/hotels/:hotelId/payouts"
            element={<RequireRole allow={['HOTEL_MANAGER']}><PayoutsPage /></RequireRole>}
          />
          <Route
            path="hotel/hotels/:hotelId/reservations"
            element={<RequireRole allow={HOTEL_SIDE}><ReservationsPage /></RequireRole>}
          />
          <Route
            path="admin/analytics"
            element={<RequireRole allow={['SUPER_ADMIN']}><AnalyticsPage /></RequireRole>}
          />
          <Route
            path="admin/payments"
            element={<RequireRole allow={['SUPER_ADMIN']}><PaymentsPage /></RequireRole>}
          />
          <Route
            path="admin/payout-batches"
            element={<RequireRole allow={['SUPER_ADMIN']}><PayoutBatchesPage /></RequireRole>}
          />
          <Route
            path="admin/moderation"
            element={<RequireRole allow={['SUPER_ADMIN']}><ModerationPage /></RequireRole>}
          />
          <Route
            path="admin/trust"
            element={<RequireRole allow={['SUPER_ADMIN']}><TrustPage /></RequireRole>}
          />
          <Route
            path="admin/applications"
            element={<RequireRole allow={['SUPER_ADMIN']}><HostApplicationsPage /></RequireRole>}
          />
          <Route
            path="admin/listings"
            element={<RequireRole allow={['SUPER_ADMIN']}><ListingReviewPage /></RequireRole>}
          />
          <Route
            path="admin/hotels"
            element={<RequireRole allow={['SUPER_ADMIN']}><HotelReviewPage /></RequireRole>}
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
