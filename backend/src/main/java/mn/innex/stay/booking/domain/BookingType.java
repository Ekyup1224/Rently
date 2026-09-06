package mn.innex.stay.booking.domain;

/**
 * Which supply type a booking points at. Step 2 carries {@link #PROPERTY} only;
 * {@code HOTEL} joins it in Step 3, when {@code room_type_id} is added.
 */
public enum BookingType {
    PROPERTY
}
