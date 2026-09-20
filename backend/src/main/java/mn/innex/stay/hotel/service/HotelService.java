package mn.innex.stay.hotel.service;

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
import mn.innex.stay.common.supply.SupplyStatus;
import mn.innex.stay.hotel.domain.Hotel;
import mn.innex.stay.hotel.repo.HotelRepository;
import mn.innex.stay.hotel.web.dto.HotelRequests;
import mn.innex.stay.user.domain.Organization;
import mn.innex.stay.user.domain.OrganizationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import mn.innex.stay.hotel.domain.RoomType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hotel management, mirroring {@code PropertyService} for houses: create, edit,
 * submit for review, pause, and the admin decision.
 *
 * <p>The one structural difference is that a hotel is only publishable once it has
 * a sellable room type, because the hotel itself is not what a guest books.
 */
@Service
public class HotelService {

    private static final Logger log = LoggerFactory.getLogger(HotelService.class);

    private final HotelRepository hotelRepository;
    private final HotelAccessService access;
    private final AuditService auditService;
    private final ObjectProvider<ListingReviewGate> reviewGate;

    public HotelService(HotelRepository hotelRepository, HotelAccessService access,
                        AuditService auditService,
                        ObjectProvider<ListingReviewGate> reviewGate) {
        this.hotelRepository = hotelRepository;
        this.access = access;
        this.auditService = auditService;
        this.reviewGate = reviewGate;
    }

    /**
     * Loads a hotel's collections while the session is open.
     *
     * <p>A hotel has three of them — photos, room types, and each room type's
     * photos — and Hibernate will not fetch them all in one query. {@code @BatchSize}
     * turns this into a couple of extra round trips rather than a cartesian
     * product, and doing it here means a controller can map the entity to a
     * response after the transaction closes without tripping a lazy-load.
     */
    private Hotel initialize(Hotel hotel) {
        hotel.getPhotos().size();
        for (RoomType roomType : hotel.getRoomTypes()) {
            roomType.getPhotos().size();
        }
        return hotel;
    }

    @Transactional
    public Hotel create(UUID actorId, HotelRequests.CreateHotel request, String ip) {
        Organization organization =
                access.resolveOrganizationForNewHotel(actorId, request.organizationId());
        if (organization.getStatus() == OrganizationStatus.SUSPENDED) {
            throw ApiException.forbidden("organization_suspended",
                    "This business is suspended and cannot add hotels");
        }

        Hotel hotel = new Hotel(organization, request.name().trim(), request.city().trim());
        hotelRepository.save(hotel);

        auditService.record(actorId, AuditAction.HOTEL_CREATED, "Hotel", hotel.getId(),
                Map.of("name", hotel.getName(), "organizationId", organization.getId().toString()), ip);
        return initialize(hotel);
    }

