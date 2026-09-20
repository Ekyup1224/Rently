package mn.innex.stay.trust.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.audit.AuditAction;
import mn.innex.stay.common.audit.AuditService;
import mn.innex.stay.trust.domain.KycDocumentType;
import mn.innex.stay.trust.domain.KycSubmission;
import mn.innex.stay.trust.repo.KycSubmissionRepository;
import mn.innex.stay.user.domain.KycStatus;
import mn.innex.stay.user.domain.User;
import mn.innex.stay.user.repo.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Identity checks for hosts.
 *
 * <p>Verification is not required to list, only to be paid. That ordering is
 * deliberate: asking for documents before someone has seen any value from the
 * platform costs more supply than it saves in fraud, while asking before money
 * moves costs a fraudster the one thing they cannot fake cheaply — a real
 * identity attached to a real bank account.
 */
@Service
public class KycService {

    private final KycSubmissionRepository submissions;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public KycService(KycSubmissionRepository submissions, UserRepository userRepository,
                      AuditService auditService) {
        this.submissions = submissions;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    /**
     * Files documents for review and moves the account to PENDING.
     *
     * @throws ApiException 409 when one is already awaiting review, so a queue
     *                      cannot be flooded by one account
     */
    @Transactional
    public KycSubmission submit(UUID userId, KycDocumentType documentType, String documentNumber,
                                String fullName, String documentImageKey, String selfieImageKey,
                                String ip) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("user_not_found", "No such user"));

        if (submissions.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, KycStatus.PENDING)
                .isPresent()) {
            throw ApiException.conflict("kyc_already_pending",
                    "Your documents are already being reviewed");
        }
        if (user.getKycStatus() == KycStatus.VERIFIED) {
            throw ApiException.conflict("kyc_already_verified", "This account is already verified");
        }

        KycSubmission submission = submissions.save(new KycSubmission(user, documentType,
                documentNumber, fullName, documentImageKey, selfieImageKey));
        user.setKycStatus(KycStatus.PENDING);
        userRepository.save(user);

        auditService.record(userId, AuditAction.KYC_SUBMITTED, "KycSubmission", submission.getId(),
                Map.of("documentType", documentType.name()), ip);
        return submission;
    }

    @Transactional(readOnly = true)
    public Optional<KycSubmission> latestFor(UUID userId) {
        return submissions.findFirstByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Page<KycSubmission> queue(List<KycStatus> statuses, Pageable pageable) {
        return submissions.findByStatusIn(
                statuses == null || statuses.isEmpty() ? List.of(KycStatus.PENDING) : statuses,
                pageable);
    }

    /**
     * Records a reviewer's decision on both the submission and the account.
     *
     * <p>The account status is what the payout rules read, so the two are written
     * together rather than left to drift apart.
     */
    @Transactional
    public KycSubmission review(UUID adminId, UUID submissionId, KycStatus outcome, String note,
                                String ip) {
        if (outcome != KycStatus.VERIFIED && outcome != KycStatus.REJECTED) {
            throw ApiException.badRequest("invalid_outcome",
                    "A submission is either verified or rejected");
        }
        KycSubmission submission = submissions.findById(submissionId)
                .orElseThrow(() -> ApiException.notFound("kyc_not_found", "No such submission"));
        if (submission.getStatus() != KycStatus.PENDING) {
            throw ApiException.conflict("kyc_already_reviewed",
                    "That submission has already been reviewed");
        }
        if (outcome == KycStatus.REJECTED && (note == null || note.isBlank())) {
            throw ApiException.badRequest("reason_required",
                    "A rejection needs a reason the host can act on");
        }

        submission.review(outcome, adminId, note);
        submissions.save(submission);

        User user = submission.getUser();
        user.setKycStatus(outcome);
        userRepository.save(user);

        auditService.record(adminId, AuditAction.KYC_REVIEWED, "KycSubmission", submissionId,
                Map.of("outcome", outcome.name(), "userId", user.getId().toString()), ip);
        return submission;
    }
}
