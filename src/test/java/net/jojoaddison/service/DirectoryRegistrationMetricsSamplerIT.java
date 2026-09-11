package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.DirectoryLink;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.domain.enumeration.DirectorySubjectKind;
import net.jojoaddison.repository.DirectoryLinkRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The registration gauges against a real collection.
 *
 * <p>The unit test proves the arithmetic against a mocked aggregate. <b>What only a real MongoDB
 * proves is the third bucket</b>: {@code not-reported} is produced by a {@code $group} on a field
 * Spring Data does not write when it is null, so the absent key, the {@code null} key and the
 * {@code Document.getBoolean(key, false)} trap are all properties of the driver rather than of any
 * Java this repository owns. A mock cannot fail on any of them.
 */
@IntegrationTest
class DirectoryRegistrationMetricsSamplerIT {

    private static final String METER_NAME = "directory.registrations";

    @Autowired
    private DirectoryRegistrationMetricsSampler sampler;

    @Autowired
    private DirectoryLinkRepository directoryLinkRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    @AfterEach
    void clean() {
        directoryLinkRepository.deleteAll();
    }

    /**
     * ⚠ The case the whole third bucket exists for, watched end to end: a clinician known only from an
     * {@code onboarding.state} frame has no {@code activated} answer, and must land in
     * {@code not-reported} rather than in {@code not-activated}. Read the group key with
     * {@code getBoolean("activated", false)} — the obvious Java — and this gauge reports a clinician as
     * barred from signing in on the strength of an event nobody sent.
     */
    @Test
    void theThreeBucketsReachTheGaugesIncludingTheOneNoEventHasAnsweredFor() {
        save(DirectorySource.HC_PATIENT, "gauge-a@x", Boolean.TRUE);
        save(DirectorySource.HC_PATIENT, "gauge-b@x", Boolean.TRUE);
        save(DirectorySource.HC_PATIENT, "gauge-c@x", Boolean.FALSE);
        save(DirectorySource.HC_PROFESSIONAL, "gauge-d@x", null);
        save(DirectorySource.HC_PROFESSIONAL, "gauge-e@x", null);
        save(DirectorySource.HC_PROFESSIONAL, "gauge-f@x", null);
        save(DirectorySource.HC_PROFESSIONAL, "gauge-g@x", null);

        long before = Instant.now().getEpochSecond();
        sampler.sampleOnce();

        assertThat(gaugeValue(DirectorySource.HC_PATIENT, "activated")).isEqualTo(2);
        assertThat(gaugeValue(DirectorySource.HC_PATIENT, "not-activated")).isEqualTo(1);
        assertThat(gaugeValue(DirectorySource.HC_PATIENT, "not-reported")).isZero();
        assertThat(gaugeValue(DirectorySource.HC_PROFESSIONAL, "not-reported")).isEqualTo(4);
        assertThat(gaugeValue(DirectorySource.HC_PROFESSIONAL, "not-activated")).isZero();
        assertThat(meterRegistry.get("directory.registrations.sampled.timestamp").gauge().value()).isGreaterThanOrEqualTo(before);
    }

    /**
     * Every source gets its three series even when it has said nothing, because a source that vanishes
     * from a dashboard is indistinguishable from a source that is quiet — a broken consumer and a slow
     * week, which need opposite responses.
     */
    @Test
    void everySourceGetsItsSeriesEvenWhenItHasSaidNothing() {
        save(DirectorySource.HC_PATIENT, "only-patient@x", Boolean.TRUE);

        sampler.sampleOnce();

        assertThat(meterRegistry.find(METER_NAME).gauges()).hasSize(DirectorySource.values().length * 3);
        assertThat(gaugeValue(DirectorySource.HC_PROFESSIONAL, "activated")).isZero();
    }

    private double gaugeValue(DirectorySource source, String state) {
        return meterRegistry.get(METER_NAME).tag("source", source.name()).tag("state", state).gauge().value();
    }

    private void save(DirectorySource source, String externalKey, Boolean activated) {
        DirectoryLink link = new DirectoryLink();
        link.setSource(source);
        link.setExternalKey(externalKey);
        link.setActivated(activated);
        link.setSubjectKind(source == DirectorySource.HC_PATIENT ? DirectorySubjectKind.PATIENT : DirectorySubjectKind.PROFESSIONAL);
        link.setFirstSeenAt(Instant.now());
        link.setLastEventAt(Instant.now());
        directoryLinkRepository.save(link);
    }
}
