package mn.innex.stay.hotel.domain;

/**
 * Whether a room type is currently sold.
 *
 * <p>Deactivating removes it from search and blocks new bookings, but leaves
 * existing reservations and their inventory intact — a room type taken out of
 * sale mid-season must not cancel the stays already booked into it.
 */
public enum RoomTypeStatus {
    ACTIVE, INACTIVE
}
