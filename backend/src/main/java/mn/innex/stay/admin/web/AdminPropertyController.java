package mn.innex.stay.admin.web;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import mn.innex.stay.common.web.ClientIp;
import mn.innex.stay.common.web.PageResponse;
import mn.innex.stay.common.supply.SupplyStatus;
import mn.innex.stay.listing.service.PropertyService;
import mn.innex.stay.listing.storage.ObjectStorage;
import mn.innex.stay.listing.web.dto.AdminPropertyStatusRequest;
import mn.innex.stay.listing.web.dto.PropertyResponse;
import mn.innex.stay.security.CurrentActor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Listing review.
 *
 * <p>Deliberately minimal for Step 2: the queue and the decision, which is all the
 * house rental loop needs to be demonstrable end to end. The full console — with
 * document review, moderation and analytics — is Step 4.
 */
@RestController
@RequestMapping("/api/v1/admin/properties")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminPropertyController {

    private final PropertyService propertyService;
    private final ObjectStorage storage;

    public AdminPropertyController(PropertyService propertyService, ObjectStorage storage) {
        this.propertyService = propertyService;
        this.storage = storage;
    }

    /**
     * The review queue.
     *
     * @param status defaults to nothing, meaning every listing; pass
     *               {@code PENDING_REVIEW} for the queue itself
     */
    @GetMapping
    public PageResponse<PropertyResponse> list(
            @RequestParam(required = false) SupplyStatus status,
            @PageableDefault(size = 25, sort = "updatedAt", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return PageResponse.of(propertyService.listByStatusForAdmin(status, pageable),
                property -> PropertyResponse.from(property, storage));
    }

    @GetMapping("/{propertyId}")
    public PropertyResponse get(@PathVariable UUID propertyId) {
        return PropertyResponse.from(propertyService.requireForAdmin(propertyId), storage);
    }

    /**
     * Approves, rejects or suspends a listing. Rejection and suspension require a
     * reason, which the owner sees — a listing cannot be fixed blind.
     */
    @PatchMapping("/{propertyId}/status")
    public PropertyResponse setStatus(@PathVariable UUID propertyId,
                                      @Valid @RequestBody AdminPropertyStatusRequest request,
                                      HttpServletRequest httpRequest) {
        return PropertyResponse.from(propertyService.setStatusAsAdmin(
                CurrentActor.requireUserId(), propertyId, request.status(), request.reason(),
                ClientIp.of(httpRequest)), storage);
    }
}
