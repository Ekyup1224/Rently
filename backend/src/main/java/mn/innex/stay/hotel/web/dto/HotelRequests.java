package mn.innex.stay.hotel.web.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import mn.innex.stay.common.supply.CancellationPolicy;
import mn.innex.stay.common.supply.SupplyStatus;
import mn.innex.stay.hotel.domain.RoomTypeStatus;

/**
 * Request payloads for the hotel side. Grouped in one file because they are all
 * small and always read together.
 */
public final class HotelRequests {

    private HotelRequests() {
    }

    /**
     * Starts a hotel with the essentials.
     *
     * @param organizationId needed only when the manager runs more than one business
     */
    public record CreateHotel(
            @NotBlank @Size(max = 180) String name,
            @NotBlank @Size(max = 120) String city,
            UUID organizationId) {
    }

    /**
     * Partial update: null means leave alone.
     *
     * <p>As with houses, changing the location or address of a live hotel returns
     * it to review, because the approval was a judgement about those facts.
     */
    public record UpdateHotel(
            @Size(max = 180) String name,
            @Size(max = 8000) String description,
            @Min(1) @Max(5) Integer starRating,
            @Size(max = 255) String addressLine,
            @Size(max = 120) String district,
            @Size(max = 120) String city,
            Double latitude,
            Double longitude,
            List<String> amenities,
            @Size(max = 4000) String policies,
            LocalTime checkInFrom,
            LocalTime checkOutBy,
            CancellationPolicy cancellationPolicy) {
    }

    /** A new room type. Capacity is guests per room, not per booking. */
    public record CreateRoomType(
            @NotBlank @Size(max = 120) String name,
            @Min(1) @Max(20) int capacity,
            @Min(1) @Max(2000) int totalRooms,
            @NotNull @DecimalMin("0") BigDecimal basePrice) {
    }

    public record UpdateRoomType(
            @Size(max = 120) String name,
            @Size(max = 4000) String description,
            @Min(1) @Max(20) Integer capacity,
            @Size(max = 160) String bedConfig,
            @Min(1) Integer sizeSqm,
            @DecimalMin("0") BigDecimal basePrice,
            @Min(1) @Max(2000) Integer totalRooms,
            List<String> amenities,
            @Min(1) Integer minStayNights,
            @Min(1) Integer maxStayNights,
            RoomTypeStatus status,
            Integer sortOrder) {
    }

    /**
     * Bulk inventory edit over a range.
     *
     * @param to              inclusive — a calendar is a set of days, not a stay
     * @param weekdays        restricts the edit to these days; empty means all
     * @param availableCount  rooms offered for sale; cannot go below what is sold
     * @param clearRate       returns those nights to the room type's base price
     */
    public record UpdateInventory(
            @NotNull java.time.LocalDate from,
            @NotNull java.time.LocalDate to,
            List<java.time.DayOfWeek> weekdays,
            @Min(0) Integer availableCount,
            @DecimalMin("0") BigDecimal rate,
            Boolean clearRate,
            Boolean stopSell,
            @Min(1) Integer minStayNights,
            Boolean clearMinStay) {

        public boolean clearRateRequested() {
            return Boolean.TRUE.equals(clearRate);
        }

        public boolean clearMinStayRequested() {
            return Boolean.TRUE.equals(clearMinStay);
        }
    }

    /** Adds an existing account to a hotel's front-desk staff. */
    public record AddStaff(@NotBlank @Size(max = 32) String phone) {
    }

    /** Admin review decision. A reason is required to reject or suspend. */
    public record HotelStatusDecision(
            @NotNull SupplyStatus status,
            @Size(max = 2000) String reason) {
    }

    /** Photo upload handshake, identical in shape to the house flow. */
    public record PhotoUploadUrl(
            @NotBlank @Pattern(regexp = "image/(jpeg|png|webp)",
                    message = "contentType must be image/jpeg, image/png or image/webp")
            String contentType,
            @Min(1) long sizeBytes) {
    }

    public record ConfirmPhoto(
            @NotBlank @Size(max = 512) String storageKey,
            @Size(max = 255) String altText) {
    }

    public record ReorderPhotos(
            @jakarta.validation.constraints.NotEmpty List<UUID> photoIdsInOrder,
            UUID coverPhotoId) {
    }
}
