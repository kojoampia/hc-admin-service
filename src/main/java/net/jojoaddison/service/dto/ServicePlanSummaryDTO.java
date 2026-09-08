package net.jojoaddison.service.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * The plan mix beneath the console's plan board — one row per plan, plus the total they share out.
 *
 * <p>{@code share} is the reason this endpoint exists. It is a proportion of the whole book of
 * subscribers, and <strong>no page of plans can produce it</strong>: a client dividing by the plans
 * it happens to be showing would print percentages that sum to 100 across a subset and mean nothing.
 * That is the same failure {@link VendorSummaryDTO} was written to avoid, reached from a different
 * direction. {@code monthlyRevenue} is arithmetic the client could do, and is here so the row
 * arrives whole rather than half-computed.
 *
 * <p><strong>Subscribers are counted from {@code Patient.plan}.</strong> There used to be a second
 * source — a denormalised {@code ServicePlan.subscriberCount} — and the two disagreed: the stored
 * counter read 41/52/23 in the {@code test} seed, 116 subscribers against a directory of twelve
 * patients, while the references resolve to 4/5/3 and reconcile exactly to the patients an operator
 * can open. A counter that nothing maintains is a fabricated figure with a database column to sit
 * in, so the field was deleted on 2026-08-24 rather than left unread. <strong>Do not add it
 * back</strong>: the mix has to be derived live because it must reconcile to the directory, so a
 * maintained counter would be kept correct for no reader while adding a write path on every plan
 * change, archive and bulk load.
 *
 * <p>Archived patients are excluded, because the directory that lists them excludes them. A share
 * computed over rows the console will not show would disagree with the console.
 *
 * @param totalSubscribers non-archived patients holding any plan; the denominator of every share
 * @param mix one row per plan, ordered by {@code displayOrder} — the order Abofonsa publishes the
 *     tiers in, and so the order the plan board draws its cards in. It said "ordered by tier" until
 *     2026-09-08, when the {@code PlanTier} ordinal it meant was deleted.
 */
public record ServicePlanSummaryDTO(long totalSubscribers, List<PlanMixRow> mix) implements Serializable {
    /**
     * One line of the plan mix table: {@code PLAN · MONTHLY PRICE · SUBSCRIBERS · SHARE · MONTHLY
     * REVENUE}.
     *
     * <p>{@code share} is <strong>null rather than zero</strong> when nobody holds any plan. A share
     * of an empty directory is undefined, not nought, and the console renders null as an em dash —
     * the distinction that lets a reader tell "no answer" from "answered zero", which
     * {@code deploy/TODO.md} §5 records as the cheapest confirmation available that an endpoint
     * replied at all. Getting this wrong would make an empty deployment claim every plan holds 0%
     * of the market, which is a statement, not an absence.
     *
     * <p>{@code monthlyRevenue} is genuinely zero in that case, and is typed to say so: no
     * subscribers times any <em>known</em> price is nought earned, which is a fact rather than a gap.
     *
     * <p><strong>It is null when the plan has no price</strong>, which is a different absence and
     * arrived with a nullable {@code ServicePlan.monthlyPrice} on 2026-09-08. A plan the catalogue
     * sync learned from Abofonsa is unpriced until an administrator sets a figure, and it can have
     * subscribers meanwhile — so a zero there would say those subscribers pay nothing, where the
     * truth is that nobody has recorded what they pay. The console renders it as an em dash and
     * leaves the row out of the column total rather than adding a zero in.
     *
     * @param planId the plan's id, so the client can link the row to its card without matching names
     * @param name the plan's display name
     * @param monthlyPrice the plan's own price, carried so the table need not re-read the plan; null
     *     on a plan nobody has priced yet
     * @param currency the plan's currency code, carried rather than assumed
     * @param subscribers non-archived patients referencing this plan
     * @param share percentage of {@code totalSubscribers}, one decimal place; null when there are none
     * @param monthlyRevenue {@code monthlyPrice × subscribers}; zero when the price is known and
     *     nobody holds the plan, and null when the plan has no price — never zero for the second
     */
    public record PlanMixRow(
        String planId,
        String name,
        BigDecimal monthlyPrice,
        String currency,
        long subscribers,
        BigDecimal share,
        BigDecimal monthlyRevenue
    )
        implements Serializable {}
}
