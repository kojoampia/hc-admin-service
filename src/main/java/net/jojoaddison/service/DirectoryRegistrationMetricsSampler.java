package net.jojoaddison.service;

import net.jojoaddison.management.DirectoryRegistrationMetersService;
import net.jojoaddison.management.DirectoryRegistrationMetersService.SourcePopulation;
import net.jojoaddison.service.dto.RegistrationTotalsDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Aggregates {@code directory_link} on a timer and hands the result to
 * {@link DirectoryRegistrationMetersService}.
 *
 * <h2>Why this is a sampler and not a gauge closure</h2>
 *
 * <p>The obvious shape — {@code Gauge.builder(name, () -> directoryRegistrationService.totals()…)} —
 * runs the aggregation on whichever thread Micrometer is exporting on, so every export pays for a
 * full {@code $group} over the collection and an exporter that stalls stalls holding a database
 * cursor. Sampling on a scheduled thread and letting the gauges read plain {@code AtomicLong}s keeps
 * the query off the export path entirely.
 *
 * <h2>It reads the same figure the endpoint serves, through the same class</h2>
 *
 * <p>{@link DirectoryRegistrationService#totals()} and nothing else — no second aggregation, no
 * second bucketing rule. The three-bucket split is the thing most likely to be got wrong (an absent
 * {@code activated} read as {@code false} is plausible, silent and wrong), and it is argued and
 * tested in one place. A dashboard that disagrees with the endpoint it sits beside would be the
 * defect this service has had twice.
 *
 * <h2>Why it lives in {@code service} and the meters live in {@code management}</h2>
 *
 * <p>{@code TechnicalStructureTest}'s layer rule lets nothing outside the declared layers reach
 * {@code ..service..}, and {@code ..management..} is not a declared layer — so a metrics class that
 * called this aggregate directly would fail the build. Hence {@code SourcePopulation}, which carries
 * the numbers across the seam as a {@code String} and three {@code long}s.
 *
 * <h2>Cost</h2>
 *
 * <p>One {@code $group} over {@code directory_link} every five minutes. Longer than the gateway's
 * minute because this population moves on inbound Kafka frames from two other products rather than on
 * an administrator's action, and because the aggregation touches every document in the collection
 * rather than counting an indexed field.
 */
@Component
public class DirectoryRegistrationMetricsSampler {

    private static final Logger LOG = LoggerFactory.getLogger(DirectoryRegistrationMetricsSampler.class);

    /** Long enough for {@code DevelopmentDataInitializer} to have finished seeding on a dev or test stack. */
    static final long INITIAL_DELAY_MS = 30_000;

    static final long INTERVAL_MS = 300_000;

    private final DirectoryRegistrationService directoryRegistrationService;

    private final DirectoryRegistrationMetersService metersService;

    public DirectoryRegistrationMetricsSampler(
        DirectoryRegistrationService directoryRegistrationService,
        DirectoryRegistrationMetersService metersService
    ) {
        this.directoryRegistrationService = directoryRegistrationService;
        this.metersService = metersService;
    }

    /**
     * The scheduled tick.
     *
     * <p>The two intervals are constants rather than configuration properties deliberately. They are
     * not a knob an operator turns after an incident, and a key here would mean writing its default in
     * two places — once in {@code ApplicationProperties} and once in the annotation, which cannot take
     * an injected value at all. That is the shape item 58 closed, and
     * {@code ApplicationPropertiesSingleSourceTest} is what keeps it from spreading.
     */
    @Scheduled(initialDelay = INITIAL_DELAY_MS, fixedDelay = INTERVAL_MS)
    public void sample() {
        sampleOnce();
    }

    /**
     * One reading of the directory population, package-private so a test can drive it without waiting
     * for a scheduler.
     *
     * <p>A failure updates nothing, which leaves the previous reading standing and the
     * {@code sampled.timestamp} gauge falling behind — that pairing is what makes a dead sampler
     * visible instead of making it look like a flat number. Zeroing the gauges instead would render
     * an unreachable database as "every registration vanished", an outage-shaped figure produced by a
     * monitoring bug.
     *
     * <p>The warning names the exception's class and not the exception. There is no subject on this
     * path to leak today — the aggregation projects three counts and never a document — but item 43's
     * rule is about what an exception message <em>can</em> embed rather than about what this
     * particular query happens to select.
     */
    void sampleOnce() {
        try {
            RegistrationTotalsDTO totals = directoryRegistrationService.totals();
            metersService.recordPopulation(
                totals
                    .bySource()
                    .stream()
                    .map(source ->
                        new SourcePopulation(source.source().name(), source.activated(), source.notActivated(), source.notReported())
                    )
                    .toList()
            );
        } catch (RuntimeException unreadable) {
            LOG.warn("Could not aggregate the directory population for the registration gauges ({}).", unreadable.getClass().getName());
        }
    }
}
