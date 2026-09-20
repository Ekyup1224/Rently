package mn.innex.stay.search.repo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import mn.innex.stay.common.supply.Amenity;
import mn.innex.stay.listing.domain.PropertyType;
import mn.innex.stay.listing.service.OccupancyPort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The unified catalogue query: houses and hotels in one ranked, paged result set.
 *
 * <p>Written as a {@code UNION ALL} of two projections rather than two queries
 * merged in Java, because sorting and paging have to happen across the whole
 * result. Merging afterwards would give wrong page counts and a sort that only
 * holds within each half — which is precisely the bug that makes a "unified"
 * search feel broken.
 *
 * <p>The two halves are unavailable for different reasons, and the SQL says so:
 * a house is taken when a booking overlaps the dates, while a hotel is taken only
 * when <em>every</em> sellable room type is closed or full. Both are decided
 * inside the query so paging stays correct.
 *
 * <p>Every value is bound as a parameter; only fixed fragments are appended and
 * the sort comes from a whitelist, so the dynamic assembly carries no injection
 * surface.
 */
@Repository
public class SupplySearchRepository {

    /** Sorts a caller may ask for, mapped to safe SQL over the unioned columns. */
    private static final Map<String, String> SORTS = Map.of(
            "price_asc", "nightly_from asc, created_at desc",
            "price_desc", "nightly_from desc, created_at desc",
            "newest", "published_at desc nulls last, created_at desc",
            "guests", "max_guests desc, nightly_from asc",
            "rating", "star_rating desc nulls last, nightly_from asc");

    private final EntityManager entityManager;
    private final OccupancyPort occupancyPort;

    public SupplySearchRepository(EntityManager entityManager, OccupancyPort occupancyPort) {
        this.entityManager = entityManager;
        this.occupancyPort = occupancyPort;
    }

