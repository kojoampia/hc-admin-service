package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.management.DirectoryRegistrationMetersService;
import net.jojoaddison.service.dto.RegistrationTotalsDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * What the sampler publishes, and what it does when the aggregation cannot be read.
 */
@ExtendWith(MockitoExtension.class)
class DirectoryRegistrationMetricsSamplerTest {

    @Mock
    private DirectoryRegistrationService directoryRegistrationService;

    private final DirectoryRegistrationMetersService meters = new DirectoryRegistrationMetersService(new SimpleMeterRegistry());

    private DirectoryRegistrationMetricsSampler sampler() {
        return new DirectoryRegistrationMetricsSampler(directoryRegistrationService, meters);
    }

    private static RegistrationTotalsDTO totals(List<RegistrationTotalsDTO.SourceTotals> bySource) {
        long activated = bySource.stream().mapToLong(RegistrationTotalsDTO.SourceTotals::activated).sum();
        long notActivated = bySource.stream().mapToLong(RegistrationTotalsDTO.SourceTotals::notActivated).sum();
        long notReported = bySource.stream().mapToLong(RegistrationTotalsDTO.SourceTotals::notReported).sum();
        return new RegistrationTotalsDTO(activated, notActivated, notReported, activated + notActivated + notReported, bySource);
    }

    /**
     * ⚠ The three buckets reach the gauges as three, per source. Different counts everywhere, because
     * equal ones would let two buckets be transposed or merged with nothing to show for it.
     */
    @Test
    void theThreeBucketsArePublishedPerSource() {
        when(directoryRegistrationService.totals()).thenReturn(
            totals(
                List.of(
                    new RegistrationTotalsDTO.SourceTotals(DirectorySource.HC_PATIENT, 3, 2, 1, 6),
                    new RegistrationTotalsDTO.SourceTotals(DirectorySource.HC_PROFESSIONAL, 0, 4, 5, 9)
                )
            )
        );

        sampler().sampleOnce();

        assertThat(meters.countFor("HC_PATIENT", "activated")).isEqualTo(3);
        assertThat(meters.countFor("HC_PATIENT", "not-activated")).isEqualTo(2);
        assertThat(meters.countFor("HC_PATIENT", "not-reported")).isEqualTo(1);
        assertThat(meters.countFor("HC_PROFESSIONAL", "activated")).isZero();
        assertThat(meters.countFor("HC_PROFESSIONAL", "not-activated")).isEqualTo(4);
        assertThat(meters.countFor("HC_PROFESSIONAL", "not-reported")).isEqualTo(5);
    }

    /**
     * The source label is the enum's own name, which is also what {@code directory_link.source} holds
     * and what the endpoint serves. Pinned because a prettier rendering here would be a second
     * spelling of one value, and only a dashboard would ever notice.
     */
    @Test
    void theSourceLabelIsTheEnumName() {
        when(directoryRegistrationService.totals()).thenReturn(
            totals(List.of(new RegistrationTotalsDTO.SourceTotals(DirectorySource.HC_PROFESSIONAL, 1, 0, 0, 1)))
        );

        sampler().sampleOnce();

        assertThat(meters.countFor("HC_PROFESSIONAL", "activated")).isEqualTo(1);
        assertThat(meters.countFor("hc-professional", "activated")).isZero();
    }

    /**
     * ⚠ A failed aggregation leaves the previous reading standing and does not throw out of the
     * scheduled tick. Zeroing would render an unreachable database as "every registration vanished",
     * which is an outage-shaped figure produced by a monitoring bug; throwing would kill the schedule
     * for the life of the process.
     */
    @Test
    void aFailedAggregationLeavesThePreviousReadingStanding() {
        when(directoryRegistrationService.totals()).thenReturn(
            totals(List.of(new RegistrationTotalsDTO.SourceTotals(DirectorySource.HC_PATIENT, 3, 2, 1, 6)))
        );
        sampler().sampleOnce();

        when(directoryRegistrationService.totals()).thenThrow(new IllegalStateException("mongo is away"));

        sampler().sampleOnce();

        assertThat(meters.countFor("HC_PATIENT", "activated")).isEqualTo(3);
        assertThat(meters.countFor("HC_PATIENT", "not-activated")).isEqualTo(2);
        assertThat(meters.countFor("HC_PATIENT", "not-reported")).isEqualTo(1);
    }

    /** Nothing is published at all before an aggregation succeeds — the meters' absent-rather-than-zero rule. */
    @Test
    void aCollectionThatNeverAggregatesPublishesNothing() {
        when(directoryRegistrationService.totals()).thenThrow(new IllegalStateException("mongo is away"));

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DirectoryRegistrationMetersService freshMeters = new DirectoryRegistrationMetersService(registry);

        new DirectoryRegistrationMetricsSampler(directoryRegistrationService, freshMeters).sampleOnce();

        assertThat(registry.find("directory.registrations").gauges()).isEmpty();
    }
}
