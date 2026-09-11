package net.jojoaddison.management;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import net.jojoaddison.management.DirectoryRegistrationMetersService.SourcePopulation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The directory-population gauges.
 *
 * <p>Two properties carry the rest of the file. <b>The third state is a state</b> —
 * {@code not-reported} is published as its own series and is never folded into
 * {@code not-activated}, which is the mistake {@code RegistrationTotalsDTO} records this service
 * having already made once. And <b>the label space is closed</b>: two keys, three state values, and
 * source values that come from an enum, so a series key can never carry a subject.
 */
class DirectoryRegistrationMetersServiceTest {

    private static final String METER_NAME = "directory.registrations";
    private static final String SAMPLED_AT_METER_NAME = "directory.registrations.sampled.timestamp";

    private MeterRegistry meterRegistry;

    private DirectoryRegistrationMetersService metersService;

    @BeforeEach
    void setup() {
        meterRegistry = new SimpleMeterRegistry();
        metersService = new DirectoryRegistrationMetersService(meterRegistry);
    }

    /**
     * A service that cannot aggregate the collection must publish <em>no series</em>, not a confident
     * zero: "nobody has registered" and "nobody has looked" are different facts.
     */
    @Test
    void nothingIsRegisteredBeforeTheFirstSuccessfulSample() {
        assertThat(meterRegistry.find(METER_NAME).gauges()).isEmpty();
        assertThat(meterRegistry.find(SAMPLED_AT_METER_NAME).gauge()).isNull();
    }

    /**
     * ⚠ Three buckets with three <b>different</b> counts. Equal counts would let any two be
     * transposed — or merged — with nothing to show for it, which is precisely the failure the third
     * bucket exists to prevent.
     */
    @Test
    void theThreeStatesArePublishedSeparately() {
        metersService.recordPopulation(List.of(new SourcePopulation("HC_PATIENT", 3, 2, 4)));

        assertThat(gaugeFor("HC_PATIENT", "activated").value()).isEqualTo(3);
        assertThat(gaugeFor("HC_PATIENT", "not-activated").value()).isEqualTo(2);
        assertThat(gaugeFor("HC_PATIENT", "not-reported").value()).isEqualTo(4);
    }

    /**
     * {@code not-reported} is not a synonym for {@code not-activated}, and this is the case that goes
     * red if somebody "simplifies" the family to the two states the sibling gateways publish.
     */
    @Test
    void notReportedIsItsOwnSeriesAndDoesNotLandInNotActivated() {
        metersService.recordPopulation(List.of(new SourcePopulation("HC_PROFESSIONAL", 0, 0, 5)));

        assertThat(gaugeFor("HC_PROFESSIONAL", "not-reported").value()).isEqualTo(5);
        assertThat(gaugeFor("HC_PROFESSIONAL", "not-activated").value()).isZero();
    }

    @Test
    void everySourceInTheReadingGetsItsOwnThreeSeries() {
        metersService.recordPopulation(
            List.of(new SourcePopulation("HC_PATIENT", 1, 0, 0), new SourcePopulation("HC_PROFESSIONAL", 0, 0, 2))
        );

        assertThat(meterRegistry.find(METER_NAME).gauges()).hasSize(6);
        assertThat(gaugeFor("HC_PATIENT", "activated").value()).isEqualTo(1);
        assertThat(gaugeFor("HC_PROFESSIONAL", "not-reported").value()).isEqualTo(2);
    }

    @Test
    void aLaterSampleMovesTheSameGaugesRatherThanRegisteringMore() {
        metersService.recordPopulation(List.of(new SourcePopulation("HC_PATIENT", 1, 1, 1)));
        metersService.recordPopulation(List.of(new SourcePopulation("HC_PATIENT", 4, 0, 2)));

        assertThat(meterRegistry.find(METER_NAME).gauges()).hasSize(3);
        assertThat(gaugeFor("HC_PATIENT", "activated").value()).isEqualTo(4);
        assertThat(gaugeFor("HC_PATIENT", "not-activated").value()).isZero();
        assertThat(gaugeFor("HC_PATIENT", "not-reported").value()).isEqualTo(2);
    }

    @Test
    void aSampleStampsTheTimestampGaugeOnce() {
        long before = Instant.now().getEpochSecond();

        metersService.recordPopulation(List.of(new SourcePopulation("HC_PATIENT", 1, 1, 1)));
        metersService.recordPopulation(List.of(new SourcePopulation("HC_PATIENT", 1, 1, 1)));

        assertThat(meterRegistry.find(SAMPLED_AT_METER_NAME).gauges()).hasSize(1);
        assertThat(meterRegistry.get(SAMPLED_AT_METER_NAME).gauge().value()).isGreaterThanOrEqualTo(before);
    }

    /**
     * The base units are what turn these names into {@code directory_registrations_accounts} and
     * {@code directory_registrations_sampled_timestamp_seconds} on the wire — a unit the translation
     * does not already see at the end of the name gets appended. Pinned because changing either
     * string renames the series every dashboard panel selects, with nothing failing.
     */
    @Test
    void theGaugesCarryTheBaseUnitsTheExporterExpects() {
        metersService.recordPopulation(List.of(new SourcePopulation("HC_PATIENT", 1, 1, 1)));

        assertThat(gaugeFor("HC_PATIENT", "activated").getId().getBaseUnit()).isEqualTo("accounts");
        assertThat(meterRegistry.get(SAMPLED_AT_METER_NAME).gauge().getId().getBaseUnit()).isEqualTo("seconds");
    }

    /**
     * ⚠ Backlog item 80's hard constraint, at the registry: this family's whole label space is two
     * keys, three literal state values and one enum name per source. Nothing that could carry a
     * subject is admitted, and a metric label is worse than a log line for one — it is a series key in
     * a store five other products read.
     */
    @Test
    void theLabelSpaceIsClosed() {
        metersService.recordPopulation(
            List.of(new SourcePopulation("HC_PATIENT", 1, 1, 1), new SourcePopulation("HC_PROFESSIONAL", 1, 1, 1))
        );

        List<Tag> tags = meterRegistry
            .find(METER_NAME)
            .gauges()
            .stream()
            .flatMap(gauge -> gauge.getId().getTags().stream())
            .toList();

        assertThat(tags).extracting(Tag::getKey).containsOnly("state", "source");
        assertThat(tags)
            .filteredOn(tag -> "state".equals(tag.getKey()))
            .extracting(Tag::getValue)
            .containsOnly("activated", "not-activated", "not-reported");
        assertThat(tags)
            .filteredOn(tag -> "source".equals(tag.getKey()))
            .extracting(Tag::getValue)
            .containsOnly("HC_PATIENT", "HC_PROFESSIONAL");
    }

    private Gauge gaugeFor(String source, String state) {
        return meterRegistry.get(METER_NAME).tag("source", source).tag("state", state).gauge();
    }
}
