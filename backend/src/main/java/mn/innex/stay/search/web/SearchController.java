package mn.innex.stay.search.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import mn.innex.stay.common.supply.Amenity;
import mn.innex.stay.common.web.PageResponse;
import mn.innex.stay.listing.domain.PropertyType;
import mn.innex.stay.search.service.SupplySearchService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The unified catalogue: houses and hotels in one search.
 *
 * <p>This is the product's whole premise, which is why it is one endpoint over one
 * query rather than two searches stitched together — sorting and paging have to
 * span both, and a guest comparing a ger against a hotel room should not have to
 * know they are different tables.
 *
 * <p>Kept at {@code /api/v1/listings/search} so existing clients and bookmarks
 * keep working, even though it now returns both supply types.
 */
@RestController
@RequestMapping("/api/v1/listings")
public class SearchController {

    private final SupplySearchService searchService;

    public SearchController(SupplySearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * Searches the catalogue.
     *
     * <p>With {@code checkIn} and {@code checkOut}, only stays that are actually
     * free are returned. The two supply types are unavailable for different
     * reasons — a house is taken when a booking overlaps, a hotel only when every
     * room type is full or closed — and both are decided inside the query so
     * paging stays correct.
     *
     * @param supplyTypes PROPERTY, HOTEL, or omit for both
     * @param amenities   results must advertise all of these
     * @param starRating  minimum hotel rating; setting it narrows to hotels
     * @param sort        relevance, price_asc, price_desc, newest, guests, rating, distance
     */
    @GetMapping("/search")
    public PageResponse<SupplySummaryResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(required = false) Integer guests,
            @RequestParam(required = false) List<String> supplyTypes,
            @RequestParam(required = false) List<String> types,
            @RequestParam(required = false) List<String> amenities,
            @RequestParam(required = false) Boolean instantBook,
            @RequestParam(required = false) Integer starRating,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return PageResponse.of(searchService.search(q, city, checkIn, checkOut, guests,
                supplyTypes, types, amenities, instantBook, starRating, minPrice, maxPrice,
                latitude, longitude, radiusKm, sort, page, size));
    }

    /** The values a search UI needs to build its controls. */
    @GetMapping("/filters")
    public Map<String, Object> filters() {
        return Map.of(
                "supplyTypes", List.of("PROPERTY", "HOTEL"),
                "propertyTypes", List.of(PropertyType.values()),
                "amenities", List.of(Amenity.values()),
                "sorts", List.of("relevance", "price_asc", "price_desc", "newest", "guests",
                        "rating", "distance"));
    }
}
