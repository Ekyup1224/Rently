package mn.innex.stay.review.service;

import org.springframework.stereotype.Component;

/** Connects the scheduler's port to the review service. */
@Component
public class ReviewPublishAdapter implements ReviewPublishPort {

    private final ReviewService reviews;

    public ReviewPublishAdapter(ReviewService reviews) {
        this.reviews = reviews;
    }

    @Override
    public int publishExpired() {
        return reviews.publishExpired();
    }
}
