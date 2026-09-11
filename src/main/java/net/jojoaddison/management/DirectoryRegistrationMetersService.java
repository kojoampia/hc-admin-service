package net.jojoaddison.management;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/**
 * How many accounts the sibling stacks have told this service about, by source and activation state —
 * the estate-wide registration figure as a time series, for the Grafana dashboard backlog item 80
 * added.
 *
 * <h2>⚠ The name, which had no precedent and so had to be argued rather than copied</h2>
 *
 * <p>Two gateways in this estate publish an account population and both were considered as the name
 * to adopt: hc-patient's {@code account.registrations{state}} and hc-professional's
 * {@code security.registration.accounts{state}}. <b>Neither is right for this figure, and adopting
 * either would be wrong in a way no panel could see.</b> Both of those count the rows in <em>that
 * application's own user store</em> — accounts it can authenticate — over two states. This counts
 * {@code directory_link}: subjects two <em>other</em> products told this service about over Kafka,
 * for which this service holds no credentials and frequently no record at all. A panel reading
 * {@code sum by (state) (account_registrations_accounts)} across the estate would silently add the
 * two populations together and double-count every hc-patient account, which is exactly the
 * tile-disagreeing-with-the-rows-underneath-it defect this dashboard has already had twice.
 *
 * <p><b>And the state spaces are different sizes.</b> {@code User.activated} is a primitive
 * {@code boolean} on both gateways, so two states is the whole truth there.
 * {@code DirectoryLink.activated} is a boxed {@code Boolean} and {@link #STATE_NOT_REPORTED} is a
 * real, common and permanent third state — a clinician known only from an {@code onboarding.state}
 * frame has never been told about either way. Putting a third value into a metric name whose two
 * existing publishers cannot produce it means every cross-product legend written against that name
 * is incomplete for one job and nobody would find out.
 *
 * <p>So the family is its own: <b>{@code directory.registrations}</b>, published as
 * {@code directory_registrations_accounts{state,source}}.
 *
 * <ul>
 *   <li><b>{@code directory}</b> because that is what this repository already calls the thing being
 *       counted — the collection is {@code directory_link}, the endpoint is
 *       {@code /api/directory-links/registrations}, the writer is {@code DirectoryProjectionService}.
 *       A metric named after the store it is read from can be traced back to it with one grep.
 *       {@code estate.registrations} was the other candidate and reads better on a dashboard, but it
 *       names no artifact in any repository, so nothing leads a reader from the series to the code.</li>
 *   <li><b>{@code registrations}</b> rather than {@code links}, following
 *       {@code RegistrationTotalsDTO}: the row is a link, the figure is about people who registered
 *       on a sibling stack, and the endpoint already chose that noun. One noun per figure per
 *       product.</li>
 *   <li><b>base unit {@code accounts}</b>, which is what makes the exported name end in
 *       {@code _accounts} exactly as the two sibling families do. That is the one thing deliberately
 *       held in common: a reader who knows one of these names can guess the shape of the others.</li>
 * </ul>
 *
 * <h2>The {@code source} dimension, and why it is safe to carry</h2>
 *
 * <p>Almost every {@code not-reported} row is an {@code HC_PROFESSIONAL} one, and split by source
 * that is obvious instead of mysterious — {@code RegistrationTotalsDTO} makes the same argument for
 * the same reason. The label space is bounded by {@code DirectorySource}, an enum with two constants,
 * so this is six series and cannot grow on its own. <b>The values are the enum's own names</b>
 * ({@code HC_PATIENT}, {@code HC_PROFESSIONAL}) rather than a prettier kebab-case rendering, because
 * those are the strings stored in {@code directory_link.source} and served by the endpoint: a label
 * that matches the data is greppable, and a second spelling would be a third copy to drift.
 *
 * <h2>⚠ No subject, ever</h2>
 *
 * <p>These are counts, and nothing here is per-subject. {@code DirectoryLinkResource}'s javadoc
 * argues at length about which surfaces may carry an address and which may not; a Prometheus label is
 * firmly on the "may not" side and further out than a log line, because it is a <em>series key</em> —
 * an identifier in one is both an unauthenticated disclosure and unbounded cardinality in a store
 * five other products read. The only dimensions are two literal enum names and three literal states.
 *
 * <h2>Absent rather than zero, and a timestamp so stale is not read as healthy</h2>
 *
 * <p>Nothing is registered until the collection has actually been aggregated once, so a service that
 * cannot reach MongoDB reports <em>no series</em> rather than a confident zero — "nobody has
 * registered" and "nobody has looked" are different facts and must not arrive as the same number.
 * {@link #SAMPLED_AT_METER_NAME} carries when the last successful read happened, because a gauge that
 * stops updating otherwise reads as a steady value rather than as a sampler that died. Both rules are
 * hc-patient's, copied with their reasoning rather than their code.
 *
 * @see net.jojoaddison.service.DirectoryRegistrationMetricsSampler the scheduled read that feeds this
 */
@Service
public class DirectoryRegistrationMetersService {

