package mn.innex.stay.search.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.PlatformTime;
import mn.innex.stay.common.supply.Amenity;
import mn.innex.stay.listing.domain.PropertyType;
import mn.innex.stay.listing.storage.ObjectStorage;
import mn.innex.stay.search.repo.SupplySearchCriteria;
import mn.innex.stay.search.repo.SupplySearchRepository;
import mn.innex.stay.search.web.SupplySummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Validates a guest search and runs it across both supply types. */
@Service
public class SupplySearchService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final double MAX_RADIUS_KM = 200;
    private static final List<String> SUPPLY_TYPES = List.of("PROPERTY", "HOTEL");

    private final SupplySearchRepository searchRepository;
    private final ObjectStorage storage;

    public SupplySearchService(SupplySearchRepository searchRepository, ObjectStorage storage) {
        this.searchRepository = searchRepository;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public Page<SupplySummaryResponse> search(String text, String city, LocalDate checkIn,
                                              LocalDate checkOut, Integer guests,
                                              List<String> supplyTypes, List<String> types,
                                              List<String> amenities, Boolean instantBook,
                                              Integer starRating, BigDecimal minPrice,
                                              BigDecimal maxPrice, Double latitude,
                                              Double longitude, Double radiusKm, String sort,
                                              Integer page, Integer size) {
        SupplySearchCriteria criteria = new SupplySearchCriteria(
                text, city, checkIn, checkOut, guests,
                parseSupplyTypes(supplyTypes),
                parseTypes(types),
                parseAmenities(amenities),
                instantBook, starRating, minPrice, maxPrice,
                latitude, longitude, clampRadius(radiusKm), sort,
                page == null || page < 0 ? 0 : page,
                normalizeSize(size));

        validate(criteria);
        return searchRepository.search(criteria)
                .map(row -> SupplySummaryResponse.from(row, storage));
    }

    private void validate(SupplySearchCriteria criteria) {
        if (criteria.checkIn() != null ^ criteria.checkOut() != null) {
            throw ApiException.badRequest("incomplete_date_range",
                    "Provide both checkIn and checkOut, or neither");
        }
        if (criteria.hasDates()) {
            if (!criteria.checkOut().isAfter(criteria.checkIn())) {
                throw ApiException.badRequest("invalid_date_range",
                        "checkOut must be after checkIn");
            }
            if (criteria.checkIn().isBefore(PlatformTime.today())) {
                throw ApiException.badRequest("check_in_in_past", "checkIn cannot be in the past");
            }
        }
        if (criteria.minPrice() != null && criteria.maxPrice() != null
                && criteria.minPrice().compareTo(criteria.maxPrice()) > 0) {
            throw ApiException.badRequest("invalid_price_range", "minPrice cannot exceed maxPrice");
        }
        if (criteria.starRating() != null
                && (criteria.starRating() < 1 || criteria.starRating() > 5)) {
            throw ApiException.badRequest("invalid_star_rating", "starRating must be 1 to 5");
        }
        boolean hasRadius = criteria.radiusKm() != null;
        if (criteria.hasPoint() != hasRadius) {
            throw ApiException.badRequest("incomplete_location_filter",
                    "Provide latitude, longitude and radiusKm together");
        }
    }

    private List<String> parseSupplyTypes(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        List<String> parsed = requested.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .toList();
        if (!SUPPLY_TYPES.containsAll(parsed)) {
            throw ApiException.badRequest("unknown_supply_type",
                    "supplyTypes must be any of: " + SUPPLY_TYPES);
        }
        return parsed;
    }

    private List<PropertyType> parseTypes(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        try {
            return names.stream()
                    .filter(name -> name != null && !name.isBlank())
                    .map(name -> PropertyType.valueOf(name.trim().toUpperCase(Locale.ROOT)))
                    .toList();
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("unknown_property_type",
                    "Unknown property type. Supported: " + List.of(PropertyType.values()));
        }
    }

    private List<Amenity> parseAmenities(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        try {
            return List.copyOf(Amenity.parseAll(names));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("unknown_amenity", ex.getMessage());
        }
    }

    private Double clampRadius(Double radiusKm) {
        if (radiusKm == null) {
            return null;
        }
        if (radiusKm <= 0) {
            throw ApiException.badRequest("invalid_radius", "radiusKm must be positive");
        }
        return Math.min(radiusKm, MAX_RADIUS_KM);
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
