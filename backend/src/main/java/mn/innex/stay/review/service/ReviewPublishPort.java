package mn.innex.stay.review.service;

/**
 * How the booking scheduler drives review publication without the booking module
 * depending on reviews. Same shape as the payout sweep next to it: one scheduler
 * owns the timers, the modules own the work.
 */
public interface ReviewPublishPort {

    /** @return how many reviews were published */
    int publishExpired();
}
