package mn.innex.stay.trust.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import mn.innex.stay.common.audit.AuditAction;
import mn.innex.stay.common.audit.AuditService;
import mn.innex.stay.common.supply.SupplyKind;
import mn.innex.stay.common.web.ClientIp;
import mn.innex.stay.security.CurrentActor;
import mn.innex.stay.trust.domain.FlagType;
import mn.innex.stay.trust.service.ListingFlagService;
import mn.innex.stay.trust.web.dto.TrustRequests;
import mn.innex.stay.trust.web.dto.TrustResponses;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * How a guest says a listing is not what it claims to be.
 *
 * <p>The report costs the guest nothing and freezes the host's money
 * immediately, which is the right asymmetry: a wrong report delays an honest
 * host by a day, while a missing one pays a fraud in full. Signing in is
 * required, so a competitor cannot bury a rival anonymously, and every report
 * carries the reporter's id into the audit log.
 */
@RestController
@RequestMapping("/api/v1")
public class GuestReportController {

    private final ListingFlagService flags;
    private final AuditService auditService;

    public GuestReportController(ListingFlagService flags, AuditService auditService) {
        this.flags = flags;
        this.auditService = auditService;
    }

    @PostMapping("/listings/{propertyId}/report")
    @ResponseStatus(HttpStatus.CREATED)
    public TrustResponses.FlagView reportListing(@PathVariable UUID propertyId,
                                                 @Valid @RequestBody TrustRequests.ReportListing request,
                                                 HttpServletRequest httpRequest) {
        return report(SupplyKind.PROPERTY, propertyId, request, httpRequest);
    }

    @PostMapping("/hotels/{hotelId}/report")
    @ResponseStatus(HttpStatus.CREATED)
    public TrustResponses.FlagView reportHotel(@PathVariable UUID hotelId,
                                               @Valid @RequestBody TrustRequests.ReportListing request,
                                               HttpServletRequest httpRequest) {
        return report(SupplyKind.HOTEL, hotelId, request, httpRequest);
    }

    private TrustResponses.FlagView report(SupplyKind kind, UUID supplyId,
                                           TrustRequests.ReportListing request,
                                           HttpServletRequest httpRequest) {
        UUID reporterId = CurrentActor.requireUserId();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", request.reason());
        if (request.details() != null && !request.details().isBlank()) {
            details.put("details", request.details());
        }

        var flag = flags.raise(kind, supplyId, FlagType.GUEST_REPORT, reporterId,
                request.bookingId(), "Guest report: " + request.reason(), details);

        auditService.record(reporterId, AuditAction.LISTING_REPORTED, kind.name(), supplyId,
                Map.of("reason", request.reason(), "flagId", flag.getId().toString()),
                ClientIp.of(httpRequest));
        return TrustResponses.FlagView.from(flag);
    }
}
