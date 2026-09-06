package mn.innex.stay.listing.repo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import mn.innex.stay.listing.domain.Amenity;
import mn.innex.stay.listing.domain.PropertyType;
import mn.innex.stay.listing.service.OccupancyPort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guest-facing listing search.
 *
 * <p>Written as native SQL rather than Criteria for three reasons: date
 * availability is a half-open range overlap, full-text matching needs Postgres's
 * {@code @@} operator against the generated {@code tsvector}, and amenity
 * filtering needs {@code jsonb @>}. Expressing those through JPA would mean
 * custom function registration and would still read worse than the SQL.
 *
 * <p>The query reads the bookings table because booked dates are derived rather
 * than duplicated into the availability table. The booking statuses that count as
 * occupying come through {@link OccupancyPort} instead of a direct import, so this
 * module still has no compile-time dependency on the booking module.
 *
 * <p>Every value is bound as a parameter. Only fixed SQL fragments are ever
 * appended, and the sort clause comes from a whitelist, so the dynamic assembly
 * carries no injection surface.
 */
@Repository
public class PropertySearchRepository {

    /** Columns a caller may sort by, mapped to safe SQL. Anything else is rejected. */
    private static final Map<String, String> SORTS = Map.of(
            "price_asc", "p.base_price asc, p.created_at desc",
            "price_desc", "p.base_price desc, p.created_at desc",
            "newest", "p.published_at desc nulls last, p.created_at desc",
            "guests", "p.max_guests desc, p.base_price asc");

    private static final String SELECT_COLUMNS = """
            select p.id                as id,
                   p.title             as title,
                   p.property_type     as property_type,
                   p.city              as city,
                   p.district          as district,
                   p.latitude          as latitude,
                   p.longitude         as longitude,
                   p.base_price        as base_price,
                   p.cleaning_fee      as cleaning_fee,
                   p.currency          as currency,
                   p.max_guests        as max_guests,
                   p.bedrooms          as bedrooms,
                   p.beds              as beds,
                   p.bathrooms         as bathrooms,
                   p.instant_book      as instant_book,
                   p.cancellation_policy as cancellation_policy,
                   p.min_stay_nights   as min_stay_nights,
                   (select ph.storage_key from property_photos ph
                     where ph.property_id = p.id
                     order by ph.is_cover desc, ph.sort_order asc limit 1) as cover_key,
                   (select count(*) from property_photos ph2 where ph2.property_id = p.id) as photo_count
            """;

    private final EntityManager entityManager;
    private final OccupancyPort occupancyPort;

    public PropertySearchRepository(EntityManager entityManager, OccupancyPort occupancyPort) {
        this.entityManager = entityManager;
        this.occupancyPort = occupancyPort;
    }

    @Transactional(readOnly = true)
    public Page<PropertySearchRow> search(PropertySearchCriteria criteria) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        String where = buildWhere(criteria, parameters);

