package mn.innex.stay.trust.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import mn.innex.stay.common.web.ClientIp;
import mn.innex.stay.security.CurrentActor;
import mn.innex.stay.trust.service.KycService;
import mn.innex.stay.trust.web.dto.TrustRequests;
import mn.innex.stay.trust.web.dto.TrustResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A host submitting identity documents, and checking where they got to.
 *
 * <p>Anyone signed in may submit: verification is about being paid, and a guest
 * who later becomes a host should not have to start again.
 */
@RestController
@RequestMapping("/api/v1/users/me/kyc")
public class KycController {

    private final KycService kyc;

    public KycController(KycService kyc) {
        this.kyc = kyc;
    }

    /** The host's own view: no document number, since they already have it. */
    @GetMapping
    public ResponseEntity<TrustResponses.KycView> mine() {
        return kyc.latestFor(CurrentActor.requireUserId())
                .map(TrustResponses.KycView::forSelf)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TrustResponses.KycView submit(@Valid @RequestBody TrustRequests.SubmitKyc request,
                                         HttpServletRequest httpRequest) {
        return TrustResponses.KycView.forSelf(kyc.submit(CurrentActor.requireUserId(),
                request.documentType(), request.documentNumber(), request.fullName(),
                request.documentImageKey(), request.selfieImageKey(),
                ClientIp.of(httpRequest)));
    }
}
