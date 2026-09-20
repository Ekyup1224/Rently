package mn.innex.stay.booking.domain;

/**
 * Which supply type a booking points at.
 *
 * <p>A {@link #PROPERTY} booking takes a whole place for its dates and is kept
 * exclusive by a database exclusion constraint. A {@link #HOTEL} booking takes a
 * number of rooms of one room type, and is kept within capacity by a counted
 * inventory row and its CHECK constraint. The two guarantees are different
 * because the two kinds of supply are.
 */
public enum BookingType {

    PROPERTY,

    HOTEL;

    public boolean isHotel() {
        return this == HOTEL;
    }
}
