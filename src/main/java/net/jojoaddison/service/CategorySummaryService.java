package net.jojoaddison.service;

import java.util.List;
import net.jojoaddison.domain.Category;
import net.jojoaddison.domain.ServiceActivity;
import net.jojoaddison.service.dto.CategorySummaryDTO;
import net.jojoaddison.service.dto.CategorySummaryDTO.CategoryActivityCount;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * Activity counts per catalogue category.
 *
 * <p>See {@link CategorySummaryDTO} for why these are counted on the activity side rather than read
 * off {@code Category.activities}.
 */
@Service
public class CategorySummaryService {

    private final MongoTemplate mongoTemplate;

    public CategorySummaryService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public CategorySummaryDTO summary() {
        List<CategoryActivityCount> counts = mongoTemplate
            .findAll(Category.class)
            .stream()
            .map(category -> new CategoryActivityCount(category.getId(), activities(category.getId()), live(category.getId())))
            .toList();
        return new CategorySummaryDTO(counts);
    }

    /**
     * The property path is {@code category.id}, not {@code category.$id} — see
     * {@code ServiceActivityResource#getAllServiceActivities} for why the literal field name matches
     * nothing. Here the consequence would be every card reading zero, which looks like an empty
     * catalogue rather than a broken query.
     */
    private long activities(String categoryId) {
        return mongoTemplate.count(new Query(inCategory(categoryId)), ServiceActivity.class);
    }

    /**
     * {@code is(true)} and not {@code ne(false)}, which is the opposite of the archived convention
     * elsewhere and is deliberate. An activity with no {@code published} field recorded has not been
     * put live — treating an absent flag as live would publish, on a screen whose entire job is
     * controlling what is live, whatever happened to be written before the field existed.
     */
    private long live(String categoryId) {
        return mongoTemplate.count(new Query(inCategory(categoryId).and("published").is(true)), ServiceActivity.class);
    }

    /**
     * A method rather than a shared constant: {@link Criteria} is mutable and {@code .and(…)} appends
     * to the receiver, so a static would carry the previous category's clause into the next call and
     * throw on the second use. With six categories on screen that means the first card is right and
     * the page 500s.
     */
    private static Criteria inCategory(String categoryId) {
        return Criteria.where("category.id").is(categoryId);
    }
}
