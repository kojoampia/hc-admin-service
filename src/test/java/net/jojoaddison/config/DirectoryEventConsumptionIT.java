package net.jojoaddison.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.DirectoryLink;
import net.jojoaddison.domain.Patient;
import net.jojoaddison.domain.ServicePlan;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.domain.enumeration.DirectorySubjectKind;
import net.jojoaddison.repository.DirectoryLinkRepository;
import net.jojoaddison.repository.PatientRepository;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.repository.ServicePlanRepository;
import net.jojoaddison.service.DirectoryProjectionService;
import net.jojoaddison.service.LogPseudonym;
import net.jojoaddison.service.SiblingEventParser;
import net.jojoaddison.service.dto.SiblingDomainEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * A registration on another stack reaches this service's directory.
 *
 * <h2>What this class is for</h2>
 *
 * <p>A patient registered on production and the admin dashboard read zero, and the cause was that
 * this service had no subscription at all — one inbound binding, to its own UI fan-out channel,
 * while forty domain events sat unread in three topics it had never named. Nothing in this
 * repository could have found that, because <b>a consumer that is never invoked and a consumer that
 * works look identical from a green build</b>: both leave the collections empty in a suite that
 * never publishes anything.
 *
 * <p>So every case here drives a real frame through the binding and reads the result out of the
 * database. {@code ConfigurationBindingTest} covers the half this cannot — the topic names in the
 * shipped file, which the test profile overrides.
 *
 * <p>{@code TestChannelBinderConfiguration} substitutes an in-memory binder, and
 * {@link InputDestination#send} runs the consumer on the calling thread, so there is nothing to wait
 * for and no flake to tune out. It does not need a broker; the assertions are about the binding, the
 * parser and the merge rule, and Kafka's own delivery is not this service's to test.
 */
@IntegrationTest
@ImportAutoConfiguration(TestChannelBinderConfiguration.class)
class DirectoryEventConsumptionIT {

    private static final String PATIENT_TOPIC = "patient-events";
    private static final String PROFESSIONAL_TOPIC = "hc.professional.registration";
    private static final String PROFESSIONAL_ENTITY_TOPIC = "hc.professional.entity";
    private static final String EMAIL = "ama.mensah@directory-consumption-it.example.com";
    private static final String ACCOUNT_ID = "acc-directory-consumption-it";

    @Autowired
    private InputDestination input;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private DirectoryLinkRepository directoryLinkRepository;

    /** For the one case that needs a catalogue to resolve a tier code against — see item 48. */
    @Autowired
    private ServicePlanRepository servicePlanRepository;

    @Autowired
    private DirectoryProjectionService projection;

    @Autowired
    private SiblingEventParser parser;

    @BeforeEach
    @AfterEach
    void clean() {
        directoryLinkRepository.deleteAll();
        patientRepository.deleteAll();
        professionalRepository.deleteAll();
        servicePlanRepository.deleteAll();
    }

    /** The reported defect, end to end within this service. */
    @Test
    void aRegistrationOnHcPatientBecomesAPatientHere() {
        sendPatient(accountCreated("2026-09-01T08:00:00Z", false));

        assertThat(patientRepository.count()).as("the registration should have produced exactly one patient").isEqualTo(1);

        Patient patient = patientRepository.findAll().get(0);
        assertThat(patient.getStatus()).as("an account that has not been activated is not yet ACTIVE").isEqualTo(AccountStatus.PENDING);
        assertThat(patient.getJoinedOn()).isEqualTo(LocalDate.of(2026, 9, 1));

        DirectoryLink link = link(DirectorySource.HC_PATIENT, EMAIL).orElseThrow();
        assertThat(link.getLogin()).isEqualTo("amensah");
        assertThat(link.getEmail()).isEqualTo(EMAIL);
        assertThat(link.getLocalId())
            .as("the link is what makes the write idempotent — it must name the record")
            .isEqualTo(patient.getId());
        assertThat(link.getLastEventType()).isEqualTo("AccountCreated");
    }

    /**
     * Kafka is at-least-once, so this is the ordinary case rather than the exotic one.
     *
     * <p>Asserted on the count and not on "no exception": duplicating a patient does not fail
     * anything, it adds a person to every dashboard tile who does not exist.
     */
    @Test
    void aRedeliveredEventChangesNoCount() {
        String frame = accountCreated("2026-09-01T08:00:00Z", false);

        sendPatient(frame);
        String firstId = patientRepository.findAll().get(0).getId();
        sendPatient(frame);
        sendPatient(frame);

        assertThat(patientRepository.count()).as("three deliveries of one event are one patient").isEqualTo(1);
        assertThat(directoryLinkRepository.count()).as("and one link").isEqualTo(1);
        assertThat(patientRepository.findAll().get(0).getId()).as("and the same record, not a replacement").isEqualTo(firstId);
    }

    /** Activation promotes the account, and it is the only status move the stream may make. */
    @Test
    void activationPromotesThePendingAccount() {
        sendPatient(accountCreated("2026-09-01T08:00:00Z", false));
        sendPatient(accountActivated("2026-09-01T09:00:00Z"));

        assertThat(patientRepository.count()).isEqualTo(1);
        Patient patient = patientRepository.findAll().get(0);
        assertThat(patient.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(patient.getLastActiveOn()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    /**
     * <b>The data-loss case, and the reason the projection-versus-record question had to be answered
     * before any of this was written.</b>
     *
     * <p>hc-admin's {@code Patient} is a record an administrator also edits — plan, hub, clinical
     * lead, status. A consumer that treated it as a projection it may overwrite would undo those
     * edits on the next redelivery, which is not a crash and not a log line; it is the console
     * quietly disagreeing with the person who last used it.
     */
    @Test
    void anAdministratorsEditIsNotUndoneByAReplay() {
        sendPatient(accountCreated("2026-09-01T08:00:00Z", false));
        sendPatient(accountActivated("2026-09-01T09:00:00Z"));

        // An administrator does what the console lets them do.
        Patient edited = patientRepository.findAll().get(0);
        edited.setStatus(AccountStatus.SUSPENDED);
        edited.setCaseCount(7);
        edited.setIsArchived(true);
        patientRepository.save(edited);

        // The whole topic is replayed — a redeploy with resetOffsets, a restored broker, anything.
        sendPatient(accountCreated("2026-09-01T08:00:00Z", false));
        sendPatient(accountActivated("2026-09-01T09:00:00Z"));

        Patient after = patientRepository.findAll().get(0);
        assertThat(after.getStatus())
            .as("only PENDING may become ACTIVE; a suspension is this service's own decision")
            .isEqualTo(AccountStatus.SUSPENDED);
        assertThat(after.getCaseCount()).as("no event carries a case count, so nothing may write one after creation").isEqualTo(7);
        assertThat(after.getIsArchived()).as("archiving is an administrator's decision and no event revokes it").isTrue();
        assertThat(patientRepository.count()).isEqualTo(1);
    }

    /**
     * An event older than what has been applied is discarded rather than replayed forwards.
     *
     * <p>Out-of-order delivery is normal on a re-read: the two account events come from hc-patient's
     * gateway and the onboarding ones from its api, and a group reading from the earliest offset
     * meets them in log order, not in wall-clock order.
     */
    @Test
    void anOlderEventDoesNotWindTheRecordBack() {
        sendPatient(accountCreated("2026-09-01T08:00:00Z", false));
        sendPatient(accountActivated("2026-09-02T09:00:00Z"));

        // The registration comes round again, after the activation. On a group reading from the
        // earliest offset that is the ordinary case, not the exotic one: the gateway's two frames and
        // the api's onboarding frames are re-read in log order, and a redelivery can land anywhere.
        sendPatient(accountCreated("2026-09-01T08:00:00Z", false));

        DirectoryLink link = link(DirectorySource.HC_PATIENT, EMAIL).orElseThrow();
        assertThat(link.getLastEventType()).as("the older frame must not become the last thing known").isEqualTo("AccountActivated");
        assertThat(patientRepository.findAll().get(0).getLastActiveOn()).isEqualTo(LocalDate.of(2026, 9, 2));
    }

    /**
     * {@code OnboardingStarted} is the only event that publishes the email-to-patientId mapping, and
     * it must land on the link — it is the identifier hc-professional's roster keys visits on, and
     * the join backlog item 22 is about.
     */
    @Test
    void onboardingBindsTheEmailToThePatientServiceId() {
        sendPatient(accountCreated("2026-09-01T08:00:00Z", false));
        sendPatient(onboardingStarted("2026-09-01T10:00:00Z", "p-1234"));

        assertThat(link(DirectorySource.HC_PATIENT, EMAIL).orElseThrow().getExternalId()).isEqualTo("p-1234");
        assertThat(patientRepository.count()).as("learning an id is not meeting a new person").isEqualTo(1);
    }

    /**
     * A frame this service cannot read does not stop the ones after it.
     *
     * <p>An exception out of a Spring Cloud Stream consumer is retried, and while that happens the
     * partition makes no progress — so one unusable message would hold up every subject whose key
     * hashes to it. These topics belong to other products and gain event types without reference to
     * this consumer.
     *
     * <p><b>Asserted on what the good frame wrote, not on a count, and the difference was a real
     * defect in this test.</b> It used to send three frames and assert {@code count == 1}. The middle
     * frame was not bad: {@code sendPatient} sets the {@code patientKey} header, so its subject key
     * resolved, and it carried no {@code occurredAt} — which the parser then read as {@code now}. So
     * it created the patient, the third frame was <em>older</em> than that watermark and was discarded
     * as stale, and the 1 being asserted was the bad frame's row. The assertion said the good frame
     * had arrived and would have passed identically if it had been dropped. A count is satisfied by
     * anything; read the record.
     */
    @Test
    void anUnreadableFrameDoesNotStopTheConsumer() {
        sendPatient("this is not an envelope");
        // The exact frame that used to poison the watermark: a well-formed envelope for this very
        // subject whose timestamp cannot be read. It is now ignored, so the good frame behind it —
        // which is dated in the past, and would have been stale against a "now" watermark — lands.
        sendPatient(accountCreated("the day before yesterday", false));
        sendPatient(accountCreated("2026-09-01T08:00:00Z", false));

        DirectoryLink link = link(DirectorySource.HC_PATIENT, EMAIL).orElseThrow();
        assertThat(link.getLastEventType()).as("the good frame after two bad ones is what wrote the link").isEqualTo("AccountCreated");
        assertThat(link.getLastEventAt())
            .as("and it is THAT frame: an unreadable timestamp read as `now` would leave a watermark of today")
            .isEqualTo(Instant.parse("2026-09-01T08:00:00Z"));
        assertThat(link.getLogin()).isEqualTo("amensah");
        assertThat(patientRepository.findAll().get(0).getJoinedOn())
            .as("the patient is dated from the good frame's occurredAt, not from when the run happened")
            .isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(patientRepository.count()).isEqualTo(1);
    }

    /**
     * A clinician's registration is recorded, and no {@code Professional} is invented for it.
     *
     * <p>{@code Professional} requires a {@code role} and a {@code licenceNumber} and
     * {@code ValidatingMongoEventListener} enforces both; neither is on a registration event, because
     * they are what credentialing collects. A row carrying a made-up licence number, in the directory
     * whose purpose is verifying licences, would be the most plausible possible wrong answer.
     *
     * <p>The two frames are asserted apart as well as together, because the registration on its own is
     * the case item 46 was reported for and it renders differently: {@code state} is the one field the
     * console prints verbatim, and this parser fell back to the event type where the payload carried
     * none, so a clinician who had just registered was shown as "· registration.created".
     */
    @Test
    void aProfessionalRegistrationIsRecordedWithoutInventingALicence() {
        sendProfessional(registrationCreated());

        assertThat(link(DirectorySource.HC_PROFESSIONAL, ACCOUNT_ID).orElseThrow())
            .as("a registration is not a stage of onboarding, and its own type is not a state")
            .satisfies(fresh -> {
                assertThat(fresh.getState()).isNull();
                assertThat(fresh.getLastEventType())
                    .as("the type is recorded, in the field that is for types")
                    .isEqualTo("registration.created");
            });

        sendProfessional(onboardingState("COMPLETED"));

        DirectoryLink link = link(DirectorySource.HC_PROFESSIONAL, ACCOUNT_ID).orElseThrow();
        assertThat(link.getLogin()).isEqualTo("kboateng");
        assertThat(link.getState()).as("onboarding state rides the registration topic and is the later of the two").isEqualTo("COMPLETED");
        assertThat(link.getLocalId()).as("no local row is created for a professional, deliberately").isNull();

        assertThat(professionalRepository.count()).isZero();
        assertThat(patientRepository.count()).as("and a clinician is certainly not a patient").isZero();
    }

    // --- the two-phase professional contract, backlog item 47 -------------------------------------

    /**
     * <b>The two phases join on {@code accountId} and produce one row, whichever order they land in.</b>
     *
     * <p>This is the contract's central claim and the reason it is asserted in both directions in the
     * next case: phase 1 is published by hc-professional's gateway to
     * {@code hc.professional.registration} and phase 2 by its api to {@code hc.professional.entity},
     * and <em>between two topics there is no ordering at all</em>. Both consumer groups read from the
     * earliest offset, so either sequence is ordinary.
     *
     * <p>Asserted on the fields rather than on a count, because the failure this guards is not "two
     * rows" — it is one row with half of it silently missing, which a count cannot see.
     */
    @Test
    void bothPhasesLandOnOneRowJoinedOnTheAccountId() {
        sendProfessional(accountCreatedOnProfessional(true));
        sendProfessionalEntity(profileStatus(ACCOUNT_ID, "2026-09-02T14:47:05Z", true, true));

        assertThat(directoryLinkRepository.count()).as("one clinician is one link, not one per topic").isEqualTo(1);

        DirectoryLink link = link(DirectorySource.HC_PROFESSIONAL, ACCOUNT_ID).orElseThrow();
        assertThat(link.getLogin()).as("phase 1 — the field the console shows, and never the email").isEqualTo("kboateng");
        assertThat(link.getActivated()).as("phase 1 — read from AccountCreated's data, never inferred").isTrue();
        assertThat(link.getAccountCreatedDate())
            .as("phase 1's createdDate is on no frame their gateway sends, and occurredAt is not substituted for it")
            .isNull();
        assertThat(link.getProfileId()).as("phase 2").isEqualTo("prof-9");
        assertThat(link.getProfileComplete()).isTrue();
        assertThat(link.getProfileVerified()).isTrue();
        assertThat(link.getProfileModifiedDate()).isEqualTo(Instant.parse("2026-09-02T14:47:00Z"));
        assertThat(link.getProfileLastModifiedBy())
            .as("hc-professional's login for whoever wrote the profile — their auditor fills it from the JWT subject")
            .isEqualTo("yasante");
        assertThat(link.getPhasesJoinedAt())
            .as("and this service records that it SAW the two meet, which is what keeps the join guard alive on a seeded stack")
            .isNotNull();

        assertThat(professionalRepository.count())
            .as("a complete, verified profile is still not a role and a licence number — no Professional is invented")
            .isZero();
    }

    /**
     * <b>Phase 2 first, which is the ordering the console has to be right about and the one nobody
     * writes a fixture for.</b>
     *
     * <p>A profile status for an account this service has not been told about is <em>not an error</em>
     * and must not be buffered waiting for a name: the row is created keyed on the {@code accountId},
     * with the identity fields absent, and phase 1 fills them in when it arrives. Holding it back
     * would make a clinician invisible for as long as the two topics disagreed, which on a backfill
     * is the whole backfill.
     */
    @Test
    void aProfileStatusArrivingFirstCreatesTheRowAndPhaseOneFillsItIn() {
        sendProfessionalEntity(profileStatus(ACCOUNT_ID, "2026-09-02T14:47:05Z", false, false));

        DirectoryLink alone = link(DirectorySource.HC_PROFESSIONAL, ACCOUNT_ID).orElseThrow();
        assertThat(alone.getSubjectKind())
            .as("a profile on that topic is a clinician's, so the row knows what it is before it knows who")
            .isEqualTo(DirectorySubjectKind.PROFESSIONAL);
        assertThat(alone.getLogin()).as("and nothing is invented to name it by").isNull();
        assertThat(alone.getEmail()).isNull();
        assertThat(alone.getActivated()).as("nor is activation guessed from a profile existing").isNull();
        assertThat(alone.getProfileComplete()).as("reported as incomplete — which is a fact, unlike the nulls above").isFalse();
        assertThat(alone.getLastEventAt()).as("no phase 1 has been seen, and the row says so by omission").isNull();

        sendProfessional(accountCreatedOnProfessional(true));

        DirectoryLink joined = link(DirectorySource.HC_PROFESSIONAL, ACCOUNT_ID).orElseThrow();
        assertThat(directoryLinkRepository.count()).as("still one row — the second phase filled it in rather than adding one").isEqualTo(1);
        assertThat(joined.getLogin()).isEqualTo("kboateng");
        assertThat(joined.getActivated()).isTrue();
        assertThat(joined.getProfileComplete()).as("and phase 1 did not overwrite phase 2's half").isFalse();
        assertThat(joined.getProfileId()).isEqualTo("prof-9");
    }

    /**
     * <b>Unknown is not false, end to end.</b>
     *
     * <p>A clinician with a registration and no profile status has {@code complete} and
     * {@code verified} <em>unknown</em>. Storing them as false would make the console assert
     * something about a person from the absence of a message — items 27(a) and 46's prohibition one
     * column along — and it is exactly the shape a {@code boolean} field or an
     * {@code asBoolean(false)} at the edge would produce silently.
     */
    @Test
    void aRegistrationWithNoProfileStatusLeavesTheProfileFieldsUnknown() {
        sendProfessional(accountCreatedOnProfessional(false));

        DirectoryLink link = link(DirectorySource.HC_PROFESSIONAL, ACCOUNT_ID).orElseThrow();
        assertThat(link.getActivated()).as("this account is reported as NOT activated, which is a fact").isFalse();
        assertThat(link.getProfileComplete()).as("and its profile status is unknown, which is not the same as incomplete").isNull();
        assertThat(link.getProfileVerified()).isNull();
        assertThat(link.getProfileEventAt()).isNull();
    }

    /**
     * The phase-2 watermark is its own, and comparing it against phase 1's would discard real events.
     *
     * <p>Two applications, two clocks, two topics. A profile status dated before the registration's
     * {@code occurredAt} is entirely ordinary — the profile may genuinely predate the account event
     * this service happens to have, and the gateway's clock need not agree with the api's — so
     * sharing {@code last_event_at} would drop it as stale while both streams sat at lag zero.
     *
     * <p>Inverted by construction: the profile status here is dated a month <em>before</em> the
     * registration, so a shared watermark makes this case fail on the profile fields being null.
     */
    @Test
    void thePhaseTwoWatermarkIsSeparateFromPhaseOnes() {
        sendProfessional(accountCreatedOnProfessional(true));
        sendProfessionalEntity(profileStatus(ACCOUNT_ID, "2026-08-01T09:00:00Z", true, false));

        DirectoryLink link = link(DirectorySource.HC_PROFESSIONAL, ACCOUNT_ID).orElseThrow();
        assertThat(link.getProfileComplete()).as("an older profile event is not stale against a newer registration").isTrue();
        assertThat(link.getProfileEventAt()).isEqualTo(Instant.parse("2026-08-01T09:00:00Z"));
        assertThat(link.getLastEventAt()).as("and phase 1's watermark is untouched by it").isEqualTo(Instant.parse("2026-09-01T08:00:00Z"));
    }

    /** Within phase 2, the watermark does its usual job: a replayed older status writes nothing. */
    @Test
    void anOlderProfileStatusDoesNotWindTheProfileBack() {
        sendProfessionalEntity(profileStatus(ACCOUNT_ID, "2026-09-02T14:47:05Z", true, true));
        sendProfessionalEntity(profileStatus(ACCOUNT_ID, "2026-08-01T09:00:00Z", false, false));

        DirectoryLink link = link(DirectorySource.HC_PROFESSIONAL, ACCOUNT_ID).orElseThrow();
        assertThat(link.getProfileComplete()).as("the replay is older than what has been applied and is discarded").isTrue();
        assertThat(link.getProfileVerified()).isTrue();
    }

    /**
     * <b>The 82%, refused before anything is written.</b>
     *
     * <p>Asserted on the collection being empty rather than on the parser answering empty, because
     * the cost this avoids is a write per frame during a backfill from the earliest offset — and a
     * consumer that parsed the Task and then declined to store it would pass a parser-level test
     * while still opening a link for every task in the topic.
     */
    @Test
    void aTaskOnTheEntityTopicWritesNothingAtAll() {
        sendProfessionalEntity(taskCreated());

        assertThat(directoryLinkRepository.count()).as("this service has no Task and must not learn a clinician from one").isZero();
    }

    /**
     * <b>A legacy {@code entity.created} for a {@code Profile} writes nothing — the architect's
     * decision 3, and the case with real consequences on the day of deploy.</b>
     *
     * <p>Those frames exist on {@code hc.professional.entity} already, roughly thirty of them, and
     * this consumer group reads from the earliest offset. Accepting them would open a link per frame
     * keyed on the <b>login</b> — their older envelope's {@code accountId} — beside the links phase 1
     * has under a {@code User.id}, so every one would render as "identity not on file" and the
     * dashboard's awaiting figure would roughly double with no event having gone wrong. That is item
     * 46 § 2's moved figure exactly.
     *
     * <p>Asserted on the collection rather than on the parser for {@code aTaskOnTheEntityTopic...}'s
     * reason, and with the accountId deliberately unlike the one phase 1 uses, because a fixture that
     * reused it would let a wrongly-accepted frame land on the right row and look harmless.
     */
    @Test
    void aLegacyProfileEntityEventOnThatTopicWritesNothingEither() {
        sendProfessionalEntity(legacyProfileEntityCreated());

        assertThat(directoryLinkRepository.count())
            .as("thirty phantom rows on the day of deploy is what accepting these would cost")
            .isZero();
    }

    /**
     * <b>{@code AccountActivated} moves activation and leaves the onboarding state alone.</b>
     *
     * <p>Two properties in one case, because they are produced by one sequence and the second is
     * invisible without the first. A registration puts three frames on that topic under two
     * envelopes, all keyed identically and therefore ordered, and {@code AccountCreated} /
     * {@code AccountActivated} carry no {@code state} — so the write path had to stop clearing a
     * field an event says nothing about. Before that, the console's "· IN_PROGRESS" suffix would have
     * vanished the moment the third frame of an ordinary registration landed.
     */
    @Test
    void anActivationMovesTheAccountAndLeavesTheOnboardingStateAlone() {
        sendProfessional(accountCreatedOnProfessional(false));
        sendProfessional(onboardingState("IN_PROGRESS"));

        assertThat(link(DirectorySource.HC_PROFESSIONAL, ACCOUNT_ID).orElseThrow().getActivated())
            .as("a self-service registration is awaiting its email link, and that is a fact rather than an unknown")
            .isFalse();

        sendProfessional(accountActivatedOnProfessional());

        DirectoryLink link = link(DirectorySource.HC_PROFESSIONAL, ACCOUNT_ID).orElseThrow();
        assertThat(link.getActivated()).as("the far side owns this outright, including the change back and forth").isTrue();
        assertThat(link.getState())
            .as("and an event that says nothing about onboarding is not an event saying onboarding has been undone")
            .isEqualTo("IN_PROGRESS");
    }

    /**
     * <b>The failure that looks correct on both sides.</b>
     *
     * <p>If the two publishers ever key their phases on different identifiers, every event of both
     * kinds is published, delivered, parsed and stored; both groups sit at lag zero; nothing is
     * dead-lettered; and this service fills with rows that can never be paired. Backlog item 47 names
     * it as the one failure to guard, and no check either product can run on itself would see it.
     *
     * <p>So it is a {@code WARN}, on the narrow condition that <b>no</b> account has ever had both
     * phases while there is at least one of each waiting alone. The line names what to compare rather
     * than asserting a fault, because there is a legitimate window — the first out-of-order profile,
     * before anything has paired — in which it fires and nothing is wrong. That window closes on the
     * first pairing, which {@link #theJoinWarningIsSilentOnceAnyAccountHasBothPhases} pins.
     */
    @Test
    void aSystematicAccountIdMismatchIsReportedRatherThanSilent() {
        sendProfessional(accountCreatedOnProfessional(true));

        Logger logger = (Logger) LoggerFactory.getLogger("net.jojoaddison");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            // The same clinician, from the api, under a key that does not match the gateway's.
            sendProfessionalEntity(profileStatus("a-different-key-entirely", "2026-09-02T14:47:05Z", true, true));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(directoryLinkRepository.count()).as("two rows for one person, which is what the mismatch produces").isEqualTo(2);
        assertThat(appender.list)
            .as("and it has to be reported: both streams are healthy and the console is permanently half empty")
            .anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage())
                    .contains("accountId")
                    .contains("hc.professional.registration")
                    .contains("hc.professional.entity");
            });
    }

    /**
     * And it is silent in the ordinary out-of-order case, which is what stops it being an alarm that
     * is always on.
     *
     * <p>One account with both phases is enough to prove the two producers agree on the key space, so
     * a second account arriving phase-2-first says nothing about a mismatch — it says the topics are
     * unordered, which they are.
     */
    @Test
    void theJoinWarningIsSilentOnceAnyAccountHasBothPhases() {
        sendProfessional(accountCreatedOnProfessional(true));
        sendProfessionalEntity(profileStatus(ACCOUNT_ID, "2026-09-02T14:47:05Z", true, true));

        Logger logger = (Logger) LoggerFactory.getLogger("net.jojoaddison");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            sendProfessionalEntity(profileStatus("another-clinician", "2026-09-02T14:48:00Z", false, false));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(appender.list)
            .as("an unordered arrival is the primary path, not a fault, and must not warn")
            .noneSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
    }

    /**
     * <b>A seeded joined row does not silence the guard, which it did until 2026-09-08.</b>
     *
     * <p>The suppressor used to be "some link holds both watermarks", and the {@code test} fixture
     * seeds exactly such a link — {@code dl-prof-complete}, deliberately, because the console's joined
     * row is otherwise unreachable outside production, which is items 45 and 46's lesson. So this
     * contract's own fixture disabled this contract's own guard on {@code quality/}, on
     * {@code deploy/e2e/} and under {@code ng serve}: every machine short of production, and the only
     * ones where a key mismatch would be seen before it shipped.
     *
     * <p>The suppressor is {@link DirectoryLink#getPhasesJoinedAt()} now — written only by the two
     * consumer write paths, on the write that brings the second phase in, and by no fixture
     * ({@code DevelopmentDataInitializerTest} pins that). This case writes the seeded shape directly,
     * both watermarks and no join stamp, and then produces the mismatch: the guard has to speak.
     */
    @Test
    void aSeededJoinedRowDoesNotSilenceTheGuardOnAQualityStack() {
        DirectoryLink seeded = new DirectoryLink();
        seeded.setSource(DirectorySource.HC_PROFESSIONAL);
        seeded.setExternalKey("seeded-clinician");
        seeded.setSubjectKind(DirectorySubjectKind.PROFESSIONAL);
        seeded.setLastEventAt(Instant.parse("2026-09-01T09:21:00Z"));
        seeded.setProfileEventAt(Instant.parse("2026-09-02T14:47:05Z"));
        directoryLinkRepository.save(seeded);

        sendProfessional(accountCreatedOnProfessional(true));

        Logger logger = (Logger) LoggerFactory.getLogger("net.jojoaddison");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            sendProfessionalEntity(profileStatus("a-different-key-entirely", "2026-09-02T14:47:05Z", true, true));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(appender.list)
            .as("the fixture proves nothing about whether the two publishers agree — no event produced that row")
            .anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).contains("accountId");
            });
    }

    /**
     * <b>Storing a link and creating nothing leaves a trace a person can find.</b>
     *
     * <p>The reported half of backlog item 46. On production, a service that had been consuming
     * {@code hc.professional.registration} since the 2026-09-06 deploy had <b>zero</b> log lines
     * mentioning {@code HC_PROFESSIONAL} — while the group's offset moved 28 → 32 across a clinician's
     * registration, with no lag and nothing dead-lettered. Only the creating path logged, and a
     * {@link net.jojoaddison.service.dto.SiblingDomainEvent.Disposition#LINK_ONLY} event creates
     * nothing, so an operator could not tell <b>"the event never arrived"</b> from <b>"the event
     * arrived and was deliberately stored as a link"</b> — two states with opposite responses.
     *
     * <p>At {@code INFO}, deliberately: {@code application-prod.yml} runs this package at {@code INFO},
     * so a line at {@code debug} would be the same absence wearing a level. And asserted on the
     * <em>content</em> rather than on "something was logged" — the source and the kind are what tell a
     * reader which stream and which decision, and a line naming neither would pass a "not empty" check
     * while answering nothing.
     *
     * <p>The subject is still a digest, which the sweep in {@code LogPseudonymTest} enforces
     * statically; here it is asserted positively, because the way this line goes wrong is somebody
     * dropping the subject from it rather than somebody printing the key.
     */
    @Test
    void aLinkOnlyRegistrationSaysSoAtInfo() {
        Logger logger = (Logger) LoggerFactory.getLogger("net.jojoaddison");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Level original = logger.getLevel();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);

        try {
            sendProfessional(registrationCreated());
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(original);
            appender.stop();
        }

        assertThat(appender.list)
            .as("a registration that creates no record must still say so, at the level production runs")
            .filteredOn(event -> event.getLevel() == Level.INFO)
            .anySatisfy(event -> {
                String line = event.getFormattedMessage();
                assertThat(line).contains("HC_PROFESSIONAL").contains("PROFESSIONAL").contains("registration.created");
                assertThat(line)
                    .as("and name the subject as a digest, so an operator has something to search for")
                    .contains(LogPseudonym.subject(ACCOUNT_ID));
                assertThat(line).as("the accountId is a correlation key and is not logged verbatim").doesNotContain(ACCOUNT_ID);
            });
    }

    /**
     * <b>The care-angel defect, end to end.</b>
     *
     * <p>hc-patient's {@code CareAngelResource.nominate} publishes {@code AccountCreated} and then
     * {@code AccountActivated}, both keyed on the <em>angel's</em> address. Until 2026-09-05 this
     * consumer read only the type, so every nomination on that stack became an ACTIVE patient here —
     * counted on the dashboard tile, drawn on the account-mix chart, listed in the directory as a
     * nameless row, and unremovable, because the merge rule forbids the consumer to delete.
     *
     * <p>An angel is not a patient in this service: hc-admin models one as its own {@code Angel}
     * entity, joined to the patient who nominated them, and neither half of that join is on the wire.
     * So the nomination is recorded as a link — the same answer as for a clinician, for the same
     * reason — and no {@code Patient} is created by either frame.
     */
    @Test
    void aCareAngelNominationDoesNotBecomeAPatient() {
        sendPatient(careAngelNominated("2026-09-01T08:00:00Z"));
        sendPatient(accountActivated("2026-09-01T08:00:01Z"));

        assertThat(patientRepository.count()).as("a care angel is not a patient and must not be counted as one").isZero();

        DirectoryLink link = link(DirectorySource.HC_PATIENT, EMAIL).orElseThrow();
        assertThat(link.getSubjectKind())
            .as("the link records what they are, so nothing later mistakes them for a patient")
            .isEqualTo(DirectorySubjectKind.CARE_ANGEL);
        assertThat(link.getLocalId()).as("no local row, deliberately — the same answer as for a clinician").isNull();
        assertThat(link.getLastEventType())
            .as("the activation still lands on the link; it simply may not create")
            .isEqualTo("AccountActivated");
    }

    /**
     * The reconciliation must not undo it either.
     *
     * <p>It walks links whose local record is missing and rebuilds them, and a care angel's link is
     * missing one <em>by design</em> — so without the kind stored on the document, one press of
     * {@code /reconcile} would recreate every patient the consumer had just refused to create. That
     * is the whole reason {@code subject_kind} is persisted rather than re-derived: the reconciliation
     * has no event in front of it to read the authorities out of.
     */
    @Test
    void reconcilingDoesNotResurrectACareAngelAsAPatient() {
        sendPatient(careAngelNominated("2026-09-01T08:00:00Z"));

        projection.reconcile();

        assertThat(patientRepository.count()).as("the backfill path must refuse exactly what the consumer refuses").isZero();
    }

    /** An angel who later registers or onboards in their own right is a patient, and becomes one. */
    @Test
    void anAngelWhoLaterOnboardsBecomesAPatient() {
        sendPatient(careAngelNominated("2026-09-01T08:00:00Z"));
        sendPatient(onboardingStarted("2026-09-05T10:00:00Z", "p-9999"));

        assertThat(patientRepository.count()).isEqualTo(1);
        DirectoryLink link = link(DirectorySource.HC_PATIENT, EMAIL).orElseThrow();
        assertThat(link.getSubjectKind())
            .as("CARE_ANGEL is promoted to PATIENT, never the reverse")
            .isEqualTo(DirectorySubjectKind.PATIENT);
        assertThat(link.getLocalId()).isEqualTo(patientRepository.findAll().get(0).getId());
    }

    /**
     * <b>The erasure, which is the sharpest case on the stream.</b>
     *
     * <p>{@code DeletionRequestChanged/COMPLETED} is published <em>after</em> hc-patient has erased
     * the profile — its own javadoc says the address has to be read off the stored request because
     * there is nothing left to look it up on, and that "a consumer must not try to resolve the
     * patient". With no type filter on the creation path, that event was what made this service start
     * storing somebody's address and open a nameless directory row for them: the one frame whose
     * entire meaning is "erase this person" was the one that made hc-admin remember them.
     */
    @Test
    void anErasureForAnUnknownSubjectStoresNothingAtAll() {
        sendPatient(deletionRequestChanged("2026-09-01T12:00:00Z", "COMPLETED"));

        assertThat(directoryLinkRepository.count())
            .as("the event that says a person has been erased may not be the event that starts storing them")
            .isZero();
        assertThat(patientRepository.count()).isZero();
    }

    /**
     * For a subject already known, the erasure marks the link and leaves the record alone.
     *
     * <p>Neither is deleted, and each for its own reason. The {@code Patient} is an administrator's
     * record with an operational history the far side knows nothing about — the merge rule has said
     * from the start that a deletion there is not a deletion here. The <b>link</b> is not deleted
     * because it holds the watermark: dropping it would let the whole of that subject's retained
     * history replay into a fresh link on the next backfill and undo the marker, which is exactly the
     * property that makes deletion the one operation that is not idempotent against a replayed stream.
     */
    @Test
    void anErasureMarksTheLinkAndRemovesNothing() {
        sendPatient(accountCreated("2026-09-01T08:00:00Z", false));
        sendPatient(onboardingStarted("2026-09-01T10:00:00Z", "p-1234"));
        String patientId = patientRepository.findAll().get(0).getId();

        sendPatient(deletionRequestChanged("2026-09-01T12:00:00Z", "COMPLETED"));

        DirectoryLink link = link(DirectorySource.HC_PATIENT, EMAIL).orElseThrow();
        assertThat(link.getErasedAt()).isEqualTo(Instant.parse("2026-09-01T12:00:00Z"));
        assertThat(link.getExternalId()).as("the patientId is a handle into a record that no longer exists").isNull();
        assertThat(link.getLogin()).isNull();
        assertThat(patientRepository.findById(patientId)).as("hc-admin's own record is not deleted by an event, ever").isPresent();

        // And the reconciliation does not rebuild a record for somebody the far side has erased.
        patientRepository.deleteById(patientId);
        projection.reconcile();
        assertThat(patientRepository.count()).isZero();
    }

    /**
     * A type neither producer has published creates nothing.
     *
     * <p>Both producers state the contract in their own javadoc — <em>a consumer meeting something it
     * does not recognise must ignore it</em> — and until 2026-09-05 this consumer did the opposite:
     * any type at all, on a frame with a resolvable subject key, opened a {@code Patient}.
     */
    @Test
    void aTypeThisServiceDoesNotModelCreatesNothing() {
        sendPatient(
            "{\"eventId\":\"evt-x\",\"type\":\"SomethingAddedNextYear\",\"version\":1," +
            "\"occurredAt\":\"2026-09-01T08:00:00Z\",\"source\":\"hcPatientService\",\"subject\":{\"email\":\"" +
            EMAIL +
            "\"},\"data\":{}}"
        );

        assertThat(patientRepository.count()).isZero();
        assertThat(directoryLinkRepository.count()).as("nor a link — this service has no idea who or what that frame is about").isZero();
    }

    /**
     * An {@code UPDATE_ONLY} event for a subject whose arrival was missed writes nothing.
     *
     * <p>A record invented from an onboarding step or a delegation change is a row nothing can ever
     * complete: the events that carry identity are the ones that open a record, and this is not one
     * of them. It is also what stops a topic gap — a retention window rolling over the account events
     * while keeping the later ones — from producing a directory of half-people.
     */
    @Test
    void anUpdateOnlyEventForAnUnknownSubjectWritesNothing() {
        sendPatient(accountActivated("2026-09-01T09:00:00Z"));

        assertThat(directoryLinkRepository.count()).isZero();
        assertThat(patientRepository.count()).isZero();
    }

    /**
     * What {@code apply} answers, which the consumer now logs and nothing used to read.
     *
     * <p>{@code Outcome} was dead: {@code handle} discarded it, so the enum described behaviour rather
     * than reporting it — and it was wrong about one case, answering {@code IGNORED} for a
     * clinician's first sighting even though it had created a link. {@code IGNORED} means nothing was
     * written, and a reader chasing "why did that registration not appear" needs those two apart.
     */
    @Test
    void sayingWhatItDidDistinguishesLinkingFromIgnoring() {
        assertThat(apply(parser.parseProfessionalEvent(bytes(registrationCreated()))))
            .as("a clinician's first sighting writes a link — deliberately no local row, but not nothing")
            .isEqualTo(DirectoryProjectionService.Outcome.LINKED);
        assertThat(apply(parser.parseProfessionalEvent(bytes(onboardingState("COMPLETED")))))
            .isEqualTo(DirectoryProjectionService.Outcome.UPDATED);

        assertThat(apply(parser.parsePatientEvent(bytes(accountCreated("2026-09-01T08:00:00Z", false)), EMAIL)))
            .isEqualTo(DirectoryProjectionService.Outcome.CREATED);
        assertThat(apply(parser.parsePatientEvent(bytes(careAngelNominated("2026-09-02T08:00:00Z")), "angel@example.com")))
            .as("a nomination is linked, not ignored — the link is what stops the activation behind it creating one")
            .isEqualTo(DirectoryProjectionService.Outcome.LINKED);
        assertThat(apply(parser.parsePatientEvent(bytes(accountActivated("2026-09-01T09:00:00Z")), "nobody@example.com")))
            .as("an update for a subject that was never seen writes nothing at all")
            .isEqualTo(DirectoryProjectionService.Outcome.IGNORED);
    }

    private DirectoryProjectionService.Outcome apply(Optional<SiblingDomainEvent> event) {
        return projection.apply(event.orElseThrow());
    }

    private static byte[] bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * <b>A patient chooses a tier on hc-patient and it reaches the row this service already holds.</b>
     *
     * <p>Backlog item 48, end to end within this service: their {@code MembershipResource} publishes
     * {@code PlanChosen} on {@code patient-events} after writing a {@code Membership}, and the four
     * fields it carries land on the {@code DirectoryLink} for that patient.
     *
     * <p><b>{@code Patient.plan} is deliberately not written</b>, and that is the assertion worth
     * having rather than the four that precede it. That field is an administrator's — one of the
     * seven this class's merge rule says no event may touch, because hc-patient does not know this
     * service's plans exist — and writing it here would let a patient set their own plan on the
     * console's directory, on the plan-mix chart and in the dashboard's monthly revenue by pressing a
     * button on another product. The choice is a <em>request</em>: {@code PENDING} for anybody but an
     * administrator on their side.
     */
    @Test
    void aPlanChoiceLandsOnTheLinkAndNeverOnThePatientRecord() {
        sendPatient(accountCreated("2026-09-01T08:00:00Z", true));
        Patient patient = patientRepository.findAll().get(0);

        sendPatient(planChosen("2026-09-02T10:11:12Z", "PAWPAW", "PENDING"));

        DirectoryLink link = link(DirectorySource.HC_PATIENT, EMAIL).orElseThrow();
        assertThat(link.getPlanMembershipId()).isEqualTo("mem-991");
        assertThat(link.getPlanCode()).isEqualTo("PAWPAW");
        assertThat(link.getPlanName()).isEqualTo("PAWPAW Plan");
        assertThat(link.getPlanStatus()).isEqualTo("PENDING");
        assertThat(link.getLocalId()).as("the row this choice is about is the one the account event opened").isEqualTo(patient.getId());
        assertThat(link.getLastEventType()).isEqualTo("PlanChosen");

        assertThat(patientRepository.count()).as("a plan choice is never a second person").isEqualTo(1);
        assertThat(patientRepository.findById(patient.getId()).orElseThrow().getPlan())
            .as("the plan an administrator sets is hc-admin's field and no event may write it")
            .isNull();
    }

    /**
     * <b>A plan choice for a patient this service has never seen writes nothing at all.</b>
     *
     * <p>{@code UPDATE_ONLY}, and of the strictest kind. hc-patient writes a {@code Membership} only
     * for a patient whose {@code AccountCreated} and {@code OnboardingStarted} were published earlier
     * on the same key — and therefore the same partition, in order — so a plan choice arriving for an
     * unknown subject means those events were missed, not that somebody new exists. A tier code is
     * not a person: a record opened from one would be a patient on every dashboard tile whose entire
     * content is which plan they picked.
     */
    @Test
    void aPlanChoiceForAnUnknownSubjectOpensNothing() {
        sendPatient(planChosen("2026-09-02T10:11:12Z", "PAWPAW", "PENDING"));

        assertThat(directoryLinkRepository.count()).as("no link is opened by a fact about somebody whose arrival was missed").isZero();
        assertThat(patientRepository.count()).isZero();
    }

    /**
     * <b>A second membership that names no tier does not inherit the first one's.</b>
     *
     * <p>The defect this closes was live until the item 48 review and is worth stating as it was
     * found. The four plan fields were written with {@code setIfPresent}, one at a time, under a
     * comment claiming <em>"all four arrive together or none does"</em> and citing hc-patient's
     * {@code containsOnlyKeys} assertion. <b>That assertion pins the key set, not the values.</b>
     * {@code Membership.plan} and {@code Membership.name} carry no {@code @NotNull} and
     * {@code announceChosenPlan} puts them on the event unconditionally, so an administrator creating
     * a membership through their CRUD path with no tier named publishes both keys with nulls under
     * them — a path their own javadoc warns about by name: <em>"the administrative CRUD path is the
     * exception, and a consumer should not generalise from the sentence above."</em>
     *
     * <p>Field by field, the second frame then wrote the new {@code membershipId} and {@code status}
     * and <b>kept the first frame's tier</b>, so the console showed a membership against a tier
     * nobody had chosen for it — item 45's plausible-wrong-answer defect on the one field this whole
     * panel exists to show, and completely invisible: nothing fails, the row renders, the count is
     * right.
     *
     * <p>So the group moves together. Asserted on the tier being <em>gone</em> rather than on the new
     * membership id being present, because the id was always written correctly — it is the stale tier
     * beside it that was the lie.
     */
    @Test
    void aSecondMembershipNamingNoTierDoesNotInheritTheFirstOnes() {
        sendPatient(accountCreated("2026-09-01T08:00:00Z", true));
        sendPatient(planChosen("2026-09-02T09:00:00Z", "PAWPAW", "PENDING"));

        assertThat(link(DirectorySource.HC_PATIENT, EMAIL).orElseThrow().getPlanCode()).isEqualTo("PAWPAW");

        // Their admin CRUD path: a membership with a real id and status and no tier on it at all.
        sendPatient(planChosenWithNoTier("2026-09-03T09:00:00Z", "mem-admin-7", "PENDING"));

        DirectoryLink link = link(DirectorySource.HC_PATIENT, EMAIL).orElseThrow();
        assertThat(link.getPlanMembershipId()).isEqualTo("mem-admin-7");
        assertThat(link.getPlanStatus()).isEqualTo("PENDING");
        assertThat(link.getPlanCode()).as("the previous membership's tier must not survive onto this one").isNull();
        assertThat(link.getPlanName()).as("nor its name").isNull();
    }

    /**
     * A frame naming no membership at all describes nothing, and touches nothing.
     *
     * <p>The group is keyed on {@code membershipId} rather than on the tier, so a malformed frame
     * cannot wipe a good row. Not a path either of hc-patient's clients can reach — the id is read off
     * the saved document — which is exactly why it is asserted rather than assumed: an unreachable
     * guard that silently stopped guarding would look identical.
     */
    @Test
    void aPlanFrameNamingNoMembershipLeavesTheStoredChoiceAlone() {
        sendPatient(accountCreated("2026-09-01T08:00:00Z", true));
        sendPatient(planChosen("2026-09-02T09:00:00Z", "PAWPAW", "PENDING"));

        sendPatient(planChosenWithNoTier("2026-09-03T09:00:00Z", null, "PENDING"));

        DirectoryLink link = link(DirectorySource.HC_PATIENT, EMAIL).orElseThrow();
        assertThat(link.getPlanCode()).isEqualTo("PAWPAW");
        assertThat(link.getPlanMembershipId()).isEqualTo("mem-991");
    }

    /**
     * A replayed older choice does not wind the tier back, which the shared watermark already does.
     *
     * <p><b>And that is why there is no second watermark for the plan.</b> Phase 2 of the professional
     * contract has {@code profile_event_at} because it comes from a different application on a
     * different topic with its own clock; a plan choice comes from the same producer, on the same
     * topic, under the same key as every other patient event, so {@code last_event_at} orders it
     * against all of them and a field of its own could never disagree with one.
     */
    @Test
    void aReplayedPlanChoiceDoesNotWindTheTierBack() {
        sendPatient(accountCreated("2026-09-01T08:00:00Z", true));
        sendPatient(planChosen("2026-09-05T09:00:00Z", "MELON", "PENDING"));
        sendPatient(planChosen("2026-09-02T09:00:00Z", "PEAR", "PENDING"));

        assertThat(link(DirectorySource.HC_PATIENT, EMAIL).orElseThrow().getPlanCode())
            .as("the older frame is behind the watermark and is not applied")
            .isEqualTo("MELON");
    }

    /**
     * <b>A tier this catalogue does not hold is stored, shown, and said out loud.</b>
     *
     * <p>Backlog item 51 predicted this exact failure before either half was built: hc-patient
     * publishes {@code PEAR} / {@code PAWPAW} / {@code MELON}, and while this service's catalogue held
     * different codes <em>"the event will arrive, parse, and resolve to nothing — and nothing will
     * say so … a healthy service, a consumer group with no lag, and a screen that is simply wrong."</em>
     * Item 51 has since made {@code ServicePlan.code} the same vocabulary, so the ordinary case
     * resolves; what remains is a tier Abofonsa has published and
     * {@code ServicePlanCatalogueSyncService} has not brought across yet, and this is the case that
     * has to be audible rather than silent.
     *
     * <p><b>Both branches are driven, and asserting only the warning would be half a test.</b> A
     * class that logged the warning for every choice would satisfy a one-sided assertion and would be
     * an alarm that is always on, which is exactly as useless as one that never fires.
     *
     * <p>Nothing creates a {@code ServicePlan} for the unknown code — asserted, because that is the
     * fourth restatement of a price list item 51 exists to have stopped.
     */
    @Test
    void announcesAPlanChoiceAndSaysWhenTheTierIsNotInThisCatalogue() {
        servicePlanRepository.save(
            new ServicePlan().name("PAWPAW Plan").code("PAWPAW").currency("GHS").featured(false).monthlyPrice(new BigDecimal("5000"))
        );
        sendPatient(accountCreated("2026-09-01T08:00:00Z", true));

        Logger logger = (Logger) LoggerFactory.getLogger("net.jojoaddison");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            sendPatient(planChosen("2026-09-02T09:00:00Z", "PAWPAW", "PENDING"));
            sendPatient(planChosen("2026-09-03T09:00:00Z", "SOURSOP", "PENDING"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(appender.list)
            .filteredOn(event -> event.getLevel() == Level.INFO)
            .as("a tier this catalogue holds is announced and not warned about")
            .anySatisfy(event -> assertThat(event.getFormattedMessage()).contains("chose PAWPAW"));

        assertThat(appender.list)
            .filteredOn(event -> event.getLevel() == Level.WARN)
            .as("a tier it does not hold is the one an operator has to be told about")
            .anySatisfy(event -> assertThat(event.getFormattedMessage()).contains("SOURSOP").contains("not a code in this catalogue"));

        assertThat(servicePlanRepository.findOneByCode("SOURSOP"))
            .as("no ServicePlan is invented from a tier code — that is the price list item 51 removed")
            .isEmpty();
        assertThat(link(DirectorySource.HC_PATIENT, EMAIL).orElseThrow().getPlanCode())
            .as("the choice is still stored: the back-office prompt is not lost over an unsynced tier")
            .isEqualTo("SOURSOP");
    }

    /** One subject on each stream is two links, never one — the sources are separate key spaces. */
    @Test
    void thetwoStreamsDoNotShareASubjectKeySpace() {
        sendPatient(accountCreated("2026-09-01T08:00:00Z", false));
        sendProfessional(registrationCreated());

        assertThat(directoryLinkRepository.count()).isEqualTo(2);
        assertThat(link(DirectorySource.HC_PATIENT, EMAIL)).isPresent();
        assertThat(link(DirectorySource.HC_PROFESSIONAL, ACCOUNT_ID)).isPresent();
    }

    /**
     * <b>A patient's email address reaches no log line, at any level.</b>
     *
     * <p>{@code subjectKey} is a lowercased email for every patient, and until 2026-09-07
     * {@code DirectoryProjectionService} logged it verbatim on the creation path at {@code INFO} —
     * which is the level production runs this package at. It was not confined to the host, either:
     * the line was found in Loki on the production stack, arriving by two independent paths (the
     * OpenTelemetry agent's log export and Alloy's container scrape) into a fourteen-day store shared
     * across six products. Backlog item 43.
     *
     * <p><b>The logger is turned up to TRACE, and that is the point of the test rather than
     * thoroughness.</b> Most of the statements are {@code debug} and would have been argued as
     * unreachable in production; they are one {@code POST /management/loggers/net.jojoaddison} away,
     * a screen in the console drives exactly that, and the person pressing it is by definition
     * debugging why a subject did not appear — the one moment when every subject on the topic gets
     * logged rather than only the new ones.
     *
     * <p>It asserts the digest is <b>present</b> as well as the address absent, deliberately. The way
     * this fix goes wrong is not somebody re-adding the email; it is somebody deleting the subject
     * from the line altogether, leaving an operator holding an address with nothing to search for.
     */
    @Test
    void noLogLineCarriesTheSubjectKey() {
        Logger logger = (Logger) LoggerFactory.getLogger("net.jojoaddison");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Level original = logger.getLevel();
        logger.setLevel(Level.TRACE);
        logger.addAppender(appender);

        try {
            // Every disposition, so that all four reachable statements fire: an update for a subject
            // with no link yet, a creation, an update, and one behind the watermark.
            sendPatient(deletionRequestChanged("2026-09-01T07:00:00Z", "REQUESTED"));
            sendPatient(accountCreated("2026-09-01T08:00:00Z", false));
            sendPatient(accountActivated("2026-09-01T09:00:00Z"));
            sendPatient(accountActivated("2026-09-01T07:30:00Z"));
            // And the LINK_ONLY disposition, whose announcement is new (backlog item 46). A
            // clinician's key is an accountId rather than an address, and it goes through the same
            // digest — a per-source exception here would be a rule nobody could apply.
            sendProfessional(registrationCreated());
            // And both of item 48's plan-choice statements, which name a subject beside a tier code.
            // The tier is NOT digested and must not be — PAWPAW names a product, not a person, and a
            // hash of it would make the line unable to say which tier is missing — so this case is
            // where that distinction is held: the code may be rendered, the address may not.
            sendPatient(planChosen("2026-09-02T09:00:00Z", "PAWPAW", "PENDING"));
            sendPatient(planChosen("2026-09-03T09:00:00Z", "SOURSOP", "PENDING"));

            // What a loaded host does by accident, done on purpose. The test containers relay
            // mongod's stdout through a logger in this package, and its "Slow query" line quotes the
            // whole command — external_key and all — so this test failed under load and passed on an
            // idle machine, which is a flake shaped exactly like the regression it guards against.
            // Emitting the line here rather than waiting for a slow query is what makes
            // thisServicesOwnStatements' exclusion provable: delete that filter and this case goes
            // red every run instead of one run in ten.
            LoggerFactory.getLogger(MongoDbTestContainer.class).info("STDOUT: {{\"external_key\":\"{}\"}}", EMAIL);
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(original);
            appender.stop();
        }

        List<ILoggingEvent> statements = thisServicesOwnStatements(appender.list);

        assertThat(statements).as("nothing was logged at all — this test would then prove nothing").isNotEmpty();

        assertThat(statements)
            .as("no statement in this package may render a patient's address, at any level")
            .noneSatisfy(event -> assertThat(event.getFormattedMessage()).contains(EMAIL));

        // The local part alone, in case a future line renders the key in pieces or masks the domain.
        assertThat(statements).noneSatisfy(event -> assertThat(event.getFormattedMessage()).contains("ama.mensah"));

        assertThat(statements)
            .as("nor a clinician's correlation key, which is an accountId and reaches the same lines")
            .noneSatisfy(event -> assertThat(event.getFormattedMessage()).contains(ACCOUNT_ID));

        assertThat(statements)
            .as("the subject must still be nameable, or an operator holding the address has no query")
            .anySatisfy(event -> assertThat(event.getFormattedMessage()).contains(LogPseudonym.subject(EMAIL)));
    }

    /**
     * The events this service actually wrote, without the test containers' relayed output.
     *
     * <h2>Why this filter exists, and why it is not a hole in the rule</h2>
     *
     * <p>{@code MongoDbTestContainer} and {@code KafkaTestContainer} live in this package and pipe
     * <b>another process's stdout</b> through a logger of their own at {@code INFO} — so mongod's own
     * {@code "Slow query"} lines land in this appender, and one of those quotes the whole command,
     * {@code external_key} and all. The rule this test states is about statements <em>this service</em>
     * writes; a database echoing a query back on a test host is not one, cannot occur in production,
     * and is not something the production logger configuration can reach.
     *
     * <p><b>Found by running the suite on a loaded host, and it had been failing intermittently
     * before this change touched the file.</b> A slow query only gets logged when a query is slow, so
     * the test passed on an idle machine and failed under load — the worst shape of flake, because it
     * looks like the security regression it exists to catch. Backlog item 46's review section.
     *
     * <p><b>Excluded by logger name and not by content.</b> Filtering out lines that <em>contain</em>
     * the address would delete the assertion; filtering by the two classes that relay somebody else's
     * output keeps it whole. The suffix rather than two literal names, so a third container added
     * later is covered — {@code PaginationIT}'s reasoning about enumerations, one test along.
     *
     * <p><b>The package is checked as well as the suffix, since 2026-09-07.</b> The suffix alone also
     * matches a <em>main</em> class called anything{@code TestContainer} — nothing is named that
     * today, and a rule that silently widens the day one is would be this test quietly excusing a
     * production logger from the only guard it has. {@code net.jojoaddison.config} is where both
     * container classes live and where a third would go; a container added somewhere else fails this
     * test loudly, which is the right way round.
     */
    private static List<ILoggingEvent> thisServicesOwnStatements(List<ILoggingEvent> captured) {
        return captured
            .stream()
            .filter(event ->
                !(event.getLoggerName().startsWith("net.jojoaddison.config.") && event.getLoggerName().endsWith("TestContainer"))
            )
            .toList();
    }

    // --- driving the bindings ---------------------------------------------------------------------

    private void sendPatient(String json) {
        // The patientKey header is what hc-patient sets as the partition key; sending it here means
        // the consumer is exercised the way the broker will actually deliver.
        send(
            PATIENT_TOPIC,
            MessageBuilder
                .withPayload(json.getBytes(StandardCharsets.UTF_8))
                .setHeader(DirectoryEventConsumers.PATIENT_KEY_HEADER, EMAIL)
                .build()
        );
    }

    /**
     * Sends, and translates the one failure that says nothing useful.
     *
     * <p>Unbinding a consumer — dropping it from {@code spring.cloud.function.definition}, which is
     * the exact shape of the production defect this class was written for — leaves no channel for
     * the destination, and {@code InputDestination.send} then fails with
     * {@code NullPointerException: Cannot invoke "SubscribableChannel.send(Message)" because the
     * return value of "InputDestination.getChannelByName(String)" is null}. That is a true failure
     * and a nearly useless one: it names the test harness, not the subscription. <b>Measured, not
     * guessed — the subscription was broken deliberately on 2026-09-04 and that is the message it
     * gave.</b>
     *
     * <p>Checked by catching rather than by asking first, because {@code getChannelByName} is not
     * public outside the binder's own package. Only {@code NullPointerException} is translated, so a
     * genuine failure inside a consumer still surfaces as itself.
     */
    private void send(String topic, Message<byte[]> message) {
        try {
            input.send(message, topic);
        } catch (NullPointerException e) {
            throw new AssertionError(
                "nothing is bound to " +
                topic +
                " — no consumer function is subscribed to it, so the message cannot be delivered and " +
                "this service would learn nothing from that stream. Check that the function is named in " +
                "spring.cloud.function.definition and that the binding declares this destination.",
                e
            );
        }
    }

    private void sendProfessional(String json) {
        send(PROFESSIONAL_TOPIC, MessageBuilder.withPayload(json.getBytes(StandardCharsets.UTF_8)).build());
    }

    /**
     * Phase 2, on the api's own topic — a <b>third</b> binding, and the one likeliest to be shipped
     * silent.
     *
     * <p>If {@code professionalProfileConsumer} is missing from
     * {@code spring.cloud.function.definition}, or the binding names no destination, this send finds
     * no channel and {@link #send} turns the binder's {@code NullPointerException} into a sentence
     * naming the topic. That is the whole reason these cases drive the binding rather than calling
     * the projection: a configured-but-unsubscribed consumer and a working one are identical from
     * every other test in this suite.
     */
    private void sendProfessionalEntity(String json) {
        send(PROFESSIONAL_ENTITY_TOPIC, MessageBuilder.withPayload(json.getBytes(StandardCharsets.UTF_8)).build());
    }

    private Optional<DirectoryLink> link(DirectorySource source, String key) {
        return directoryLinkRepository.findSubject(source, key);
    }

    // --- the wire formats, copied from the two publishers -----------------------------------------

    private static String accountCreated(String occurredAt, boolean activated) {
        return (
            "{\"eventId\":\"evt-created\",\"type\":\"AccountCreated\",\"version\":1,\"occurredAt\":\"" +
            occurredAt +
            "\",\"source\":\"patientGateway\",\"subject\":{\"email\":\"" +
            EMAIL +
            "\",\"login\":\"amensah\",\"patientId\":null}," +
            "\"data\":{\"authorities\":\"ROLE_USER\",\"langKey\":\"en\",\"activated\":" +
            activated +
            "}}"
        );
    }

    /**
     * hc-patient's {@code MembershipResource.announceChosenPlan}, key for key.
     *
     * <p>{@code planCode} is their {@code Membership.plan} and {@code planName} is their
     * {@code Membership.name} — their document has no {@code code} field, and both of their clients'
     * {@code choosePlan} writes the tier's code into {@code plan}. The status travels as
     * {@code MembershipStatus.name()} rather than as the enum, by their own decision.
     */
    private static String planChosen(String occurredAt, String planCode, String status) {
        return (
            "{\"eventId\":\"evt-plan-" +
            planCode +
            "\",\"type\":\"PlanChosen\",\"version\":1,\"occurredAt\":\"" +
            occurredAt +
            "\",\"source\":\"hcPatientService\",\"subject\":{\"email\":\"" +
            EMAIL +
            "\",\"login\":null,\"patientId\":\"p-1234\"}," +
            "\"data\":{\"membershipId\":\"mem-991\",\"planCode\":\"" +
            planCode +
            "\",\"planName\":\"" +
            planCode +
            " Plan\",\"status\":\"" +
            status +
            "\"}}"
        );
    }

    /**
     * The same event with the tier keys present and null, which is what their administrative CRUD
     * path publishes for a membership created with no plan on it.
     *
     * <p>Written out rather than parameterised off {@link #planChosen} so the nulls are visible as
     * JSON literals: this is the shape the group write exists for, and a reader has to be able to see
     * that the keys are there and empty rather than missing.
     */
    private static String planChosenWithNoTier(String occurredAt, String membershipId, String status) {
        return (
            "{\"eventId\":\"evt-plan-none\",\"type\":\"PlanChosen\",\"version\":1,\"occurredAt\":\"" +
            occurredAt +
            "\",\"source\":\"hcPatientService\",\"subject\":{\"email\":\"" +
            EMAIL +
            "\",\"login\":null,\"patientId\":\"p-1234\"}," +
            "\"data\":{\"membershipId\":" +
            (membershipId == null ? "null" : "\"" + membershipId + "\"") +
            ",\"planCode\":null,\"planName\":null,\"status\":\"" +
            status +
            "\"}}"
        );
    }

    private static String accountActivated(String occurredAt) {
        return (
            "{\"eventId\":\"evt-activated\",\"type\":\"AccountActivated\",\"version\":1,\"occurredAt\":\"" +
            occurredAt +
            "\",\"source\":\"patientGateway\",\"subject\":{\"email\":\"" +
            EMAIL +
            "\",\"login\":\"amensah\",\"patientId\":null},\"data\":{\"activatedAt\":\"" +
            occurredAt +
            "\"}}"
        );
    }

    private static String onboardingStarted(String occurredAt, String patientId) {
        return (
            "{\"eventId\":\"evt-onboarding\",\"type\":\"OnboardingStarted\",\"version\":1,\"occurredAt\":\"" +
            occurredAt +
            "\",\"source\":\"hcPatientService\",\"subject\":{\"email\":\"" +
            EMAIL +
            "\",\"login\":null,\"patientId\":\"" +
            patientId +
            "\"},\"data\":{\"startedAt\":\"" +
            occurredAt +
            "\"}}"
        );
    }

    /** hc-patient's {@code CareAngelResource.nominate}, first frame — keyed on the ANGEL's address. */
    private static String careAngelNominated(String occurredAt) {
        return (
            "{\"eventId\":\"evt-angel\",\"type\":\"AccountCreated\",\"version\":1,\"occurredAt\":\"" +
            occurredAt +
            "\",\"source\":\"patientGateway\",\"subject\":{\"email\":\"" +
            EMAIL +
            "\",\"login\":\"aangel\",\"patientId\":null}," +
            "\"data\":{\"authorities\":\"ROLE_USER,ROLE_ANGEL\",\"activated\":true,\"reason\":\"careAngelNomination\"}}"
        );
    }

    /** {@code DeletionRequestService.announce}. {@code COMPLETED} is published after the erasure. */
    private static String deletionRequestChanged(String occurredAt, String change) {
        return (
            "{\"eventId\":\"evt-deletion\",\"type\":\"DeletionRequestChanged\",\"version\":1,\"occurredAt\":\"" +
            occurredAt +
            "\",\"source\":\"hcPatientService\",\"subject\":{\"email\":\"" +
            EMAIL +
            "\",\"login\":\"amensah\",\"patientId\":\"p-1234\"}," +
            "\"data\":{\"requestId\":\"req-1\",\"change\":\"" +
            change +
            "\"}}"
        );
    }

    private static String registrationCreated() {
        return (
            "{\"eventId\":\"evt-registration\",\"eventType\":\"registration.created\",\"occurredAt\":\"2026-09-01T08:00:00Z\"," +
            "\"source\":\"hc-professional-gateway\",\"actor\":\"anonymous\",\"payload\":{\"accountId\":\"" +
            ACCOUNT_ID +
            "\",\"login\":\"kboateng\",\"email\":\"k.boateng@example.com\",\"langKey\":\"en\",\"origin\":\"self-service\"}}"
        );
    }

    /**
     * <b>Phase 1's {@code AccountCreated}, in the estate-shaped envelope their gateway actually
     * sends</b> — {@code type} and {@code data}, with the subject beside the body.
     *
     * <p>It replaced a fabricated {@code registration.created} carrying {@code activated} and two
     * account dates, which hc-professional publishes on no frame. The dates are absent here because
     * they are absent there, so a test asserting them would be asserting the fixture.
     */
    private static String accountCreatedOnProfessional(Boolean activated) {
        return (
            "{\"eventId\":\"evt-account-created\",\"type\":\"AccountCreated\",\"version\":1," +
            "\"occurredAt\":\"2026-09-01T08:00:00Z\",\"source\":\"hc-professional-gateway\"," +
            "\"subject\":{\"email\":\"k.boateng@example.com\",\"login\":\"kboateng\",\"accountId\":\"" +
            ACCOUNT_ID +
            "\"},\"data\":{\"authorities\":\"ROLE_USER\",\"langKey\":\"en\"" +
            (activated == null ? "" : ",\"activated\":" + activated) +
            "}}"
        );
    }

    /** Phase 1's {@code AccountActivated}, which carries no state and must not clear the one stored. */
    private static String accountActivatedOnProfessional() {
        return (
            "{\"eventId\":\"evt-account-activated\",\"type\":\"AccountActivated\",\"version\":1," +
            "\"occurredAt\":\"2026-09-01T12:00:00Z\",\"source\":\"hc-professional-gateway\"," +
            "\"subject\":{\"email\":\"k.boateng@example.com\",\"login\":\"kboateng\",\"accountId\":\"" +
            ACCOUNT_ID +
            "\"},\"data\":{\"activatedAt\":\"2026-09-01T12:00:00Z\"}}"
        );
    }

    /**
     * Phase 2: the profile status, keyed on the same accountId, on hc-professional's entity topic —
     * in {@code ProfessionalEvent}, which is the envelope their {@code publishProfileStatus} builds.
     */
    private static String profileStatus(String accountId, String occurredAt, boolean complete, boolean verified) {
        return (
            "{\"eventId\":\"evt-profile-status\",\"type\":\"ProfileStatus\",\"version\":1,\"occurredAt\":\"" +
            occurredAt +
            "\",\"source\":\"hc-professional-service\"," +
            "\"subject\":{\"email\":null,\"login\":null,\"accountId\":\"" +
            accountId +
            "\"},\"data\":{\"profileId\":\"prof-9\",\"accountId\":\"" +
            accountId +
            "\",\"isComplete\":" +
            complete +
            ",\"isVerified\":" +
            verified +
            ",\"createdDate\":\"2026-08-19T11:30:00Z\",\"modifiedDate\":\"2026-09-02T14:47:00Z\"," +
            "\"lastModifiedBy\":\"yasante\"}}"
        );
    }

    /**
     * A legacy {@code entity.created} for a {@code Profile}, in their older envelope — real, already
     * on the topic, and refused by type since the architect's decision 3.
     */
    private static String legacyProfileEntityCreated() {
        return (
            "{\"eventId\":\"evt-legacy-profile\",\"eventType\":\"entity.created\",\"occurredAt\":\"2026-09-02T14:47:05Z\"," +
            "\"source\":\"hc-professional-service\",\"actor\":\"admin\",\"payload\":{\"entityType\":\"Profile\"," +
            "\"entityId\":\"prof-9\",\"accountId\":\"kboateng\"}}"
        );
    }

    /** A Task on the same topic — 82% of it, and none of this service's business. */
    private static String taskCreated() {
        return (
            "{\"eventId\":\"evt-task\",\"eventType\":\"entity.created\",\"occurredAt\":\"2026-09-02T14:47:05Z\"," +
            "\"source\":\"hc-professional-service\",\"actor\":\"admin\",\"payload\":{\"entityType\":\"Task\"," +
            "\"entityId\":\"task-1\",\"accountId\":\"" +
            ACCOUNT_ID +
            "\"}}"
        );
    }

    private static String onboardingState(String state) {
        return (
            "{\"eventId\":\"evt-state\",\"eventType\":\"onboarding.state\",\"occurredAt\":\"2026-09-01T11:00:00Z\"," +
            "\"source\":\"hc-professional-service\",\"actor\":\"admin\",\"payload\":{\"accountId\":\"" +
            ACCOUNT_ID +
            "\",\"state\":\"" +
            state +
            "\",\"applicationId\":\"app-1\",\"requestedRole\":\"NURSE\"}}"
        );
    }
}
