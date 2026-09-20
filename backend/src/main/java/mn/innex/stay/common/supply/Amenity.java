package mn.innex.stay.listing.domain;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Amenities a listing can advertise. Stored as a JSON array of these names, which
 * keeps the set extensible without a migration, while the enum stops arbitrary
 * strings reaching the database and fragmenting the search filters.
 */
public enum Amenity {

    WIFI, KITCHEN, WASHER, DRYER, TV, WORKSPACE, PARKING_FREE, PARKING_PAID,
    HEATING, AIR_CONDITIONING, HOT_WATER, SHOWER, BATHTUB,
    /** Wood or coal stove: the normal heat source in a ger. */
    STOVE_HEATING,
    ELEVATOR, PRIVATE_ENTRANCE, WHEELCHAIR_ACCESSIBLE,
    POOL, HOT_TUB, SAUNA, BBQ_GRILL, GARDEN, TERRACE, FIREPLACE,
    MOUNTAIN_VIEW, RIVER_VIEW, CITY_VIEW,
    SMOKE_ALARM, FIRE_EXTINGUISHER, FIRST_AID_KIT, SECURITY_CAMERAS_OUTSIDE,
    PETS_ALLOWED, SMOKING_ALLOWED, EVENTS_ALLOWED, LONG_TERM_STAYS,
    CRIB, HIGH_CHAIR, SELF_CHECK_IN, LUGGAGE_DROPOFF;

    /**
     * Parses amenity names, ignoring case and surrounding whitespace. Blank
     * entries are skipped; an unrecognized name is rejected.
     *
     * @throws IllegalArgumentException if any name is not an amenity
     */
    public static Set<Amenity> parseAll(Iterable<String> names) {
        Set<Amenity> parsed = new LinkedHashSet<>();
        if (names == null) {
            return parsed;
        }
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                parsed.add(parse(name));
            }
        }
        return parsed;
    }

    /** @throws IllegalArgumentException if the name is not an amenity */
    public static Amenity parse(String name) {
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(amenity -> amenity.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown amenity: " + name));
    }
}