    public static final String REGISTRATIONS_METER_NAME = "directory.registrations";
    public static final String REGISTRATIONS_METER_DESCRIPTION =
        "Accounts the sibling stacks have announced to this service, by source and activation state.";
    public static final String REGISTRATIONS_METER_BASE_UNIT = "accounts";
    public static final String REGISTRATIONS_METER_STATE_DIMENSION = "state";
    public static final String REGISTRATIONS_METER_SOURCE_DIMENSION = "source";

    /** An event has said the account can sign in. */
    public static final String STATE_ACTIVATED = "activated";

    /** An event has said it cannot. */
    public static final String STATE_NOT_ACTIVATED = "not-activated";

    /**
     * <b>No event has said either way.</b> Not a synonym for {@link #STATE_NOT_ACTIVATED}, and the
     * reason this family cannot share a name with the gateways' two-state one — see the class javadoc
     * and {@code RegistrationTotalsDTO}, which records the previous version of exactly this mistake.
     */
    public static final String STATE_NOT_REPORTED = "not-reported";

    /** Epoch seconds of the last successful aggregation, so a sampler that stopped is distinguishable from a flat number. */
    public static final String SAMPLED_AT_METER_NAME = "directory.registrations.sampled.timestamp";
    public static final String SAMPLED_AT_METER_DESCRIPTION = "When the directory population was last aggregated.";
    public static final String SAMPLED_AT_METER_BASE_UNIT = "seconds";

    private final MeterRegistry registry;

    /** One holder per source, created and registered the first time that source is seen in a reading. */
    private final Map<String, SourceGauges> bySource = new ConcurrentHashMap<>();

    private final AtomicLong sampledAt = new AtomicLong(0);

    private final AtomicBoolean sampledAtRegistered = new AtomicBoolean(false);

    public DirectoryRegistrationMetersService(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Publishes a fresh reading of the directory population.
     *
     * <p>Takes {@link SourcePopulation} rather than the service's own DTO on purpose: the ArchUnit
     * layer rule in {@code TechnicalStructureTest} lets nothing outside the declared layers reach
     * {@code ..service..} or {@code ..domain..}, and this class is in neither. The consequence is
     * that the source arrives as a {@code String}, which is also what the label is.
     *
     * @param populations one entry per source, every source the aggregate knows about, including any
     *     that are entirely zero — a source that vanishes from a reading is indistinguishable on a
     *     dashboard from a source that is quiet, and those need opposite responses.
     */
    public void recordPopulation(List<SourcePopulation> populations) {
        for (SourcePopulation population : populations) {
            SourceGauges gauges = bySource.computeIfAbsent(population.source(), this::registerGaugesFor);
            gauges.activated().set(population.activated());
            gauges.notActivated().set(population.notActivated());
            gauges.notReported().set(population.notReported());
        }
        sampledAt.set(Instant.now().getEpochSecond());
        registerSampledAtOnce();
    }

    /** The last reading for one source and state, for tests. Zero if that source has never been sampled. */
    public long countFor(String source, String state) {
        SourceGauges gauges = bySource.get(source);
        if (gauges == null) {
            return 0;
        }
        return switch (state) {
            case STATE_ACTIVATED -> gauges.activated().get();
            case STATE_NOT_ACTIVATED -> gauges.notActivated().get();
            case STATE_NOT_REPORTED -> gauges.notReported().get();
            default -> 0;
        };
    }

    private SourceGauges registerGaugesFor(String source) {
        SourceGauges gauges = new SourceGauges(new AtomicLong(0), new AtomicLong(0), new AtomicLong(0));
        gaugeBuilder(source, STATE_ACTIVATED, gauges.activated()).register(registry);
        gaugeBuilder(source, STATE_NOT_ACTIVATED, gauges.notActivated()).register(registry);
        gaugeBuilder(source, STATE_NOT_REPORTED, gauges.notReported()).register(registry);
        return gauges;
    }

    private void registerSampledAtOnce() {
        if (!sampledAtRegistered.compareAndSet(false, true)) {
            return;
        }
        Gauge.builder(SAMPLED_AT_METER_NAME, sampledAt, AtomicLong::doubleValue)
            .baseUnit(SAMPLED_AT_METER_BASE_UNIT)
            .description(SAMPLED_AT_METER_DESCRIPTION)
            .register(registry);
    }

    private Gauge.Builder<AtomicLong> gaugeBuilder(String source, String state, AtomicLong value) {
        return Gauge.builder(REGISTRATIONS_METER_NAME, value, AtomicLong::doubleValue)
            .baseUnit(REGISTRATIONS_METER_BASE_UNIT)
            .description(REGISTRATIONS_METER_DESCRIPTION)
            .tag(REGISTRATIONS_METER_SOURCE_DIMENSION, source)
            .tag(REGISTRATIONS_METER_STATE_DIMENSION, state);
    }

    /**
     * One source's three counts, as the sampler hands them over.
     *
     * @param source the label value — {@code DirectorySource}'s own name.
     * @param activated an event has said the account can sign in.
     * @param notActivated an event has said it cannot.
     * @param notReported no event has said either way.
     */
    public record SourcePopulation(String source, long activated, long notActivated, long notReported) {}

    /** The three live values for one source. Held rather than re-read so nothing queries MongoDB on the export path. */
    private record SourceGauges(AtomicLong activated, AtomicLong notActivated, AtomicLong notReported) {}
}
