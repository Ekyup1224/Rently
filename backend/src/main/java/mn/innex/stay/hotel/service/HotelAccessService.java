package mn.innex.stay.hotel.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import mn.innex.stay.common.ApiException;
import mn.innex.stay.hotel.domain.Hotel;
import mn.innex.stay.hotel.repo.HotelRepository;
import mn.innex.stay.hotel.repo.HotelStaffRepository;
import mn.innex.stay.user.domain.Organization;
import mn.innex.stay.user.domain.Role;
import mn.innex.stay.user.domain.User;
import mn.innex.stay.user.domain.UserRole;
import mn.innex.stay.user.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who may act on which hotel.
 *
 * <p>Two levels, because a hotel is a workplace rather than a possession:
 *
 * <ul>
 *   <li>A {@code HOTEL_MANAGER} holds an organization-scoped grant and can do
 *       anything to every hotel in that organization.
 *   <li>A {@code HOTEL_STAFF} account holds the same organization-scoped grant but
 *       is additionally assigned to specific hotels, and can only read and work
 *       the front desk. Rates, inventory, room types and staff are closed to them.
 * </ul>
 *
 * <p>Authority is read from the database rather than from the token's claims, so a
 * revoked grant takes effect immediately instead of at the next token refresh.
 * Every check resolves the actor from the security context, so a hotel id in a URL
 * grants nothing on its own.
 */
@Service
public class HotelAccessService {

    private final HotelRepository hotelRepository;
    private final HotelStaffRepository staffRepository;
    private final UserRepository userRepository;

    public HotelAccessService(HotelRepository hotelRepository, HotelStaffRepository staffRepository,
                              UserRepository userRepository) {
        this.hotelRepository = hotelRepository;
        this.staffRepository = staffRepository;
        this.userRepository = userRepository;
    }

    /**
     * The hotel, if this user manages it.
     *
     * @throws ApiException 404 when the hotel does not exist or is not theirs —
     *                      whether someone else's hotel exists is not their business
     */
    @Transactional(readOnly = true)
    public Hotel requireManaged(UUID userId, UUID hotelId) {
        Hotel hotel = requireHotel(hotelId);
        if (!managedOrganizationIds(userId).contains(hotel.getOrganization().getId())) {
            throw notFound();
        }
        return hotel;
    }

    /** The hotel, if this user manages it or is assigned to it as staff. */
    @Transactional(readOnly = true)
    public Hotel requireManagedOrStaffed(UUID userId, UUID hotelId) {
        Hotel hotel = requireHotel(hotelId);
        boolean manages = managedOrganizationIds(userId).contains(hotel.getOrganization().getId());
        if (manages || staffRepository.existsByHotelIdAndUserId(hotelId, userId)) {
            return hotel;
        }
        throw notFound();
    }

    /**
     * Refuses an action that staff are not allowed to take, such as changing rates.
     *
     * @throws ApiException 403 when the user is staff rather than a manager
     */
    @Transactional(readOnly = true)
    public Hotel requireManagerForChange(UUID userId, UUID hotelId) {
        Hotel hotel = requireHotel(hotelId);
        if (managedOrganizationIds(userId).contains(hotel.getOrganization().getId())) {
            return hotel;
        }
        if (staffRepository.existsByHotelIdAndUserId(hotelId, userId)) {
            // Deliberately 403 rather than 404: they can see this hotel, so hiding
            // its existence would only be confusing.
            throw ApiException.forbidden("manager_role_required",
                    "Only a hotel manager can change this. Staff accounts can view "
                            + "reservations and handle check-in and check-out.");
        }
        throw notFound();
    }

    /** Organizations this user manages hotels for. */
    @Transactional(readOnly = true)
    public Set<UUID> managedOrganizationIds(UUID userId) {
        User user = requireUser(userId);
        return user.getRoles().stream()
                .filter(grant -> grant.getRole() == Role.HOTEL_MANAGER)
                .map(UserRole::getOrganization)
                .filter(java.util.Objects::nonNull)
                .map(Organization::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * The organization to create a hotel under.
     *
     * @param requested explicit choice, needed only when the user manages several
     * @throws ApiException 400 when the user manages none, or several and named none
     */
    @Transactional(readOnly = true)
    public Organization resolveOrganizationForNewHotel(UUID userId, UUID requested) {
        User user = requireUser(userId);
        List<Organization> managed = user.getRoles().stream()
                .filter(grant -> grant.getRole() == Role.HOTEL_MANAGER)
                .map(UserRole::getOrganization)
                .filter(java.util.Objects::nonNull)
                .toList();

        if (managed.isEmpty()) {
            throw ApiException.forbidden("no_hotel_organization",
                    "This account does not manage a hotel business yet");
        }
        if (requested != null) {
            return managed.stream()
                    .filter(organization -> organization.getId().equals(requested))
                    .findFirst()
                    .orElseThrow(() -> ApiException.badRequest("organization_not_managed",
                            "You do not manage that business"));
        }
        if (managed.size() > 1) {
            throw ApiException.badRequest("organization_required",
                    "You manage more than one business; say which one this hotel belongs to");
        }
        return managed.get(0);
    }

    /** Hotels this user can see at all, manager or staff. */
    @Transactional(readOnly = true)
    public List<UUID> staffedHotelIds(UUID userId) {
        return staffRepository.findHotelIdsForUser(userId);
    }

    private Hotel requireHotel(UUID hotelId) {
        return hotelRepository.findByIdWithDetails(hotelId).orElseThrow(HotelAccessService::notFound);
    }

    private User requireUser(UUID userId) {
        return userRepository.findByIdWithRoles(userId).orElseThrow(
                () -> ApiException.unauthorized("unauthenticated", "Authentication is required"));
    }

    private static ApiException notFound() {
        return ApiException.notFound("hotel_not_found", "Hotel not found");
    }
}
