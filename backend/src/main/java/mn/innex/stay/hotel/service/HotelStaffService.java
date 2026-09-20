package mn.innex.stay.hotel.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.PhoneNumbers;
import mn.innex.stay.common.audit.AuditAction;
import mn.innex.stay.common.audit.AuditService;
import mn.innex.stay.hotel.domain.Hotel;
import mn.innex.stay.hotel.domain.HotelStaffAssignment;
import mn.innex.stay.hotel.repo.HotelStaffRepository;
import mn.innex.stay.user.domain.Role;
import mn.innex.stay.user.domain.User;
import mn.innex.stay.user.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Front-desk staff for a hotel.
 *
 * <p>A manager adds an existing account by phone number — the person signs up as a
 * guest first, which means their number is already verified. Adding them grants
 * {@code HOTEL_STAFF} scoped to the manager's organization and assigns them to
 * this hotel, so they can work the desk here and nowhere else.
 *
 * <p>What staff may do is fixed for now (reservations, check-in and check-out; no
 * rates, inventory, room types or staff), enforced by
 * {@link HotelAccessService#requireManagerForChange}.
 */
@Service
public class HotelStaffService {

    private final HotelStaffRepository staffRepository;
    private final UserRepository userRepository;
    private final HotelAccessService access;
    private final AuditService auditService;

    public HotelStaffService(HotelStaffRepository staffRepository, UserRepository userRepository,
                             HotelAccessService access, AuditService auditService) {
        this.staffRepository = staffRepository;
        this.userRepository = userRepository;
        this.access = access;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<HotelStaffAssignment> list(UUID actorId, UUID hotelId) {
        access.requireManagerForChange(actorId, hotelId);
        return staffRepository.findByHotelId(hotelId);
    }

    /**
     * Adds an account to this hotel's staff.
     *
     * @throws ApiException 404 when no account exists for that number — deliberately
     *                      not created here, because staff must verify their own phone
     */
    @Transactional
    public HotelStaffAssignment add(UUID actorId, UUID hotelId, String rawPhone, String ip) {
        Hotel hotel = access.requireManagerForChange(actorId, hotelId);
        String phone = PhoneNumbers.normalize(rawPhone);

        User staff = userRepository.findByPhone(phone).orElseThrow(
                () -> ApiException.notFound("user_not_found",
                        "No account exists for that number. Ask them to sign up first, "
                                + "then add them here."));

        if (staff.getId().equals(actorId)) {
            throw ApiException.badRequest("cannot_add_self",
                    "You already manage this hotel; you do not need a staff assignment");
        }
        if (staffRepository.existsByHotelIdAndUserId(hotelId, staff.getId())) {
            throw ApiException.conflict("already_staff", "They are already staff at this hotel");
        }

        // Grant the role scoped to this hotel's organization if they do not hold it.
        boolean granted = staff.grantRole(Role.HOTEL_STAFF, hotel.getOrganization(), actorId);
        if (granted) {
            userRepository.save(staff);
        }

        HotelStaffAssignment assignment = staffRepository.save(
                new HotelStaffAssignment(hotel, staff, actorId));

        auditService.record(actorId, AuditAction.HOTEL_STAFF_ADDED, "Hotel", hotelId,
                Map.of("userId", staff.getId().toString(),
                        "phone", PhoneNumbers.mask(phone),
                        "roleGranted", granted), ip);
        return assignment;
    }

    /**
     * Removes a staff assignment. The organization-scoped role grant is left in
     * place if they still work at another hotel in the same business.
     */
    @Transactional
    public void remove(UUID actorId, UUID hotelId, UUID userId, String ip) {
        Hotel hotel = access.requireManagerForChange(actorId, hotelId);
        HotelStaffAssignment assignment = staffRepository.findByHotelIdAndUserId(hotelId, userId)
                .orElseThrow(() -> ApiException.notFound("assignment_not_found",
                        "They are not staff at this hotel"));

        staffRepository.delete(assignment);
        staffRepository.flush();

        // No hotels left in this organization means the role no longer applies.
        boolean stillStaffSomewhere = staffRepository.findHotelIdsForUser(userId).stream()
                .anyMatch(otherHotelId -> !otherHotelId.equals(hotelId));
        if (!stillStaffSomewhere) {
            userRepository.findByIdWithRoles(userId).ifPresent(staff -> {
                if (staff.revokeRole(Role.HOTEL_STAFF, hotel.getOrganization())) {
                    userRepository.save(staff);
                }
            });
        }

        auditService.record(actorId, AuditAction.HOTEL_STAFF_REMOVED, "Hotel", hotelId,
                Map.of("userId", userId.toString(), "roleRevoked", !stillStaffSomewhere), ip);
    }
}
