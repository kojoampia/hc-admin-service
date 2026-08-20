package net.jojoaddison.service.dto;

import java.io.Serializable;
import java.util.List;

/**
 * How many activities sit under each catalogue category, for the cards on the catalogue screen
 * ("Clinical visits · 4").
 *
 * <p>The count cannot come from the category documents. {@code Category.activities} is a
 * {@code @DBRef} set, so a page of categories carries references and not a total, and resolving them
 * to count them would fetch every activity in the catalogue to display six numbers. Counting on the
 * activity side is one query per category over an indexed field.
 *
 * <p>Counts are returned for <strong>every</strong> category, not only the page of cards being
 * drawn. The client joins by id, so a second page of categories needs no second summary — the same
 * shape {@link VendorSummaryDTO} uses, for the same reason: figures computed over the whole
 * collection do not change with the slice on screen.
 *
 * @param categories one entry per category, in the order {@code GET /api/categories} returns them
 */
public record CategorySummaryDTO(List<CategoryActivityCount> categories) implements Serializable {
    /**
     * One category's activity tally.
     *
     * <p>{@code live} is carried beside {@code activities} because the screen's whole point is the
     * live/withdrawn state — an activity that exists and is withdrawn is not one a patient can be
     * booked onto, so a card reading "Equipment · 2" when only one is live overstates the catalogue.
     * The seed has exactly that case on purpose.
     *
     * <p>Both are genuine zeroes when a category has no activities: the category exists and holds
     * nothing, which is a fact the card should state rather than an absence it should hide behind an
     * em dash.
     *
     * @param categoryId the category these counts belong to
     * @param activities activities referencing this category, whatever their published state
     * @param live the subset whose {@code published} flag is true
     */
    public record CategoryActivityCount(String categoryId, long activities, long live) implements Serializable {}
}
