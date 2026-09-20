package mn.innex.stay.hotel.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import mn.innex.stay.common.supply.CancellationPolicy;
import mn.innex.stay.common.supply.SupplyStatus;
import mn.innex.stay.hotel.domain.Hotel;
import mn.innex.stay.hotel.domain.HotelPhoto;
import mn.innex.stay.hotel.domain.HotelStaffAssignment;
import mn.innex.stay.hotel.domain.RoomType;
import mn.innex.stay.hotel.domain.RoomTypeStatus;
import mn.innex.stay.hotel.service.HotelPhotoService;

/** Response payloads for the hotel side, grouped because they are always read together. */
public final class HotelResponses {

    private HotelResponses() {
    }

    public record Photo(UUID id, String url, String altText, int sortOrder, boolean cover) {
    }

    /**
     * A hotel as its manager or an admin sees it: everything, review state included.
     * The guest-facing shape is {@link PublicHotel}.
     *
     * @param readinessProblems what still blocks submitting, so the form can show a checklist
     */
    public record ManagedHotel(
            UUID id,
            UUID organizationId,
            String organizationName,
            String name,
            String description,
            Integer starRating,
            String addressLine,
            String district,
            String city,
            String country,
            Double latitude,
            Double longitude,
            List<String> amenities,
            String policies,
            LocalTime checkInFrom,
            LocalTime checkOutBy,
            String currency,
            CancellationPolicy cancellationPolicy,
            SupplyStatus status,
            String rejectionReason,
            List<String> readinessProblems,
            List<Photo> photos,
            List<ManagedRoomType> roomTypes,
            Instant publishedAt,
            Instant createdAt,
            Instant updatedAt) {

        public static ManagedHotel from(Hotel hotel, HotelPhotoService photos) {
            return new ManagedHotel(
                    hotel.getId(), hotel.getOrganization().getId(),
                    hotel.getOrganization().getName(), hotel.getName(), hotel.getDescription(),
                    hotel.getStarRating(), hotel.getAddressLine(), hotel.getDistrict(),
                    hotel.getCity(), hotel.getCountry(), hotel.getLatitude(), hotel.getLongitude(),
                    hotel.getAmenities(), hotel.getPolicies(), hotel.getCheckInFrom(),
                    hotel.getCheckOutBy(), hotel.getCurrency(), hotel.getCancellationPolicy(),
                    hotel.getStatus(), hotel.getRejectionReason(), hotel.reviewReadinessProblems(),
                    hotel.getPhotos().stream().map(photo -> toPhoto(photo, photos)).toList(),
                    hotel.getRoomTypes().stream()
                            .map(roomType -> ManagedRoomType.from(roomType, photos)).toList(),
                    hotel.getPublishedAt(), hotel.getCreatedAt(), hotel.getUpdatedAt());
        }
    }

    /** @param totalRooms physical rooms, and the default availability per night */
    public record ManagedRoomType(
            UUID id,
            UUID hotelId,
            String name,
            String description,
            int capacity,
            String bedConfig,
            Integer sizeSqm,
            BigDecimal basePrice,
            int totalRooms,
            List<String> amenities,
            int minStayNights,
            Integer maxStayNights,
            RoomTypeStatus status,
            int sortOrder,
            List<Photo> photos) {

        public static ManagedRoomType from(RoomType roomType, HotelPhotoService photos) {
            return new ManagedRoomType(
                    roomType.getId(), roomType.getHotel().getId(), roomType.getName(),
                    roomType.getDescription(), roomType.getCapacity(), roomType.getBedConfig(),
                    roomType.getSizeSqm(), roomType.getBasePrice(), roomType.getTotalRooms(),
                    roomType.getAmenities(), roomType.getMinStayNights(),
                    roomType.getMaxStayNights(), roomType.getStatus(), roomType.getSortOrder(),
                    roomType.getPhotos().stream()
                            .map(photo -> new Photo(photo.getId(),
                                    photos.publicUrl(photo.getStorageKey()), photo.getAltText(),
                                    photo.getSortOrder(), photo.isCover()))
                            .toList());
        }
    }

