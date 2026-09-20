package mn.innex.stay.listing.web;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import mn.innex.stay.common.web.ClientIp;
import mn.innex.stay.common.web.PageResponse;
import mn.innex.stay.common.supply.SupplyStatus;
import mn.innex.stay.listing.service.PropertyService;
import mn.innex.stay.listing.storage.ObjectStorage;
import mn.innex.stay.listing.web.dto.PropertyCreateRequest;
import mn.innex.stay.listing.web.dto.PropertyResponse;
import mn.innex.stay.listing.web.dto.PropertyUpdateRequest;
import mn.innex.stay.security.CurrentActor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A house owner's own listings.
 *
 * <p>Every method resolves the owner from the security context and every lookup
 * goes through an ownership check, so a listing id in the URL grants nothing.
 * {@code HOUSE_OWNER} is required to reach any of it.
 */
@RestController
@RequestMapping("/api/v1/owner/properties")
@PreAuthorize("hasRole('HOUSE_OWNER')")
public class OwnerPropertyController {

    private final PropertyService propertyService;
    private final ObjectStorage storage;

    public OwnerPropertyController(PropertyService propertyService, ObjectStorage storage) {
        this.propertyService = propertyService;
        this.storage = storage;
    }

    /** Starts a draft. Only the essentials are required so the first screen can save. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PropertyResponse create(@Valid @RequestBody PropertyCreateRequest request,
                                   HttpServletRequest httpRequest) {
        return PropertyResponse.from(propertyService.create(
                CurrentActor.requireUserId(), request, ClientIp.of(httpRequest)), storage);
    }

    @GetMapping
    public PageResponse<PropertyResponse> list(
            @RequestParam(required = false) SupplyStatus status,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return PageResponse.of(
                propertyService.listForOwner(CurrentActor.requireUserId(), status, pageable),
                property -> PropertyResponse.from(property, storage));
    }

    @GetMapping("/{propertyId}")
    public PropertyResponse get(@PathVariable UUID propertyId) {
        return PropertyResponse.from(
                propertyService.requireOwned(CurrentActor.requireUserId(), propertyId), storage);
    }

    /**
     * Partial update. Changing the location or property type of a live listing
     * returns it to review — the response's {@code status} says so.
     */
    @PatchMapping("/{propertyId}")
    public PropertyResponse update(@PathVariable UUID propertyId,
                                   @Valid @RequestBody PropertyUpdateRequest request,
                                   HttpServletRequest httpRequest) {
        return PropertyResponse.from(propertyService.update(
                CurrentActor.requireUserId(), propertyId, request, ClientIp.of(httpRequest)),
                storage);
    }

    /** Submits for admin review, refusing an incomplete listing with the specifics. */
    @PostMapping("/{propertyId}/submit")
    public PropertyResponse submit(@PathVariable UUID propertyId, HttpServletRequest httpRequest) {
        return PropertyResponse.from(propertyService.submitForReview(
                CurrentActor.requireUserId(), propertyId, ClientIp.of(httpRequest)), storage);
    }

    /** Takes a live listing out of search without deleting it. */
    @PostMapping("/{propertyId}/pause")
    public PropertyResponse pause(@PathVariable UUID propertyId, HttpServletRequest httpRequest) {
        return PropertyResponse.from(propertyService.pause(
                CurrentActor.requireUserId(), propertyId, ClientIp.of(httpRequest)), storage);
    }

    @PostMapping("/{propertyId}/resume")
    public PropertyResponse resume(@PathVariable UUID propertyId, HttpServletRequest httpRequest) {
        return PropertyResponse.from(propertyService.resume(
                CurrentActor.requireUserId(), propertyId, ClientIp.of(httpRequest)), storage);
    }

    /** Drafts only. A listing that has been live is paused, never deleted. */
    @DeleteMapping("/{propertyId}")
    public ResponseEntity<Void> delete(@PathVariable UUID propertyId,
                                       HttpServletRequest httpRequest) {
        propertyService.delete(CurrentActor.requireUserId(), propertyId, ClientIp.of(httpRequest));
        return ResponseEntity.noContent().build();
    }
}
