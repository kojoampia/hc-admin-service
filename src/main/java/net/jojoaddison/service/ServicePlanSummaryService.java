package net.jojoaddison.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import net.jojoaddison.domain.Patient;
import net.jojoaddison.domain.ServicePlan;
import net.jojoaddison.service.dto.ServicePlanSummaryDTO;
import net.jojoaddison.service.dto.ServicePlanSummaryDTO.PlanMixRow;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * The plan mix under the console's plan board, computed over the whole patient directory.
 *
 * <p>See {@link ServicePlanSummaryDTO} for why the share cannot be computed by the client, and why
 * subscribers are counted from {@code Patient.plan} rather than read off
 * {@code ServicePlan.subscriberCount}.
 */
@Service
public class ServicePlanSummaryService {

    /** One decimal place, which is what the table prints. See {@link #shares}. */
    private static final int SHARE_SCALE = 1;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * Archived patients are out of every figure.
     *
     * <p>A method rather than a constant for the reason {@code VendorSummaryService} documents at
     * length: {@link Criteria} is mutable, {@code .and(…)} appends to the receiver and returns it,
     * so a shared static would accumulate a {@code plan.id} clause on its first use and throw on its
     * second. Here that would mean the first plan counted correctly and the next one 500ing — a
     * failure that only appears once there are two plans.
     */
    private static Criteria notArchived() {
        return Criteria.where("is_archived").ne(true);
    }

    private final MongoTemplate mongoTemplate;

    public ServicePlanSummaryService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public ServicePlanSummaryDTO summary() {
        List<ServicePlan> plans = mongoTemplate.findAll(ServicePlan.class).stream().sorted(byTier()).toList();

        List<Long> counts = plans.stream().map(plan -> subscribers(plan.getId())).toList();
        long total = counts.stream().mapToLong(Long::longValue).sum();
        List<BigDecimal> shares = shares(counts, total);

        List<PlanMixRow> mix = new ArrayList<>(plans.size());
        for (int i = 0; i < plans.size(); i++) {
            ServicePlan plan = plans.get(i);
            mix.add(
                new PlanMixRow(
                    plan.getId(),
                    plan.getName(),
                    plan.getMonthlyPrice(),
                    plan.getCurrency(),
                    counts.get(i),
                    shares.get(i),
                    revenue(plan.getMonthlyPrice(), counts.get(i))
                )
            );
        }
        return new ServicePlanSummaryDTO(total, List.copyOf(mix));
    }

    /**
     * Card order: {@code ESSENTIAL}, {@code PLUS}, {@code FAMILY}, which is both the enum's order and
     * ascending price, and is the order the design draws the three cards in.
     *
     * <p>A plan with no tier sorts last rather than throwing. {@code tier} is {@code @NotNull} on the
     * domain, but this is a read path over whatever is stored, and a summary that 500s because one
     * document predates a constraint is worse than one that puts it at the end.
     */
    private static Comparator<ServicePlan> byTier() {
        return Comparator.comparing(plan -> plan.getTier() == null ? Integer.MAX_VALUE : plan.getTier().ordinal());
    }

    /**
     * Non-archived patients holding this plan.
     *
     * <p>The property path is {@code plan.id}, not {@code plan.$id}. A DBRef is stored as
     * {@code { $ref, $id }}, so {@code $id} is the right <em>field</em> — but writing it literally
     * bypasses Spring Data's query mapper, which is also what converts the id to the type actually
     * stored. Written that way it matches nothing, and every plan would report zero subscribers,
     * which is indistinguishable from a directory nobody has subscribed in. Given the property path,
     * the mapper rewrites the field and converts the value with it. {@code ShiftAssignmentResource}
     * carries the same note for the same reason.
     */
    private long subscribers(String planId) {
        return mongoTemplate.count(new Query(notArchived().and("plan.id").is(planId)), Patient.class);
    }

    /**
     * Percentages that sum to exactly 100, by largest remainder.
     *
     * <p>Rounding each share independently does not add up: three plans at a third each print 33.3
     * three times and the column totals 99.9, and a reader who notices is right to distrust the
     * whole table. The remainder method hands the shortfall to the rows with the largest truncated
     * fractions, so the printed figures total 100 exactly and each is within one unit of the last
     * place of its true value — which is the best available, because a set of one-decimal figures
     * summing to 100 does not always exist otherwise.
     *
     * <p>Every share is null when nobody holds a plan: see {@link PlanMixRow}. Note that this is not
     * the same as returning zeroes, and the console renders the two differently on purpose.
     */
    private static List<BigDecimal> shares(List<Long> counts, long total) {
        if (total == 0) {
            return counts.stream().<BigDecimal>map(count -> null).toList();
        }
        BigDecimal scale = BigDecimal.TEN.pow(SHARE_SCALE);
        BigDecimal totalValue = BigDecimal.valueOf(total);

        // Exact percentage scaled to whole units of the last decimal place, then split into the part
        // that is certainly ours and the remainder that decides who gets the leftovers.
        List<BigDecimal> exact = counts
            .stream()
            .map(count -> BigDecimal.valueOf(count).multiply(HUNDRED).multiply(scale).divide(totalValue, 10, RoundingMode.HALF_UP))
            .toList();
        List<BigDecimal> floor = exact.stream().map(value -> value.setScale(0, RoundingMode.FLOOR)).toList();

        long leftover = scale.multiply(HUNDRED).longValueExact() - floor.stream().mapToLong(BigDecimal::longValueExact).sum();

        // The rows with the largest discarded fraction have the strongest claim on the leftover units.
        // There are always fewer leftovers than rows — the floors give away under one unit each — so
        // this walks the order once and no row is awarded twice.
        List<Integer> order = IntStream
            .range(0, counts.size())
            .boxed()
            .sorted(Comparator.comparing((Integer i) -> exact.get(i).subtract(floor.get(i))).reversed())
            .toList();

        BigDecimal[] result = floor.toArray(new BigDecimal[0]);
        for (int i = 0; i < leftover; i++) {
            int target = order.get(i);
            result[target] = result[target].add(BigDecimal.ONE);
        }
        return Arrays.stream(result).map(value -> value.movePointLeft(SHARE_SCALE)).toList();
    }

    /**
     * {@code monthlyPrice × subscribers}, and zero when the plan carries no price.
     *
     * <p>Zero rather than null: a plan nobody holds earns nothing, which is a fact. A plan with no
     * price recorded is a gap, but it is a gap in the plan and shows there — inventing a revenue
     * figure from a missing price is what would need suppressing, and multiplying by zero is not
     * that.
     */
    private static BigDecimal revenue(BigDecimal monthlyPrice, long subscribers) {
        if (monthlyPrice == null) {
            return BigDecimal.ZERO;
        }
        return monthlyPrice.multiply(BigDecimal.valueOf(subscribers));
    }
}
