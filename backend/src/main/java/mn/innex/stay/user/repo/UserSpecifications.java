package mn.innex.stay.user.repo;

import mn.innex.stay.user.domain.KycStatus;
import mn.innex.stay.user.domain.Role;
import mn.innex.stay.user.domain.User;
import mn.innex.stay.user.domain.UserStatus;
import org.springframework.data.jpa.domain.Specification;

/** Filters behind {@code GET /api/v1/admin/users}. Each is a no-op when its argument is null. */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> statusIs(UserStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<User> kycStatusIs(KycStatus kycStatus) {
        return kycStatus == null ? null : (root, query, cb) -> cb.equal(root.get("kycStatus"), kycStatus);
    }

    public static Specification<User> hasRole(Role role) {
        return role == null ? null : (root, query, cb) -> {
            // A join would multiply rows for multi-role users, so match by subquery.
            var subquery = query.subquery(Long.class);
            var grant = subquery.from(mn.innex.stay.user.domain.UserRole.class);
            subquery.select(cb.literal(1L))
                    .where(cb.and(
                            cb.equal(grant.get("user").get("id"), root.get("id")),
                            cb.equal(grant.get("role"), role)));
            return cb.exists(subquery);
        };
    }

    /** Case-insensitive contains over phone, email and full name. */
    public static Specification<User> matches(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String pattern = "%" + term.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("phone")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("email"), "")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("fullName"), "")), pattern));
    }

    @SafeVarargs
    public static Specification<User> allOf(Specification<User>... specs) {
        Specification<User> combined = (root, query, cb) -> cb.conjunction();
        for (Specification<User> spec : specs) {
            if (spec != null) {
                combined = combined.and(spec);
            }
        }
        return combined;
    }
}
