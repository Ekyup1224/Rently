package mn.innex.stay.user.domain;

/**
 * Platform roles. A single account may hold {@link #CLIENT} plus one host-side
 * role, so roles are stored as grants rather than a single column on the user.
 */
public enum Role {

    /** Guest: search, book, pay, message, review, manage own trips. */
    CLIENT(false),

    /** Individual landlord: own properties, own calendar, own payouts. */
    HOUSE_OWNER(false),

    /** Business account: hotels, room types, inventory, staff, reports. */
    HOTEL_MANAGER(true),

    /** Scoped subset of the manager's rights, e.g. check-in/out but no rate changes. */
    HOTEL_STAFF(true),

    /** Platform operator: approvals, commissions, payments, disputes, moderation. */
    SUPER_ADMIN(false);

    private final boolean organizationScoped;

    Role(boolean organizationScoped) {
        this.organizationScoped = organizationScoped;
    }

    /** True when a grant of this role is meaningless without an organization. */
    public boolean isOrganizationScoped() {
        return organizationScoped;
    }

    /** Spring Security authority name, e.g. {@code ROLE_HOTEL_MANAGER}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