    @Transactional(readOnly = true)
    public Page<SupplySearchRow> search(SupplySearchCriteria criteria) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        String union = buildUnion(criteria, parameters);
        PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size());

        if (union.isBlank()) {
            // Both halves excluded by the filters: no need to ask the database.
            return new PageImpl<>(List.of(), pageRequest, 0);
        }

        Query countQuery = entityManager.createNativeQuery(
                "select count(*) from (" + union + ") as results");
        parameters.forEach(countQuery::setParameter);
        long total = ((Number) countQuery.getSingleResult()).longValue();
        if (total == 0) {
            return new PageImpl<>(List.of(), pageRequest, 0);
        }

        String sql = "select * from (" + union + ") as results"
                + " order by " + orderBy(criteria)
                + " limit :limit offset :offset";
        Query query = entityManager.createNativeQuery(sql, Tuple.class);
        parameters.forEach(query::setParameter);
        query.setParameter("limit", criteria.size());
        query.setParameter("offset", (long) criteria.page() * criteria.size());

        @SuppressWarnings("unchecked")
        List<Tuple> rows = query.getResultList();
        return new PageImpl<>(rows.stream().map(SupplySearchRepository::toRow).toList(),
                pageRequest, total);
    }

    private String buildUnion(SupplySearchCriteria criteria, Map<String, Object> parameters) {
        List<String> branches = new ArrayList<>();
        if (criteria.includesProperties()) {
            branches.add(propertyBranch(criteria, parameters));
        }
        if (criteria.includesHotels()) {
            branches.add(hotelBranch(criteria, parameters));
        }
        return String.join(" union all ", branches);
    }

    private String propertyBranch(SupplySearchCriteria criteria, Map<String, Object> parameters) {
        List<String> clauses = new ArrayList<>();
        clauses.add("p.status = 'APPROVED'");
        // A suspended owner's inventory disappears from the catalogue at once.
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
        if (criteria.hasPoint() && criteria.radiusKm() != null) {
            clauses.add("p.latitude between :minLat and :maxLat");
            clauses.add("p.longitude between :minLng and :maxLng");
            putBoundingBox(criteria, parameters);
        }
        if (criteria.hasDates()) {
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
            putDates(criteria, parameters);
        }

        String relevance = isPresent(criteria.text())
                ? "ts_rank(p.search_vector, plainto_tsquery('simple', :text))"
                : "0::real";

        return """
                select 'PROPERTY'::text        as supply_type,
                       p.id                    as id,
                       p.title                 as title,
                       p.property_type::text   as property_type,
                       null::integer           as star_rating,
                       p.city                  as city,
                       p.district              as district,
                       p.latitude              as latitude,
                       p.longitude             as longitude,
                       p.base_price            as nightly_from,
                       p.cleaning_fee          as cleaning_fee,
                       p.currency              as currency,
                       p.max_guests            as max_guests,
                       p.bedrooms              as bedrooms,
                       p.beds                  as beds,
                       p.bathrooms             as bathrooms,
                       p.instant_book          as instant_book,
                       p.cancellation_policy   as cancellation_policy,
                       p.min_stay_nights       as min_stay_nights,
                       (select ph.storage_key from property_photos ph
                         where ph.property_id = p.id
                         order by ph.is_cover desc, ph.sort_order asc limit 1) as cover_key,
                       (select count(*) from property_photos ph2
                         where ph2.property_id = p.id)::integer as photo_count,
                       null::integer           as room_type_count,
                       p.rating_average        as rating_average,
                       p.rating_count          as rating_count,
                       p.published_at          as published_at,
                       p.created_at            as created_at,
                       %s                      as relevance
                from properties p
                join users u on u.id = p.owner_user_id
                where %s
                """.formatted(relevance, String.join(" and ", clauses));
    }

    private String hotelBranch(SupplySearchCriteria criteria, Map<String, Object> parameters) {
        List<String> clauses = new ArrayList<>();
        clauses.add("h.status = 'APPROVED'");
        clauses.add("o.status = 'ACTIVE'");
        // A hotel with nothing sellable is not a search result.
        clauses.add("""
                exists (select 1 from room_types rt0
                         where rt0.hotel_id = h.id and rt0.status = 'ACTIVE'
                           and rt0.base_price > 0 and rt0.total_rooms > 0)""");

        if (isPresent(criteria.text())) {
            clauses.add("h.search_vector @@ plainto_tsquery('simple', :text)");
            parameters.put("text", criteria.text().trim());
        }
        if (isPresent(criteria.city())) {
            clauses.add("h.city ilike :city");
            parameters.put("city", "%" + criteria.city().trim() + "%");
        }
        if (criteria.guests() != null && criteria.guests() > 0) {
            // Matched against the largest room, the same way a house is matched
            // against its capacity. Bigger parties split across rooms on the
            // hotel page itself.
            clauses.add("""
                    exists (select 1 from room_types rtg
                             where rtg.hotel_id = h.id and rtg.status = 'ACTIVE'
                               and rtg.capacity >= :guests)""");
            parameters.put("guests", criteria.guests());
        }
        if (criteria.amenities() != null && !criteria.amenities().isEmpty()) {
            clauses.add("h.amenities @> cast(:amenities as jsonb)");
            parameters.put("amenities", toJsonArray(criteria.amenities()));
        }
        if (criteria.starRating() != null) {
            clauses.add("h.star_rating >= :starRating");
            parameters.put("starRating", criteria.starRating());
        }
        if (criteria.minPrice() != null) {
            clauses.add("""
                    exists (select 1 from room_types rtmin
                             where rtmin.hotel_id = h.id and rtmin.status = 'ACTIVE'
                               and rtmin.base_price >= :minPrice)""");
            parameters.put("minPrice", criteria.minPrice());
        }
        if (criteria.maxPrice() != null) {
            clauses.add("""
                    exists (select 1 from room_types rtmax
                             where rtmax.hotel_id = h.id and rtmax.status = 'ACTIVE'
                               and rtmax.base_price > 0 and rtmax.base_price <= :maxPrice)""");
            parameters.put("maxPrice", criteria.maxPrice());
        }
        if (criteria.hasPoint() && criteria.radiusKm() != null) {
            clauses.add("h.latitude between :minLat and :maxLat");
            clauses.add("h.longitude between :minLng and :maxLng");
            putBoundingBox(criteria, parameters);
        }
        if (criteria.hasDates()) {
            // A hotel is available when at least one room type can take the whole
            // stay. A night with no inventory row is open by definition, so only
            // stored rows can rule a night out.
            clauses.add("""
                    exists (
                        select 1 from room_types rt
                        where rt.hotel_id = h.id and rt.status = 'ACTIVE'
                          and rt.base_price > 0 and rt.total_rooms > 0
                          and :nights >= rt.min_stay_nights
                          and (rt.max_stay_nights is null or :nights <= rt.max_stay_nights)
                          and not exists (
                              select 1 from room_inventory_day inv
                              where inv.room_type_id = rt.id
                                and inv.day >= :checkIn and inv.day < :checkOut
                                and (inv.stop_sell or inv.booked_count >= inv.available_count))
                          and not exists (
                              select 1 from room_inventory_day inv2
                              where inv2.room_type_id = rt.id and inv2.day = :checkIn
                                and inv2.min_stay_nights is not null
                                and :nights < inv2.min_stay_nights))""");
            putDates(criteria, parameters);
        }
        if (Boolean.TRUE.equals(criteria.instantBook())) {
            // Every hotel books instantly; the filter simply does not exclude any.
            clauses.add("true");
        }

        String relevance = isPresent(criteria.text())
                ? "ts_rank(h.search_vector, plainto_tsquery('simple', :text))"
                : "0::real";

        return """
                select 'HOTEL'::text           as supply_type,
                       h.id                    as id,
                       h.name                  as title,
                       null::text              as property_type,
                       h.star_rating           as star_rating,
                       h.city                  as city,
                       h.district              as district,
                       h.latitude              as latitude,
                       h.longitude             as longitude,
                       (select min(rtp.base_price) from room_types rtp
                         where rtp.hotel_id = h.id and rtp.status = 'ACTIVE'
                           and rtp.base_price > 0)          as nightly_from,
                       0::numeric(14,2)        as cleaning_fee,
                       h.currency              as currency,
                       (select max(rtc.capacity) from room_types rtc
                         where rtc.hotel_id = h.id and rtc.status = 'ACTIVE')::integer as max_guests,
                       null::integer           as bedrooms,
                       null::integer           as beds,
                       null::numeric(3,1)      as bathrooms,
                       true                    as instant_book,
                       h.cancellation_policy   as cancellation_policy,
                       (select min(rts.min_stay_nights) from room_types rts
                         where rts.hotel_id = h.id and rts.status = 'ACTIVE')::integer as min_stay_nights,
                       (select hp.storage_key from hotel_photos hp
                         where hp.hotel_id = h.id
                         order by hp.is_cover desc, hp.sort_order asc limit 1) as cover_key,
                       (select count(*) from hotel_photos hp2
                         where hp2.hotel_id = h.id)::integer as photo_count,
                       (select count(*) from room_types rtn
                         where rtn.hotel_id = h.id and rtn.status = 'ACTIVE')::integer as room_type_count,
                       h.rating_average        as rating_average,
                       h.rating_count          as rating_count,
                       h.published_at          as published_at,
                       h.created_at            as created_at,
                       %s                      as relevance
                from hotels h
                join organizations o on o.id = h.organization_id
                where %s
                """.formatted(relevance, String.join(" and ", clauses));
    }

    private void putDates(SupplySearchCriteria criteria, Map<String, Object> parameters) {
        parameters.put("nights", criteria.nights());
        parameters.put("checkIn", criteria.checkIn());
        parameters.put("checkOut", criteria.checkOut());
        parameters.put("occupying", occupancyPort.occupyingStatusNames());
    }

    private void putBoundingBox(SupplySearchCriteria criteria, Map<String, Object> parameters) {
        // Cheap and index-usable, slightly generous at the corners. Distance
        // ordering refines it when asked for.
        double latDelta = criteria.radiusKm() / 111.0;
        double lngDelta = criteria.radiusKm()
                / (111.0 * Math.max(0.01, Math.cos(Math.toRadians(criteria.latitude()))));
        parameters.put("minLat", criteria.latitude() - latDelta);
        parameters.put("maxLat", criteria.latitude() + latDelta);
        parameters.put("minLng", criteria.longitude() - lngDelta);
        parameters.put("maxLng", criteria.longitude() + lngDelta);
    }

    private String orderBy(SupplySearchCriteria criteria) {
        String requested = criteria.sort() == null ? "" : criteria.sort().trim().toLowerCase();

        if ("relevance".equals(requested) && isPresent(criteria.text())) {
            return "relevance desc, created_at desc";
        }
        if ("distance".equals(requested) && criteria.hasPoint()) {
            // Squared planar distance with a longitude correction: monotonic in
            // true distance at city scale and far cheaper than haversine.
            return """
                    (power(latitude - %s, 2)
                      + power((longitude - %s) * %s, 2)) asc nulls last"""
                    .formatted(criteria.latitude(), criteria.longitude(),
                            Math.cos(Math.toRadians(criteria.latitude())));
        }
        return SORTS.getOrDefault(requested, "published_at desc nulls last, created_at desc");
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    /** Builds a JSON array from enum names; no user text reaches this. */
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

    private static SupplySearchRow toRow(Tuple tuple) {
        return new SupplySearchRow(
                (String) tuple.get("supply_type"),
                (UUID) tuple.get("id"),
                (String) tuple.get("title"),
                (String) tuple.get("property_type"),
                asInteger(tuple.get("star_rating")),
                (String) tuple.get("city"),
                (String) tuple.get("district"),
                (Double) tuple.get("latitude"),
                (Double) tuple.get("longitude"),
                (BigDecimal) tuple.get("nightly_from"),
                (BigDecimal) tuple.get("cleaning_fee"),
                (String) tuple.get("currency"),
                asInteger(tuple.get("max_guests")) == null ? 0 : asInteger(tuple.get("max_guests")),
                asInteger(tuple.get("bedrooms")),
                asInteger(tuple.get("beds")),
                (BigDecimal) tuple.get("bathrooms"),
                Boolean.TRUE.equals(tuple.get("instant_book")),
                (String) tuple.get("cancellation_policy"),
                asInteger(tuple.get("min_stay_nights")) == null
                        ? 1 : asInteger(tuple.get("min_stay_nights")),
                (String) tuple.get("cover_key"),
                asInteger(tuple.get("photo_count")) == null
                        ? 0 : asInteger(tuple.get("photo_count")),
                asInteger(tuple.get("room_type_count")),
                (BigDecimal) tuple.get("rating_average"),
                asInteger(tuple.get("rating_count")) == null
                        ? 0 : asInteger(tuple.get("rating_count")));
    }

    private static Integer asInteger(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }
}
