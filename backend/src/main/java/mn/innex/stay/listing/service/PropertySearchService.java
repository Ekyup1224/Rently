package mn.innex.stay.listing.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.PlatformTime;
import mn.innex.stay.listing.domain.Amenity;
import mn.innex.stay.listing.domain.PropertyType;
import mn.innex.stay.listing.repo.PropertySearchCriteria;
import mn.innex.stay.listing.repo.PropertySearchRepository;
import mn.innex.stay.listing.storage.ObjectStorage;
import mn.innex.stay.listing.web.dto.ListingSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Validates and runs guest searches.
 *
 * <p>Step 2 searches houses only. Step 3 adds hotel rooms and blends both into one
 * result set — the reason this sits behind a service rather than in the controller
 * is so that unification happens in one place.
 */
@Service
public class PropertySearchService {

    /** Caps the page size: a search result page is for humans, not bulk export. */
    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final double MAX_RADIUS_KM = 200;

    private final PropertySearchRepository searchRepository;
    private final ObjectStorage storage;

    public PropertySearchService(PropertySearchRepository searchRepository, ObjectStorage storage) {
        this.searchRepository = searchRepository;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public Page<ListingSummaryResponse> search(String text, String city, LocalDate checkIn,
                                               LocalDate checkOut, Integer guests,
                                               List<String> types, List<String> amenities,
                                               Boolean instantBook, BigDecimal minPrice,
                                               BigDecimal maxPrice, Double latitude,
                                               Double longitude, Double radiusKm, String sort,
                                               Integer page, Integer size) {
        PropertySearchCriteria criteria = new PropertySearchCriteria(
                text, city,
                checkIn, checkOut,
                guests,
                parseTypes(types),
                parseAmenities(amenities),
                instantBook,
                minPrice, maxPrice,
                latitude, longitude, clampRadius(radiusKm),
                sort,
                page == null || page < 0 ? 0 : page,
                normalizeSize(size));

        validate(criteria);
        return searchRepository.search(criteria)
                .map(row -> ListingSummaryResponse.from(row, storage));
    }

    private void validate(PropertySearchCriteria criteria) {
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
                throw ApiException.badRequest("check_in_in_past",
                        "checkIn cannot be in the past");
            }
        }
        if (criteria.minPrice() != null && criteria.maxPrice() != null
                && criteria.minPrice().compareTo(criteria.maxPrice()) > 0) {
            throw ApiException.badRequest("invalid_price_range",
                    "minPrice cannot exceed maxPrice");
        }
        // A point without a radius, or a radius without a point, is a client bug
        // that would otherwise silently return unfiltered results.
        boolean hasRadius = criteria.radiusKm() != null;
        if (criteria.hasPoint() != hasRadius) {
            throw ApiException.badRequest("incomplete_location_filter",
                    "Provide latitude, longitude and radiusKm together");
        }
    }

    private List<PropertyType> parseTypes(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        try {
            return names.stream()
                    .filter(name -> name != null && !name.isBlank())
                    .map(name -> PropertyType.valueOf(name.trim().toUpperCase()))
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
