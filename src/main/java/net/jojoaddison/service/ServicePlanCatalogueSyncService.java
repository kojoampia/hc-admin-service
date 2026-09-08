package net.jojoaddison.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.jojoaddison.domain.ServicePlan;
import net.jojoaddison.repository.ServicePlanRepository;
import net.jojoaddison.service.AbofonsaContentClient.PublishedPlan;
import net.jojoaddison.service.dto.PlanCatalogueSyncDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Keeps this console's plan catalogue in step with the one Abofonsa publishes.
 *
 * <h2>What is synced, and what deliberately is not</h2>
 *
 * <p>Three fields: {@code code}, {@code name} and {@code displayOrder}. That is the decision recorded
 * against backlog item 51, and it is narrower than "mirror the catalogue" on purpose.
 *
 * <p><b>{@code monthlyPrice} is not synced and stays this service's own.</b> Abofonsa publishes
 * {@code priceAmount} as a string already formatted for the requesting locale — {@code "8,000"} —
 * and publishes no machine-readable price at all. hc-patient renders that string and says why:
 * <em>"Already formatted for the requesting locale — render it, never re-format it."</em> This
 * console cannot render it, because it multiplies: {@code ServicePlanSummaryService} computes
 * monthly revenue as {@code monthlyPrice × subscribers}. So a {@link java.math.BigDecimal} is kept
 * here and an administrator sets it. <b>Parsing {@code priceAmount} into a number is the one change
 * this class exists to prevent</b> — it is what hc-patient's comment forbids, it would break
 * silently on any locale that groups differently, and the failure mode is a wrong figure on a
 * dashboard rather than an exception anybody sees.
 *
 * <p>So item 51 is <b>not fully closed by this class</b>. Names, codes and ordering stop drifting;
 * the price is still a second copy, and it is the field the item was opened about. Say so rather
 * than describing the catalogue as reconciled.
 *
 * <p>{@code currency}, {@code featured} and {@code summary} are seeded from the published plan
 * <b>when a plan is created and never afterwards</b>. Currency is the strongest case and the rest
 * follow it: a currency belongs to the price, the price is local, and a currency that moved on its
 * own would leave a local amount labelled in a unit it was never set in. After creation all three
 * are this console's.
 *
 * <h2>How it runs, and why nothing depends on it succeeding</h2>
 *
 * <p>A background refresh into the local collection, plus {@code POST /api/service-plans/sync} for
 * an administrator who has just been told the price list changed. <b>No screen reads Abofonsa.</b>
 * The plan board, the patient directory, the CSV export and the dashboard's plan mix all read
 * {@code service_plan} exactly as they did before, so an Abofonsa outage cannot make any of them
 * fail, empty or slow — the copy simply stays as it was. That is the whole reason the decision was
 * "keep a local record" rather than "read through to the publisher": item 51 puts it as a dashboard
 * tile that cannot compute a plan mix without a third party being a worse trade than a patient
 * screen quietly omitting a price list.
 *
 * <h2>It never deletes, and that is not timidity</h2>
 *
 * <p>{@code Patient.plan} is a {@code @DBRef} to one of these documents. Deleting a plan because a
 * marketing page stopped listing it would dangle every patient holding it, silently, at whatever
 * hour the scheduler happened to run. A plan whose code is no longer published is reported in
 * {@link PlanCatalogueSyncDTO#unpublishedCodes()} and left exactly alone; withdrawing it is an
 * administrator's decision made against the directory.
 *
 * <p>For the same reason it never re-keys. The join to the published catalogue is {@code code}, a
 * field beside the id, not the id itself — the {@code _id} of a plan is whatever it has always been,
 * so no {@code @DBRef} anywhere moves when a plan is renamed from {@code Bridge Plus} to
 * {@code PAWPAW Plan}.
 */
@Service
public class ServicePlanCatalogueSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(ServicePlanCatalogueSyncService.class);

    /** {@code ServicePlan.summary} is {@code @Size(max = 240)} and {@code forWho} is free prose. */
    private static final int SUMMARY_MAX = 240;

    /** {@code ServicePlan.name} is {@code @NotNull @Size(max = 60)}. */
    private static final int NAME_MAX = 60;

    private final AbofonsaContentClient contentClient;
    private final ServicePlanRepository servicePlanRepository;

    public ServicePlanCatalogueSyncService(AbofonsaContentClient contentClient, ServicePlanRepository servicePlanRepository) {
        this.contentClient = contentClient;
        this.servicePlanRepository = servicePlanRepository;
    }

    /**
     * The scheduled refresh.
     *
     * <p>A long initial delay and a long period, both configurable. A published price list changes a
     * few times a year, so the schedule is a safety net rather than the mechanism — the mechanism an
     * administrator uses when they know something changed is the endpoint. The initial delay keeps
     * the first attempt off the startup path: this service must come up whether or not a third party
     * is answering, and {@code DevelopmentDataInitializer} is already writing the seed at that point.
     *
     * <p>Nothing here is guarded against two instances running it at once, because nothing needs to
     * be: every write is an upsert on {@code code}, and {@code config/ServicePlanIndexes} makes that
     * key unique so two first sightings of one plan cannot both insert.
     */
    @Scheduled(
        initialDelayString = "${application.abofonsa-content.initial-delay-ms:120000}",
        fixedDelayString = "${application.abofonsa-content.refresh-ms:21600000}"
    )
    public void scheduledSync() {
        sync();
    }

    /**
     * Reads the published catalogue and reconciles the local copy to it.
     *
     * <p>Safe to run at any time and safe to run twice — the same idempotent upsert either way. When
     * the publisher cannot be read it returns {@link PlanCatalogueSyncDTO#notReached(boolean)} and writes
     * nothing at all, which is a normal outcome and not an error: see the class comment.
     */
    public PlanCatalogueSyncDTO sync() {
        Optional<List<PublishedPlan>> published = contentClient.plans();
        if (published.isEmpty()) {
            // Already logged by the client, with the reason. Nothing to add and nothing to write —
            // except which of the two reasons it was, which the caller cannot recover otherwise. A
            // deployment with the client switched off has not learned that Abofonsa is down.
            return PlanCatalogueSyncDTO.notReached(contentClient.isEnabled());
        }
        List<PublishedPlan> plans = published.orElseThrow();

        int created = 0;
        int updated = 0;
        int unchanged = 0;
        List<String> publishedCodes = new ArrayList<>(plans.size());
        List<String> refused = new ArrayList<>();

        for (PublishedPlan plan : plans) {
            publishedCodes.add(plan.code());
            if (plan.name() == null || plan.name().isBlank() || plan.name().length() > NAME_MAX) {
                // Skipped rather than truncated or defaulted. A name is what an administrator reads
                // on the plan board and what the CSV export writes, so a shortened or invented one
                // is the identifier-as-a-name defect this repo has already had twice.
                LOG.warn("Abofonsa published {} with a name this service cannot store; leaving the local copy alone", plan.code());
                refused.add(plan.code());
                continue;
            }
            Optional<ServicePlan> existing = servicePlanRepository.findOneByCode(plan.code());
            if (existing.isEmpty()) {
                servicePlanRepository.save(create(plan));
                created++;
                LOG.info(
                    "Plan catalogue learned {} ({}) from Abofonsa. It has no monthlyPrice until an administrator sets one, " +
                    "so it earns nothing on the dashboard — the published price is a display string this service does not parse.",
                    plan.code(),
                    plan.name()
                );
                continue;
            }
            ServicePlan stored = existing.orElseThrow();
            if (apply(plan, stored)) {
                servicePlanRepository.save(stored);
                updated++;
                LOG.info("Plan catalogue updated {} to the published name and ordering", plan.code());
            } else {
                unchanged++;
            }
        }

        // Reported against every plan this service holds, not only the ones just touched — a plan
        // whose code was never published at all is exactly as unresolvable by item 48's inbound plan
        // event as one that has been withdrawn.
        List<ServicePlan> all = servicePlanRepository.findAll();
        List<String> unpublished = all
            .stream()
            .map(ServicePlan::getCode)
            .filter(Objects::nonNull)
            .filter(code -> !publishedCodes.contains(code))
            .sorted()
            .toList();
        List<String> unpriced = all
            .stream()
            .filter(plan -> plan.getMonthlyPrice() == null)
            // Falls back to the id for a plan with no code — one an administrator created before
            // the catalogue was reconciled. Naming it by something is what makes it findable; the
            // alternative is a count that says a plan is unpriced and not which.
            .map(plan -> plan.getCode() == null ? plan.getId() : plan.getCode())
            .sorted()
            .toList();

        LOG.info(
            "Plan catalogue synced with Abofonsa: {} published, {} created, {} updated, {} unchanged, {} refused, {} held " +
            "here but not published (left alone — patients reference plans), {} with no local price",
            plans.size(),
            created,
            updated,
            unchanged,
            refused.size(),
            unpublished.size(),
            unpriced.size()
        );
        return new PlanCatalogueSyncDTO(true, true, plans.size(), created, updated, unchanged, unpublished, unpriced, List.copyOf(refused));
    }

    /**
     * A plan this service has never held.
     *
     * <p>{@code currency}, {@code featured} and {@code summary} are taken from the publisher once, at
     * creation. {@code monthlyPrice} is left null deliberately — see {@code ServicePlan.monthlyPrice}
     * for why null rather than zero, and the class comment for why it is never derived from
     * {@code priceAmount}.
     */
    private static ServicePlan create(PublishedPlan plan) {
        return new ServicePlan()
            .code(plan.code())
            .name(plan.name())
            .displayOrder(plan.displayOrder())
            .currency(plan.priceCurrency())
            .featured(plan.featured())
            .summary(truncate(plan.forWho()));
    }

    /**
     * Moves the three synced fields onto a plan already held, and answers whether anything changed.
     *
     * <p>The answer is what keeps a quiet run quiet: without it every scheduled pass would rewrite
     * every plan, and {@code AuditLogCallback} writes an {@code AuditLog} row for every save — six
     * hourly rows a day per plan, all of them saying nothing happened.
     */
    private static boolean apply(PublishedPlan plan, ServicePlan stored) {
        boolean changed = false;
        if (plan.name() != null && !plan.name().equals(stored.getName())) {
            stored.setName(plan.name());
            changed = true;
        }
        if (!Integer.valueOf(plan.displayOrder()).equals(stored.getDisplayOrder())) {
            stored.setDisplayOrder(plan.displayOrder());
            changed = true;
        }
        return changed;
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= SUMMARY_MAX) {
            return value;
        }
        return value.substring(0, SUMMARY_MAX);
    }
}
