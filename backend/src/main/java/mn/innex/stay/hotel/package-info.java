/**
 * Hotel supply: hotels owned by an organization, their room types, counted
 * per-day inventory, and staff assignments.
 *
 * <p>This module deliberately has <strong>no dependency on {@link mn.innex.stay.booking}</strong>.
 * It never needs one: the rooms sold on a given night live on
 * {@link mn.innex.stay.hotel.domain.RoomInventoryDay#getBookedCount()}, maintained
 * by a database trigger, so availability and occupancy are answerable from hotel
 * data alone. Anything that genuinely needs reservations -- the front desk, the
 * revenue report -- lives in the booking module instead.
 */
package mn.innex.stay.hotel;