    @Transactional
    public Hotel update(UUID actorId, UUID hotelId, HotelRequests.UpdateHotel request, String ip) {
        Hotel hotel = access.requireManagerForChange(actorId, hotelId);
        if (!hotel.getStatus().isOwnerEditable()) {
            throw ApiException.conflict("hotel_not_editable",
                    "A suspended hotel cannot be edited. Contact platform support.");
        }

        Map<String, Object> changes = new LinkedHashMap<>();
        boolean requiresReReview = false;

        if (request.name() != null) {
            hotel.setName(request.name().trim());
            changes.put("name", hotel.getName());
        }
        if (request.description() != null) {
            hotel.setDescription(blankToNull(request.description()));
            changes.put("description", "updated");
        }
        if (request.starRating() != null) {
            hotel.setStarRating(request.starRating());
            changes.put("starRating", request.starRating());
        }
        if (request.addressLine() != null) {
            requiresReReview |= !request.addressLine().equals(hotel.getAddressLine());
            hotel.setAddressLine(blankToNull(request.addressLine()));
            changes.put("addressLine", "updated");
        }
        if (request.district() != null) {
            hotel.setDistrict(blankToNull(request.district()));
        }
        if (request.city() != null) {
            requiresReReview |= !request.city().equalsIgnoreCase(hotel.getCity());
            hotel.setCity(request.city().trim());
            changes.put("city", hotel.getCity());
        }
        if (request.latitude() != null || request.longitude() != null) {
            Double latitude = request.latitude() != null ? request.latitude() : hotel.getLatitude();
            Double longitude = request.longitude() != null ? request.longitude() : hotel.getLongitude();
            requiresReReview |= !java.util.Objects.equals(latitude, hotel.getLatitude())
                    || !java.util.Objects.equals(longitude, hotel.getLongitude());
            hotel.setLocation(latitude, longitude);
            changes.put("location", "updated");
        }
        if (request.amenities() != null) {
            hotel.setAmenities(parseAmenities(request.amenities()));
            changes.put("amenities", hotel.getAmenities().size());
        }
        if (request.policies() != null) {
            hotel.setPolicies(blankToNull(request.policies()));
        }
        if (request.checkInFrom() != null) {
            hotel.setCheckInFrom(request.checkInFrom());
        }
        if (request.checkOutBy() != null) {
            hotel.setCheckOutBy(request.checkOutBy());
        }
        if (request.cancellationPolicy() != null) {
            hotel.setCancellationPolicy(request.cancellationPolicy());
            changes.put("cancellationPolicy", request.cancellationPolicy().name());
        }

        if (requiresReReview && hotel.getStatus() == SupplyStatus.APPROVED) {
            hotel.submitForReview();
            changes.put("statusChange", "APPROVED -> PENDING_REVIEW");
            log.info("Hotel {} returned to review after a location or address change", hotelId);
        }

        hotelRepository.save(hotel);
        auditService.record(actorId, AuditAction.HOTEL_UPDATED, "Hotel", hotelId, changes, ip);
        return initialize(hotel);
    }

    @Transactional
    public Hotel submitForReview(UUID actorId, UUID hotelId, String ip) {
        Hotel hotel = access.requireManagerForChange(actorId, hotelId);
        if (!hotel.getStatus().isSubmittable()) {
            throw ApiException.conflict("hotel_not_submittable",
                    "A hotel in state " + hotel.getStatus() + " cannot be submitted");
        }

        List<String> problems = hotel.reviewReadinessProblems();
        if (!problems.isEmpty()) {
            throw ApiException.badRequest("hotel_incomplete",
                    "This hotel is not ready: " + String.join("; ", problems));
        }

        hotel.submitForReview();
        hotelRepository.save(hotel);
        auditService.record(actorId, AuditAction.HOTEL_SUBMITTED, "Hotel", hotelId,
                Map.of("name", hotel.getName()), ip);
        return initialize(hotel);
    }

    @Transactional
    public Hotel pause(UUID actorId, UUID hotelId, String ip) {
        Hotel hotel = access.requireManagerForChange(actorId, hotelId);
        if (hotel.getStatus() != SupplyStatus.APPROVED) {
            throw ApiException.conflict("hotel_not_live", "Only a live hotel can be paused");
        }
        hotel.setStatus(SupplyStatus.PAUSED);
        hotelRepository.save(hotel);
        auditService.record(actorId, AuditAction.HOTEL_STATUS_CHANGED, "Hotel", hotelId,
                Map.of("to", "PAUSED"), ip);
        return initialize(hotel);
    }

    @Transactional
    public Hotel resume(UUID actorId, UUID hotelId, String ip) {
        Hotel hotel = access.requireManagerForChange(actorId, hotelId);
        if (hotel.getStatus() != SupplyStatus.PAUSED) {
            throw ApiException.conflict("hotel_not_paused", "This hotel is not paused");
        }
        hotel.approve();
        hotelRepository.save(hotel);
        auditService.record(actorId, AuditAction.HOTEL_STATUS_CHANGED, "Hotel", hotelId,
                Map.of("to", "APPROVED"), ip);
        return initialize(hotel);
    }

