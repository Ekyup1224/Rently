package mn.innex.stay.common;

/** Email addresses are stored lower-cased and trimmed so the unique index is meaningful. */
public final class Emails {

    private Emails() {
    }

    public static String normalize(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim().toLowerCase();
    }
}