    /**
     * The guest-facing hotel page.
     *
     * <p>Like the house listing page, this omits owner-only facts: no review state,
     * no rejection reason. Unlike it, the street address <em>is</em> included — a
     * hotel is a public business at a published address, not someone's home.
     */
    public record PublicHotel(
            UUID id,
            String name,
            String description,
            Integer starRating,
            String addressLine,
            String district,
            String city,
            String country,
            Double latitude,
            Double longitude,
            List<String> amenities,
            String policies,
            LocalTime checkInFrom,
            LocalTime checkOutBy,
            String currency,
            CancellationPolicy cancellationPolicy,
            List<Photo> photos,
            /** Guest reviews. Distinct from starRating, which is the official class. */
            java.math.BigDecimal ratingAverage,
            int ratingCount,
            List<PublicRoomType> roomTypes) {

        public static PublicHotel from(Hotel hotel, List<RoomType> sellable,
                                       HotelPhotoService photos) {
            return new PublicHotel(
                    hotel.getId(), hotel.getName(), hotel.getDescription(), hotel.getStarRating(),
                    hotel.getAddressLine(), hotel.getDistrict(), hotel.getCity(),
                    hotel.getCountry(), hotel.getLatitude(), hotel.getLongitude(),
                    hotel.getAmenities(), hotel.getPolicies(), hotel.getCheckInFrom(),
                    hotel.getCheckOutBy(), hotel.getCurrency(), hotel.getCancellationPolicy(),
                    hotel.getPhotos().stream().map(photo -> toPhoto(photo, photos)).toList(),
                    hotel.getRatingAverage(), hotel.getRatingCount(),
                    sellable.stream().map(roomType -> PublicRoomType.from(roomType, photos)).toList());
        }
    }

    /**
     * A bookable room type as a guest sees it.
     *
     * <p>{@code totalRooms} is deliberately absent: how many rooms a hotel has is
     * its business, and a guest only needs to know whether their dates are free —
     * which the availability endpoint answers.
     */
    public record PublicRoomType(
            UUID id,
            String name,
            String description,
            int capacity,
            String bedConfig,
            Integer sizeSqm,
            BigDecimal nightlyFrom,
            List<String> amenities,
            int minStayNights,
            Integer maxStayNights,
            List<Photo> photos) {

        public static PublicRoomType from(RoomType roomType, HotelPhotoService photos) {
            return new PublicRoomType(
                    roomType.getId(), roomType.getName(), roomType.getDescription(),
                    roomType.getCapacity(), roomType.getBedConfig(), roomType.getSizeSqm(),
                    roomType.getBasePrice(), roomType.getAmenities(), roomType.getMinStayNights(),
                    roomType.getMaxStayNights(),
                    roomType.getPhotos().stream()
                            .map(photo -> new Photo(photo.getId(),
                                    photos.publicUrl(photo.getStorageKey()), photo.getAltText(),
                                    photo.getSortOrder(), photo.isCover()))
                            .toList());
        }
    }

    /**
     * Whether a room type can take a stay, and what it would cost.
     *
     * @param roomsLeft rooms still sellable for the whole stay — the minimum across
     *                  its nights, since a stay needs every night
     * @param unavailableReason why it cannot be booked, when {@code bookable} is false
     */
    public record RoomTypeAvailability(
            UUID roomTypeId,
            String name,
            int capacity,
            boolean bookable,
            String unavailableReason,
            int roomsLeft,
            BigDecimal nightlyFrom,
            BigDecimal totalForStay,
            String currency,
            List<Photo> photos) {
    }

    public record StaffMember(
            UUID userId,
            String phone,
            String fullName,
            Instant assignedAt) {

        public static StaffMember from(HotelStaffAssignment assignment) {
            return new StaffMember(assignment.getUser().getId(), assignment.getUser().getPhone(),
                    assignment.getUser().getFullName(), assignment.getAssignedAt());
        }
    }

    /**
     * How a hotel performed over a period.
     *
     * @param occupancyPercent sold room-nights as a share of those offered for sale
     * @param averageDailyRate revenue divided by sold room-nights, the standard ADR
     */
    public record OccupancyReport(
            java.time.LocalDate from,
            java.time.LocalDate to,
            int roomNightsAvailable,
            int roomNightsSold,
            BigDecimal occupancyPercent,
            BigDecimal roomRevenue,
            BigDecimal averageDailyRate,
            String currency,
            int reservations) {
    }

    private static Photo toPhoto(HotelPhoto photo, HotelPhotoService photos) {
        return new Photo(photo.getId(), photos.publicUrl(photo.getStorageKey()),
                photo.getAltText(), photo.getSortOrder(), photo.isCover());
    }
}