    /** Hotels the actor can see: everything in the orgs they manage, plus any they staff. */
    @Transactional(readOnly = true)
    public Page<Hotel> listForActor(UUID actorId, SupplyStatus status, Pageable pageable) {
        Set<UUID> organizationIds = access.managedOrganizationIds(actorId);
        if (!organizationIds.isEmpty()) {
            // A manager of several businesses is rare; the common case is one.
            UUID organizationId = organizationIds.iterator().next();
            Page<Hotel> page = status == null
                    ? hotelRepository.findByOrganizationId(organizationId, pageable)
                    : hotelRepository.findByOrganizationIdAndStatus(organizationId, status, pageable);
            page.forEach(this::initialize);
            return page;
        }
        List<UUID> staffed = access.staffedHotelIds(actorId);
        if (staffed.isEmpty()) {
            return Page.empty(pageable);
        }
        Page<Hotel> page = hotelRepository.findByIdIn(staffed, pageable);
        page.forEach(this::initialize);
        return page;
    }

    @Transactional(readOnly = true)
    public Hotel requireForActor(UUID actorId, UUID hotelId) {
        return initialize(access.requireManagedOrStaffed(actorId, hotelId));
    }

    /** The public hotel page. Only approved hotels with an active business resolve. */
    @Transactional(readOnly = true)
    public Hotel requirePubliclyVisible(UUID hotelId) {
        Hotel hotel = hotelRepository.findByIdWithDetails(hotelId)
                .orElseThrow(() -> ApiException.notFound("hotel_not_found", "Hotel not found"));
        boolean visible = hotel.getStatus().isPubliclyVisible()
                && hotel.getOrganization().getStatus() == OrganizationStatus.ACTIVE;
        if (!visible) {
            throw ApiException.notFound("hotel_not_found", "Hotel not found");
        }
        return initialize(hotel);
    }

    @Transactional(readOnly = true)
    public Hotel requireForAdmin(UUID hotelId) {
        return initialize(hotelRepository.findByIdWithDetails(hotelId)
                .orElseThrow(() -> ApiException.notFound("hotel_not_found", "Hotel not found")));
    }

    @Transactional(readOnly = true)
    public Page<Hotel> listByStatusForAdmin(SupplyStatus status, Pageable pageable) {
        Page<Hotel> page = status == null
                ? hotelRepository.findAll(pageable)
                : hotelRepository.findByStatus(status, pageable);
        page.forEach(this::initialize);
        return page;
    }

    @Transactional
    public Hotel setStatusAsAdmin(UUID actorId, UUID hotelId, SupplyStatus status, String reason,
                                  String ip) {
        Hotel hotel = requireForAdmin(hotelId);

        boolean needsReason = status == SupplyStatus.REJECTED || status == SupplyStatus.SUSPENDED;
        if (needsReason && (reason == null || reason.isBlank())) {
            throw ApiException.badRequest("reason_required",
                    "A reason is required so the hotel knows what to fix");
        }

        SupplyStatus previous = hotel.getStatus();
        switch (status) {
            case APPROVED -> {
                // An unresolved flag outranks a reviewer's approval: whoever is
                // looking at this listing may not know what was raised against it.
                ListingReviewGate gate = reviewGate.getIfAvailable();
                if (gate != null && gate.isBlocked(SupplyKind.HOTEL, hotelId)) {
                    throw ApiException.conflict("listing_flagged",
                            "This listing has an unresolved flag and cannot be approved yet");
                }
                List<String> problems = hotel.reviewReadinessProblems();
                if (!problems.isEmpty()) {
                    throw ApiException.badRequest("hotel_incomplete",
                            "Cannot approve an incomplete hotel: " + String.join("; ", problems));
                }
                hotel.approve();
            }
            case REJECTED -> hotel.reject(reason);
            default -> hotel.setStatus(status);
        }
        hotelRepository.save(hotel);

        String action = switch (status) {
            case APPROVED -> AuditAction.HOTEL_APPROVED;
            case REJECTED -> AuditAction.HOTEL_REJECTED;
            default -> AuditAction.HOTEL_STATUS_CHANGED;
        };
        auditService.record(actorId, action, "Hotel", hotelId,
                Map.of("from", previous.name(), "to", status.name(),
                        "reason", reason == null ? "" : reason), ip);
        return initialize(hotel);
    }

    private Set<Amenity> parseAmenities(List<String> names) {
        try {
            return Amenity.parseAll(names);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("unknown_amenity", ex.getMessage());
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
