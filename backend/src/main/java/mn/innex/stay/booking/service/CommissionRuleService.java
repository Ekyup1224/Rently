package mn.innex.stay.booking.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import mn.innex.stay.booking.domain.CommissionRule;
import mn.innex.stay.booking.domain.CommissionScope;
import mn.innex.stay.booking.repo.CommissionRuleRepository;
import mn.innex.stay.common.ApiException;
import mn.innex.stay.listing.domain.PropertyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves and edits the platform's take rate.
 *
 * <p>Resolution prefers the most specific rule: a rate set for one property type
 * beats the global rate. Rules are never edited in place — a change closes the
 * current rule and opens a new one — so the rate a past booking was priced under
 * stays recoverable.
 */
@Service
public class CommissionRuleService {

    private static final Logger log = LoggerFactory.getLogger(CommissionRuleService.class);

    private final CommissionRuleRepository repository;

    public CommissionRuleService(CommissionRuleRepository repository) {
        this.repository = repository;
    }

    /**
     * The rule to price a property booking under.
     *
     * @throws ApiException 500 when no global rule is configured — pricing cannot
     *                      be guessed, and silently charging 0% would give away
     *                      the platform's revenue
     */
    @Transactional(readOnly = true)
    public CommissionRule resolveForProperty(PropertyType propertyType, Instant at) {
        List<CommissionRule> typeSpecific =
                repository.findEffective(CommissionScope.PROPERTY_TYPE, propertyType.name(), at);
        if (!typeSpecific.isEmpty()) {
            return typeSpecific.get(0);
        }
        return repository.findEffectiveGlobal(at).orElseThrow(() -> {
            log.error("No effective GLOBAL commission rule at {} — pricing is blocked", at);
            return new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "commission_not_configured", "No commission rule is configured");
        });
    }

    /**
     * The rule to price a hotel booking under. A rate negotiated with one hotel
     * beats the global rate, which is how a chain gets different terms.
     */
    @Transactional(readOnly = true)
    public CommissionRule resolveForHotel(UUID hotelId, Instant at) {
        List<CommissionRule> hotelSpecific =
                repository.findEffective(CommissionScope.HOTEL, hotelId.toString(), at);
        if (!hotelSpecific.isEmpty()) {
            return hotelSpecific.get(0);
        }
        return repository.findEffectiveGlobal(at).orElseThrow(() -> {
            log.error("No effective GLOBAL commission rule at {} — pricing is blocked", at);
            return new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "commission_not_configured", "No commission rule is configured");
        });
    }

    @Transactional(readOnly = true)
    public List<CommissionRule> listAll() {
        return repository.findAllByOrderByEffectiveFromDesc();
    }

    /**
     * Introduces a new rate, closing the rule it supersedes at the same instant so
     * there is never a gap or an overlap.
     */
    @Transactional
    public CommissionRule replace(CommissionScope scope, String category, BigDecimal hostFeePercent,
                                  BigDecimal guestFeePercent, String note, UUID actorId) {
        if (scope == CommissionScope.GLOBAL && category != null) {
            throw ApiException.badRequest("category_not_applicable",
                    "A GLOBAL rule takes no category");
        }
        if (scope != CommissionScope.GLOBAL && (category == null || category.isBlank())) {
            throw ApiException.badRequest("category_required",
                    "A " + scope + " rule needs a category");
        }

        Instant now = Instant.now();
        for (CommissionRule superseded : repository.findEffective(scope, category, now)) {
            superseded.closeAt(now);
            repository.save(superseded);
        }

        CommissionRule replacement = new CommissionRule(
                scope, category, hostFeePercent, guestFeePercent, now, note, actorId);
        log.info("Commission rule replaced: scope={} category={} host={}% guest={}%",
                scope, category, hostFeePercent, guestFeePercent);
        return repository.save(replacement);
    }
}
