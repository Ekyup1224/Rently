package mn.innex.stay.listing.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.audit.AuditAction;
import mn.innex.stay.common.audit.AuditService;
import mn.innex.stay.common.supply.ListingReviewGate;
import mn.innex.stay.common.supply.SupplyKind;
import mn.innex.stay.common.supply.Amenity;
import mn.innex.stay.listing.domain.Property;
import mn.innex.stay.common.supply.SupplyStatus;
import mn.innex.stay.listing.domain.PropertyType;
import mn.innex.stay.listing.repo.PropertyRepository;
import mn.innex.stay.listing.web.dto.PropertyCreateRequest;
import mn.innex.stay.listing.web.dto.PropertyUpdateRequest;
import mn.innex.stay.user.domain.User;
import mn.innex.stay.user.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owner-side listing management, plus the admin review decision.
 *
 * <p>Every method that touches an existing listing goes through
 * {@link #requireOwned}, which takes the owner id from the security context. A
 * listing id in a URL therefore grants nothing on its own.
 */
@Service
public class PropertyService {

    private static final Logger log = LoggerFactory.getLogger(PropertyService.class);

    /**
     * Fields the approval was based on. Changing one of these on a live listing
     * sends it back for review; changing price, text or photos does not, because
     * owners need to adjust those without losing visibility for a day.
     */
    private static final String REVIEW_TRIGGERING_FIELDS = "location, address or property type";

    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ObjectProvider<ListingReviewGate> reviewGate;

    public PropertyService(PropertyRepository propertyRepository, UserRepository userRepository,
                           AuditService auditService,
                           ObjectProvider<ListingReviewGate> reviewGate) {
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.reviewGate = reviewGate;
    }

    @Transactional
    public Property create(UUID ownerId, PropertyCreateRequest request, String ip) {
        User owner = userRepository.findByIdWithRoles(ownerId)
                .orElseThrow(() -> ApiException.unauthorized("unauthenticated",
                        "Authentication is required"));

        Property property = new Property(owner, request.title().trim(), request.propertyType(),
                request.city().trim());
        property.setMaxGuests(request.maxGuests());
        propertyRepository.save(property);

        auditService.record(ownerId, AuditAction.PROPERTY_CREATED, "Property", property.getId(),
                Map.of("title", property.getTitle(), "type", property.getPropertyType().name()), ip);
        return property;
    }

    /**
     * Applies the non-null fields of a partial update.
     *
     * @throws ApiException 409 when the listing is suspended, which the owner
     *                      cannot edit their way out of
     */
    @Transactional
    public Property update(UUID ownerId, UUID propertyId, PropertyUpdateRequest request, String ip) {
        Property property = requireOwned(ownerId, propertyId);

        if (!property.getStatus().isOwnerEditable()) {
            throw ApiException.conflict("listing_not_editable",
                    "A suspended listing cannot be edited. Contact support.");
        }

        Map<String, Object> changes = new LinkedHashMap<>();
        boolean requiresReReview = false;

        if (request.title() != null) {
            property.setTitle(request.title().trim());
            changes.put("title", property.getTitle());
        }
        if (request.description() != null) {
            property.setDescription(blankToNull(request.description()));
            changes.put("description", "updated");
        }
        if (request.propertyType() != null && request.propertyType() != property.getPropertyType()) {
            property.setPropertyType(request.propertyType());
            changes.put("propertyType", request.propertyType().name());
            requiresReReview = true;
        }
        if (request.maxGuests() != null) {
            property.setMaxGuests(request.maxGuests());
            changes.put("maxGuests", request.maxGuests());
        }
        if (request.bedrooms() != null) {
            property.setBedrooms(request.bedrooms());
        }
        if (request.beds() != null) {
            property.setBeds(request.beds());
        }
        if (request.bathrooms() != null) {
            property.setBathrooms(request.bathrooms());
        }
        if (request.addressLine() != null) {
            requiresReReview |= !request.addressLine().equals(property.getAddressLine());
            property.setAddressLine(blankToNull(request.addressLine()));
            changes.put("addressLine", "updated");
        }
        if (request.district() != null) {
            property.setDistrict(blankToNull(request.district()));
        }
        if (request.city() != null) {
            requiresReReview |= !request.city().equalsIgnoreCase(property.getCity());
            property.setCity(request.city().trim());
            changes.put("city", property.getCity());
        }
        if (request.latitude() != null || request.longitude() != null) {
            Double latitude = request.latitude() != null ? request.latitude() : property.getLatitude();
            Double longitude = request.longitude() != null ? request.longitude() : property.getLongitude();
            requiresReReview |= !java.util.Objects.equals(latitude, property.getLatitude())
                    || !java.util.Objects.equals(longitude, property.getLongitude());
            property.setLocation(latitude, longitude);
            changes.put("location", "updated");
        }
        if (request.amenities() != null) {
            property.setAmenities(parseAmenities(request.amenities()));
            changes.put("amenities", property.getAmenities().size());
        }
        if (request.houseRules() != null) {
            property.setHouseRules(blankToNull(request.houseRules()));
        }
        if (request.checkInFrom() != null) {
            property.setCheckInFrom(request.checkInFrom());
        }
        if (request.checkOutBy() != null) {
            property.setCheckOutBy(request.checkOutBy());
        }
        if (request.basePrice() != null) {
            property.setBasePrice(request.basePrice());
            changes.put("basePrice", property.getBasePrice().toPlainString());
        }
        if (request.cleaningFee() != null) {
            property.setCleaningFee(request.cleaningFee());
        }
        if (request.minStayNights() != null) {
            property.setMinStayNights(request.minStayNights());
        }
        if (request.maxStayNights() != null) {
            property.setMaxStayNights(request.maxStayNights());
        }
        if (request.cancellationPolicy() != null) {
            property.setCancellationPolicy(request.cancellationPolicy());
            changes.put("cancellationPolicy", request.cancellationPolicy().name());
        }
        if (request.instantBook() != null) {
            property.setInstantBook(request.instantBook());
            changes.put("instantBook", request.instantBook());
        }

        assertStayRangeCoherent(property);

        // A live listing whose location or type changed goes back in the queue: the
        // approval was a judgement about those facts.
        if (requiresReReview && property.getStatus() == SupplyStatus.APPROVED) {
            property.submitForReview();
            changes.put("statusChange", "APPROVED -> PENDING_REVIEW");
            log.info("Listing {} returned to review after a change to {}",
                    propertyId, REVIEW_TRIGGERING_FIELDS);
        }

        propertyRepository.save(property);
        auditService.record(ownerId, AuditAction.PROPERTY_UPDATED, "Property", propertyId,
                changes, ip);
        return property;
    }

    /**
     * Submits a listing for admin review, refusing incomplete ones with the exact
     * list of what is missing rather than a generic error.
     */
    @Transactional
    public Property submitForReview(UUID ownerId, UUID propertyId, String ip) {
        Property property = requireOwned(ownerId, propertyId);

        if (!property.getStatus().isSubmittable()) {
            throw ApiException.conflict("listing_not_submittable",
                    "A listing in state " + property.getStatus() + " cannot be submitted");
        }

        List<String> problems = property.reviewReadinessProblems();
        if (!problems.isEmpty()) {
            throw ApiException.badRequest("listing_incomplete",
                    "This listing is not ready: " + String.join("; ", problems));
        }

        property.submitForReview();
        propertyRepository.save(property);
        auditService.record(ownerId, AuditAction.PROPERTY_SUBMITTED, "Property", propertyId,
                Map.of("title", property.getTitle()), ip);
        return property;
    }

    /** Owner takes a live listing out of search without losing it. */
    @Transactional
    public Property pause(UUID ownerId, UUID propertyId, String ip) {
        Property property = requireOwned(ownerId, propertyId);
        if (property.getStatus() != SupplyStatus.APPROVED) {
            throw ApiException.conflict("listing_not_live", "Only a live listing can be paused");
        }
        property.setStatus(SupplyStatus.PAUSED);
        propertyRepository.save(property);
        auditService.record(ownerId, AuditAction.PROPERTY_STATUS_CHANGED, "Property", propertyId,
                Map.of("to", "PAUSED"), ip);
        return property;
    }

    @Transactional
    public Property resume(UUID ownerId, UUID propertyId, String ip) {
        Property property = requireOwned(ownerId, propertyId);
        if (property.getStatus() != SupplyStatus.PAUSED) {
            throw ApiException.conflict("listing_not_paused", "This listing is not paused");
        }
        // Straight back to live: it was already approved, and pausing is not a
        // content change that needs re-reviewing.
        property.approve();
        propertyRepository.save(property);
        auditService.record(ownerId, AuditAction.PROPERTY_STATUS_CHANGED, "Property", propertyId,
                Map.of("to", "APPROVED"), ip);
        return property;
    }

    @Transactional(readOnly = true)
    public Page<Property> listForOwner(UUID ownerId, SupplyStatus status, Pageable pageable) {
        return status == null
                ? propertyRepository.findByOwnerId(ownerId, pageable)
                : propertyRepository.findByOwnerIdAndStatus(ownerId, status, pageable);
    }

    @Transactional(readOnly = true)
    public Property requireOwned(UUID ownerId, UUID propertyId) {
        Property property = propertyRepository.findByIdWithPhotos(propertyId)
                .orElseThrow(() -> ApiException.notFound("listing_not_found", "Listing not found"));
        if (!property.isOwnedBy(ownerId)) {
            // Not "forbidden": whether a listing exists is not this caller's business.
            throw ApiException.notFound("listing_not_found", "Listing not found");
        }
        return property;
    }

    /** The public listing page. Only approved listings from active owners resolve. */
    @Transactional(readOnly = true)
    public Property requirePubliclyVisible(UUID propertyId) {
        Property property = propertyRepository.findByIdWithPhotos(propertyId)
                .orElseThrow(() -> ApiException.notFound("listing_not_found", "Listing not found"));
        boolean visible = property.getStatus().isPubliclyVisible()
                && property.getOwner().getStatus() == mn.innex.stay.user.domain.UserStatus.ACTIVE;
        if (!visible) {
            throw ApiException.notFound("listing_not_found", "Listing not found");
        }
        return property;
    }

    /** Any listing, in any state. Admin-only: there is no ownership filter here. */
    @Transactional(readOnly = true)
    public Property requireForAdmin(UUID propertyId) {
        return propertyRepository.findByIdWithPhotos(propertyId)
                .orElseThrow(() -> ApiException.notFound("listing_not_found", "Listing not found"));
    }

    /** Admin review decision. Rejecting or suspending requires a reason. */
    @Transactional
    public Property setStatusAsAdmin(UUID actorId, UUID propertyId, SupplyStatus status,
                                     String reason, String ip) {
        Property property = propertyRepository.findByIdWithPhotos(propertyId)
                .orElseThrow(() -> ApiException.notFound("listing_not_found", "Listing not found"));

        boolean needsReason = status == SupplyStatus.REJECTED || status == SupplyStatus.SUSPENDED;
        if (needsReason && (reason == null || reason.isBlank())) {
            throw ApiException.badRequest("reason_required",
                    "A reason is required so the owner knows what to fix");
        }

        SupplyStatus previous = property.getStatus();
        switch (status) {
            case APPROVED -> {
                // An unresolved flag outranks a reviewer's approval: whoever is
                // looking at this listing may not know what was raised against it.
                ListingReviewGate gate = reviewGate.getIfAvailable();
                if (gate != null && gate.isBlocked(SupplyKind.PROPERTY, propertyId)) {
                    throw ApiException.conflict("listing_flagged",
                            "This listing has an unresolved flag and cannot be approved yet");
                }
                List<String> problems = property.reviewReadinessProblems();
                if (!problems.isEmpty()) {
                    throw ApiException.badRequest("listing_incomplete",
                            "Cannot approve an incomplete listing: " + String.join("; ", problems));
                }
                property.approve();
            }
            case REJECTED -> property.reject(reason);
            default -> property.setStatus(status);
        }
        propertyRepository.save(property);

        String action = switch (status) {
            case APPROVED -> AuditAction.PROPERTY_APPROVED;
            case REJECTED -> AuditAction.PROPERTY_REJECTED;
            default -> AuditAction.PROPERTY_STATUS_CHANGED;
        };
        auditService.record(actorId, action, "Property", propertyId,
                Map.of("from", previous.name(), "to", status.name(),
                        "reason", reason == null ? "" : reason), ip);
        return property;
    }

    @Transactional(readOnly = true)
    public Page<Property> listByStatusForAdmin(SupplyStatus status, Pageable pageable) {
        return status == null
                ? propertyRepository.findAll(pageable)
                : propertyRepository.findByStatus(status, pageable);
    }

    /**
     * Deletes a draft. Anything that has ever been live is kept, because bookings,
     * payouts and reviews reference it — those listings are paused or suspended
     * instead.
     */
    @Transactional
    public void delete(UUID ownerId, UUID propertyId, String ip) {
        Property property = requireOwned(ownerId, propertyId);
        if (property.getStatus() != SupplyStatus.DRAFT
                && property.getStatus() != SupplyStatus.REJECTED) {
            throw ApiException.conflict("listing_not_deletable",
                    "Only a draft or rejected listing can be deleted. Pause it instead.");
        }
        propertyRepository.delete(property);
        auditService.record(ownerId, AuditAction.PROPERTY_DELETED, "Property", propertyId,
                Map.of("title", property.getTitle()), ip);
    }

    private Set<Amenity> parseAmenities(List<String> names) {
        try {
            return Amenity.parseAll(names);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("unknown_amenity", ex.getMessage());
        }
    }

    private void assertStayRangeCoherent(Property property) {
        if (property.getMaxStayNights() != null
                && property.getMaxStayNights() < property.getMinStayNights()) {
            throw ApiException.badRequest("invalid_stay_range",
                    "Maximum stay cannot be shorter than minimum stay");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Property types offered in the owner's form. */
    public List<PropertyType> supportedTypes() {
        return List.of(PropertyType.values());
    }
}