        Query countQuery = entityManager.createNativeQuery(
                "select count(*) from properties p join users u on u.id = p.owner_user_id " + where);
        bind(countQuery, parameters);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size());
        if (total == 0) {
            return new PageImpl<>(List.of(), pageRequest, 0);
        }

        String sql = SELECT_COLUMNS
                + " from properties p join users u on u.id = p.owner_user_id "
                + where
                + " order by " + orderBy(criteria, parameters)
                + " limit :limit offset :offset";

        Query query = entityManager.createNativeQuery(sql, Tuple.class);
        bind(query, parameters);
        query.setParameter("limit", criteria.size());
        query.setParameter("offset", (long) criteria.page() * criteria.size());

        @SuppressWarnings("unchecked")
        List<Tuple> rows = query.getResultList();
        return new PageImpl<>(rows.stream().map(PropertySearchRepository::toRow).toList(),
                pageRequest, total);
    }

    private String buildWhere(PropertySearchCriteria criteria, Map<String, Object> parameters) {
        List<String> clauses = new ArrayList<>();
        // Only approved listings from active owners are ever visible, so suspending
        // a host removes their inventory from search immediately.
        clauses.add("p.status = 'APPROVED'");
        clauses.add("u.status = 'ACTIVE'");

        if (isPresent(criteria.text())) {
            clauses.add("p.search_vector @@ plainto_tsquery('simple', :text)");
            parameters.put("text", criteria.text().trim());
        }
        if (isPresent(criteria.city())) {
            clauses.add("p.city ilike :city");
            parameters.put("city", "%" + criteria.city().trim() + "%");
        }
        if (criteria.guests() != null && criteria.guests() > 0) {
            clauses.add("p.max_guests >= :guests");
            parameters.put("guests", criteria.guests());
        }
        if (criteria.types() != null && !criteria.types().isEmpty()) {
            clauses.add("p.property_type in (:types)");
            parameters.put("types", criteria.types().stream().map(PropertyType::name).toList());
        }
        if (criteria.amenities() != null && !criteria.amenities().isEmpty()) {
            // jsonb containment: the listing must advertise every requested amenity.
            clauses.add("p.amenities @> cast(:amenities as jsonb)");
            parameters.put("amenities", toJsonArray(criteria.amenities()));
        }
        if (Boolean.TRUE.equals(criteria.instantBook())) {
            clauses.add("p.instant_book = true");
        }
        if (criteria.minPrice() != null) {
            clauses.add("p.base_price >= :minPrice");
            parameters.put("minPrice", criteria.minPrice());
        }
        if (criteria.maxPrice() != null) {
            clauses.add("p.base_price <= :maxPrice");
            parameters.put("maxPrice", criteria.maxPrice());
        }
        if (criteria.hasPoint() && criteria.radiusKm() != null && criteria.radiusKm() > 0) {
            // Bounding box only: cheap, index-usable, and slightly generous at the
            // corners. Distance ordering below refines it.
            double latDelta = criteria.radiusKm() / 111.0;
            double lngDelta = criteria.radiusKm()
                    / (111.0 * Math.max(0.01, Math.cos(Math.toRadians(criteria.latitude()))));
            clauses.add("p.latitude between :minLat and :maxLat");
            clauses.add("p.longitude between :minLng and :maxLng");
            parameters.put("minLat", criteria.latitude() - latDelta);
            parameters.put("maxLat", criteria.latitude() + latDelta);
            parameters.put("minLng", criteria.longitude() - lngDelta);
            parameters.put("maxLng", criteria.longitude() + lngDelta);
        }

        if (criteria.hasDates()) {
            long nights = criteria.nights();
            // The listing's own stay limits, and any per-day minimum on the arrival night.
            clauses.add("""
                    (:nights >= p.min_stay_nights
                      and (p.max_stay_nights is null or :nights <= p.max_stay_nights))""");
            clauses.add("""
                    not exists (select 1 from property_availability a
                                 where a.property_id = p.id and a.is_blocked
                                   and a.day >= :checkIn and a.day < :checkOut)""");
            clauses.add("""
                    not exists (select 1 from property_availability a2
                                 where a2.property_id = p.id and a2.day = :checkIn
                                   and a2.min_stay_nights is not null
                                   and :nights < a2.min_stay_nights)""");
            // Half-open overlap: a stay ending the day another begins is fine.
            clauses.add("""
                    not exists (select 1 from bookings b
                                 where b.property_id = p.id
                                   and b.status in (:occupying)
                                   and b.check_in < :checkOut and b.check_out > :checkIn)""");
            parameters.put("nights", nights);
            parameters.put("checkIn", criteria.checkIn());
            parameters.put("checkOut", criteria.checkOut());
            parameters.put("occupying", occupancyPort.occupyingStatusNames());
        }

        return "where " + String.join(" and ", clauses);
    }

    private String orderBy(PropertySearchCriteria criteria, Map<String, Object> parameters) {
        String requested = criteria.sort() == null ? "" : criteria.sort().trim().toLowerCase();

        if ("relevance".equals(requested) && parameters.containsKey("text")) {
            return "ts_rank(p.search_vector, plainto_tsquery('simple', :text)) desc, p.created_at desc";
        }
        if ("distance".equals(requested) && criteria.hasPoint()) {
            // Squared planar distance with a longitude correction: monotonic in true
            // distance at city scale, and far cheaper than haversine for ordering.
            parameters.put("sortLat", criteria.latitude());
            parameters.put("sortLng", criteria.longitude());
            parameters.put("lngScale", Math.cos(Math.toRadians(criteria.latitude())));
            return """
                    (power(p.latitude - :sortLat, 2)
                      + power((p.longitude - :sortLng) * :lngScale, 2)) asc nulls last""";
        }
        return SORTS.getOrDefault(requested, "p.published_at desc nulls last, p.created_at desc");
    }

    private void bind(Query query, Map<String, Object> parameters) {
        parameters.forEach(query::setParameter);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    /** Builds a JSON array literal from enum names; no user text reaches this. */
    private static String toJsonArray(List<Amenity> amenities) {
        StringBuilder json = new StringBuilder("[");
        for (int index = 0; index < amenities.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append('"').append(amenities.get(index).name()).append('"');
        }
        return json.append(']').toString();
    }

    private static PropertySearchRow toRow(Tuple tuple) {
        return new PropertySearchRow(
                (UUID) tuple.get("id"),
                (String) tuple.get("title"),
                PropertyType.valueOf((String) tuple.get("property_type")),
                (String) tuple.get("city"),
                (String) tuple.get("district"),
                (Double) tuple.get("latitude"),
                (Double) tuple.get("longitude"),
                (BigDecimal) tuple.get("base_price"),
                (BigDecimal) tuple.get("cleaning_fee"),
                (String) tuple.get("currency"),
                ((Number) tuple.get("max_guests")).intValue(),
                ((Number) tuple.get("bedrooms")).intValue(),
                ((Number) tuple.get("beds")).intValue(),
                (BigDecimal) tuple.get("bathrooms"),
                (Boolean) tuple.get("instant_book"),
                (String) tuple.get("cancellation_policy"),
                ((Number) tuple.get("min_stay_nights")).intValue(),
                (String) tuple.get("cover_key"),
                ((Number) tuple.get("photo_count")).intValue());
    }

    /** Flat projection of one search result; assembled into a response DTO by the service. */
    public record PropertySearchRow(
            UUID id,
            String title,
            PropertyType propertyType,
            String city,
            String district,
            Double latitude,
            Double longitude,
            BigDecimal basePrice,
            BigDecimal cleaningFee,
            String currency,
            int maxGuests,
            int bedrooms,
            int beds,
            BigDecimal bathrooms,
            boolean instantBook,
            String cancellationPolicy,
            int minStayNights,
            String coverStorageKey,
            int photoCount) {
    }
}
