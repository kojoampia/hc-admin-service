package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.domain.enumeration.DirectorySubjectKind;
import net.jojoaddison.service.dto.ProfileStatusEvent;
import net.jojoaddison.service.dto.SiblingDomainEvent;
import net.jojoaddison.service.dto.SiblingDomainEvent.Disposition;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * The two sibling envelopes, read off the wire.
 *
 * <p>The payloads below are written out as literal JSON rather than built from a shared type,
 * because <b>there is no shared type</b> — the whole risk this class covers is that hc-patient or
 * hc-professional changes a field name and nothing in either build fails. A fixture assembled from
 * this repository's own classes would agree with itself for ever. These strings were copied from the
 * two publishers, and if the parser stops reading them the test names which field went missing.
 */
class SiblingEventParserTest {

    private static final String PATIENT_EMAIL = "ama.mensah@example.com";

    private final SiblingEventParser parser = new SiblingEventParser(new ObjectMapper());

    @Test
    void readsAPatientAccountCreatedEvent() {
        SiblingDomainEvent event = parser
            .parsePatientEvent(bytes(accountCreated(PATIENT_EMAIL, "2026-09-01T08:00:00Z", false)), null)
            .orElseThrow();

        assertThat(event.source()).isEqualTo(DirectorySource.HC_PATIENT);
        assertThat(event.type()).isEqualTo("AccountCreated");
        assertThat(event.subjectKey()).isEqualTo(PATIENT_EMAIL);
        assertThat(event.login()).isEqualTo("amensah");
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-09-01T08:00:00Z"));
        assertThat(event.activated()).as("this account has not been activated yet").isFalse();
    }

    /**
     * The header wins over the payload, because it is what the broker partitioned on.
     *
     * <p>They are the same value in practice. Asserting the precedence anyway pins which one is
     * authoritative, so a future reader does not "simplify" the fallback into the primary.
     */
    @Test
    void prefersThePartitionKeyHeaderAndFallsBackToThePayload() {
        String frame = accountCreated(PATIENT_EMAIL, "2026-09-01T08:00:00Z", false);

        assertThat(parser.parsePatientEvent(bytes(frame), "  OTHER@Example.COM ").orElseThrow().subjectKey())
            .as("the header is the partition key and is trimmed and lowercased like the producer does")
            .isEqualTo("other@example.com");
        assertThat(parser.parsePatientEvent(bytes(frame), "   ").orElseThrow().subjectKey())
            .as("a blank header falls back to subject.email")
            .isEqualTo(PATIENT_EMAIL);
    }

    @Test
    void treatsAnActivationAsEvidenceTheAccountCanSignIn() {
        assertThat(
            parser
                .parsePatientEvent(bytes(accountActivated(PATIENT_EMAIL)), null)
                .orElseThrow()
                .activated()
        ).isTrue();
        assertThat(
            parser
                .parsePatientEvent(bytes(accountCreated(PATIENT_EMAIL, "2026-09-01T08:00:00Z", true)), null)
                .orElseThrow()
                .activated()
        )
            .as("an account created already activated says so in its data")
            .isTrue();
    }

    /**
     * <b>The care-angel trap.</b> hc-patient publishes {@code AccountCreated} for a nomination too,
     * keyed on the angel's own address, with {@code ROLE_ANGEL} among the authorities and
     * {@code reason: careAngelNomination} — and then an {@code AccountActivated} straight after it,
     * because an angel's account is created already activated.
     *
     * <p>Until 2026-09-05 the parser named two types and read them only for {@code activated}, so
     * both frames went down the creation path and every nomination became an ACTIVE patient: on the
     * count, in the directory, on the account-mix chart, on the weekly-joins tile. An angel is not a
     * patient here — hc-admin has a separate {@code Angel} entity — so the nomination is a link and
     * nothing else, and the activation that follows it may only update.
     */
    @Test
    void aCareAngelNominationIsALinkAndNeverAPatient() {
        SiblingDomainEvent nomination = parser.parsePatientEvent(bytes(careAngelNominated(PATIENT_EMAIL)), null).orElseThrow();

        assertThat(nomination.disposition())
            .as("a nomination may not open a patient record; it is a link and nothing more")
            .isEqualTo(Disposition.LINK_ONLY);
        assertThat(nomination.subjectKind()).isEqualTo(DirectorySubjectKind.CARE_ANGEL);
        assertThat(nomination.activated()).as("an angel's account being activated is not a patient becoming active").isFalse();

        // The second frame the same call site emits. It carries no ROLE_ANGEL and no reason — only
        // an activatedAt — so nothing in it says who it is about. UPDATE_ONLY is what stops it.
        assertThat(
            parser
                .parsePatientEvent(bytes(accountActivated(PATIENT_EMAIL)), null)
                .orElseThrow()
                .disposition()
        )
            .as("the nomination's activation frame carries no marker at all, so it must not create either")
            .isEqualTo(Disposition.UPDATE_ONLY);
    }

    /** Either marker alone is enough; they are set at one call site and one could outlive the other. */
    @Test
    void recognisesANominationFromEitherMarkerAlone() {
        String byAuthority = accountCreated(PATIENT_EMAIL, "2026-09-01T08:00:00Z", true).replace(
            "\"authorities\":\"ROLE_USER\"",
            "\"authorities\":\"ROLE_USER,ROLE_ANGEL\""
        );
        String byReason = accountCreated(PATIENT_EMAIL, "2026-09-01T08:00:00Z", true).replace(
            "\"langKey\":\"en\"",
            "\"reason\":\"careAngelNomination\""
        );

        assertThat(parser.parsePatientEvent(bytes(byAuthority), null).orElseThrow().disposition()).isEqualTo(Disposition.LINK_ONLY);
        assertThat(parser.parsePatientEvent(bytes(byReason), null).orElseThrow().disposition()).isEqualTo(Disposition.LINK_ONLY);
    }

    /**
     * <b>All eight types hc-patient publishes, and what each may do.</b>
     *
     * <p>Enumerated from {@code PatientEventType} in that repository rather than from the frames that
     * happen to be in a topic. The entry that closed this work claimed the sweep had been done and it
     * had been done for hc-professional only: the parser named two of these and there was no
     * type filter on the creation path at all, so the other five — and every type either product adds
     * next — opened a {@code Patient}.
     *
     * <p>{@code PlanChosen} is the eighth, added on 2026-09-08 by their item 18. It arrived exactly
     * the way that paragraph predicts one will: published by them, ignored by the {@code default} on
     * this side, with nothing failing anywhere. Their own javadoc said so at the time — <em>"their
     * item 48 is open and their parser has no PlanChosen case today, so it falls to its
     * default"</em> — which is what a type filter buys and what an untyped creation path costs.
     */
    @Test
    void modelsEveryTypeHcPatientPublishes() {
        assertThat(dispositionOf("AccountCreated")).isEqualTo(Disposition.CREATE);
        assertThat(dispositionOf("OnboardingStarted"))
            .as("the one event that proves a profile exists on the far side, and the only one carrying the patientId")
            .isEqualTo(Disposition.CREATE);
        assertThat(dispositionOf("AccountActivated")).isEqualTo(Disposition.UPDATE_ONLY);
        assertThat(dispositionOf("OnboardingStepCompleted")).isEqualTo(Disposition.UPDATE_ONLY);
        assertThat(dispositionOf("OnboardingCompleted")).isEqualTo(Disposition.UPDATE_ONLY);
        assertThat(dispositionOf("CareDelegationChanged"))
            .as("keyed on the PATIENT's address, with the angel's in data — it changes a subject already known")
            .isEqualTo(Disposition.UPDATE_ONLY);
        assertThat(dispositionOf("DeletionRequestChanged")).isEqualTo(Disposition.UPDATE_ONLY);
        assertThat(dispositionOf("PlanChosen"))
            .as("a plan choice is a fact about a patient hc-patient has already announced, never a subject of its own")
            .isEqualTo(Disposition.UPDATE_ONLY);
    }

    /**
     * <b>The {@code PlanChosen} contract, written as literals on purpose.</b>
     *
     * <p>This is the half of backlog item 48 that nothing else can enforce. hc-patient's
     * {@code PatientEventType.PLAN_CHOSEN} states it — <em>"a rename here is not a compile error
     * there, it is an event their {@code switch} silently ignores"</em> — and their
     * {@code MembershipPlanEventTest} pins the same five strings from the other side, in their own
     * words because the two repositories cannot be compiled against each other. If one of these has
     * to be edited, the other repository has to be edited in the same breath.
     *
     * <p>The frame below is their envelope with their payload: type {@code "PlanChosen"}, four keys,
     * keyed on the lowercased email like every other event on that stream with the {@code patientId}
     * beside it. <b>{@code planCode} is their {@code Membership.plan} and {@code planName} is their
     * {@code Membership.name}</b> — their document has no {@code code} field at all — which is why
     * the values here are a tier code and a display name rather than two ids.
     */
    @Test
    void readsThePlanChoiceHcPatientPublishes() {
        SiblingDomainEvent event = parser.parsePatientEvent(bytes(planChosen(PATIENT_EMAIL, "PAWPAW", "PENDING")), null).orElseThrow();

        assertThat(event.type()).isEqualTo("PlanChosen");
        assertThat(event.subjectKey()).as("the lowercased email, exactly as every other event on this stream").isEqualTo(PATIENT_EMAIL);
        assertThat(event.externalId()).as("patientId rides beside the key in the subject").isEqualTo("p-1234");

        assertThat(event.planChoice()).isNotNull();
        assertThat(event.planChoice().membershipId()).isEqualTo("mem-991");
        assertThat(event.planChoice().code()).isEqualTo("PAWPAW");
        assertThat(event.planChoice().name()).isEqualTo("PAWPAW Plan");
        assertThat(event.planChoice().status())
            .as("PENDING for anybody but an administrator on their side — a request, not a subscription in force")
            .isEqualTo("PENDING");
    }

    /**
     * A tier this catalogue has never heard of is carried through unchanged, and no plan is invented.
     *
     * <p>Backlog item 51 predicted this failure before either half existed: a code that resolves to
     * nothing, on a healthy consumer group with no lag, and a screen that is simply wrong. The parser
     * is deliberately not where it is caught — dropping the frame here would make it silent in the one
     * class that could have said something — so the value survives to
     * {@code DirectoryProjectionService.announcePlanChoice}, which is where a subject can be named
     * beside it.
     */
    @Test
    void carriesATierThisCatalogueDoesNotHoldRatherThanDroppingIt() {
        SiblingDomainEvent event = parser.parsePatientEvent(bytes(planChosen(PATIENT_EMAIL, "SOURSOP", "PENDING")), null).orElseThrow();

        assertThat(event.planChoice().code()).isEqualTo("SOURSOP");
        assertThat(event.disposition()).isEqualTo(Disposition.UPDATE_ONLY);
    }

    /**
     * Every other type carries no plan choice at all, and the field is null rather than empty.
     *
     * <p>An empty {@code PlanChoice} on every frame would be indistinguishable from a choice whose
     * four fields all went missing, and {@code DirectoryProjectionService} branches on the null to
     * decide whether to write the plan fields or announce anything.
     */
    @Test
    void carriesNoPlanChoiceOnAnyOtherEvent() {
        assertThat(
            parser
                .parsePatientEvent(bytes(accountCreated(PATIENT_EMAIL, "2026-09-01T08:00:00Z", false)), null)
                .orElseThrow()
                .planChoice()
        ).isNull();
        assertThat(
            parser
                .parseProfessionalEvent(bytes(registrationCreated("acc-1")))
                .orElseThrow()
                .planChoice()
        )
            .as("a clinician has no membership tier and nothing on either of their topics carries one")
            .isNull();
        assertThat(
            parser
                .parseProfessionalEvent(bytes(accountCreatedEvent("acc-1", true)))
                .orElseThrow()
                .planChoice()
        ).isNull();
    }

    /**
     * A {@code PlanChosen} missing its payload keys is still an event, and every field reads null.
     *
     * <p>Not a case either of hc-patient's clients can reach — {@code choosePlan} always writes a plan
     * — but their administrative CRUD path accepts a membership with no plan on it, and this parser's
     * whole contract is that it never throws for a frame it cannot make sense of. Null rather than the
     * empty string, so the console's "not reported" and its "chose nothing" cannot be confused.
     */
    @Test
    void readsAPlanChoiceWithNothingInItRatherThanRefusingTheFrame() {
        SiblingDomainEvent event = parser.parsePatientEvent(bytes(typed("PlanChosen", PATIENT_EMAIL)), null).orElseThrow();

        assertThat(event.planChoice()).isNotNull();
        assertThat(event.planChoice().code()).isNull();
        assertThat(event.planChoice().name()).isNull();
        assertThat(event.planChoice().status()).isNull();
        assertThat(event.planChoice().membershipId()).isNull();
    }

    /**
     * The sharpest case on the stream, and the one an untyped creation path got exactly backwards.
     *
     * <p>{@code DeletionRequestChanged/COMPLETED} is published <em>after</em> the profile has been
     * erased — hc-patient's own javadoc says the email has to be read off the stored request because
     * the profile is already gone and "a consumer must not try to resolve the patient". So the event
     * whose entire meaning is "erase this person" was, for a subject with no link, the thing that made
     * this service start storing their address and open a nameless directory row for them.
     */
    @Test
    void marksTheCompletedErasureAndNeverOpensARecordForIt() {
        SiblingDomainEvent completed = parser.parsePatientEvent(bytes(deletionRequest(PATIENT_EMAIL, "COMPLETED")), null).orElseThrow();

        assertThat(completed.disposition()).isEqualTo(Disposition.UPDATE_ONLY);
        assertThat(completed.erased()).as("the far side has already deleted this person's profile").isTrue();

        // The other three changes are ordinary lifecycle news about somebody who still exists.
        for (String change : new String[] { "RAISED", "CANCELLED", "REJECTED" }) {
            assertThat(
                parser
                    .parsePatientEvent(bytes(deletionRequest(PATIENT_EMAIL, change)), null)
                    .orElseThrow()
                    .erased()
            )
                .as("%s is a request, not an erasure", change)
                .isFalse();
        }
    }

    /**
     * A type neither producer has published is ignored, which is the contract both of them state in
     * their own javadoc: <em>a consumer meeting something it does not recognise must ignore it.</em>
     */
    @Test
    void ignoresATypeItDoesNotModel() {
        assertThat(parser.parsePatientEvent(bytes(typed("SomethingAddedNextYear", PATIENT_EMAIL)), null))
            .as("hc-patient may add a type without reference to this consumer, and it must not become a patient")
            .isEmpty();
        assertThat(parser.parseProfessionalEvent(bytes(professionalTyped("compliance.alert"))))
            .as("hc-professional owns its topic and may add to it too")
            .isEmpty();
    }

    /** Both clinician types are links and nothing more; no {@code Professional} is ever invented. */
    @Test
    void keepsNoLocalRecordForEitherProfessionalType() {
        assertThat(
            parser
                .parseProfessionalEvent(bytes(registrationCreated("acc-1")))
                .orElseThrow()
                .disposition()
        ).isEqualTo(Disposition.LINK_ONLY);
        assertThat(
            parser
                .parseProfessionalEvent(bytes(onboardingState("acc-1", "COMPLETED")))
                .orElseThrow()
                .disposition()
        ).isEqualTo(Disposition.LINK_ONLY);
        assertThat(
            parser
                .parseProfessionalEvent(bytes(registrationCreated("acc-1")))
                .orElseThrow()
                .subjectKind()
        ).isEqualTo(DirectorySubjectKind.PROFESSIONAL);
    }

    private Disposition dispositionOf(String type) {
        return parser
            .parsePatientEvent(bytes(typed(type, PATIENT_EMAIL)), null)
            .orElseThrow()
            .disposition();
    }

    /**
     * {@code OnboardingStarted} is the one event that binds an email to a patient id, and the parser
     * has to pick it up from {@code subject.patientId} — hc-patient's envelope says in its own
     * javadoc that this is the only place the mapping is published.
     */
    @Test
    void picksUpThePatientIdWhereOnboardingPublishesIt() {
        SiblingDomainEvent event = parser.parsePatientEvent(bytes(onboardingStarted(PATIENT_EMAIL, "p-1234")), null).orElseThrow();

        assertThat(event.externalId()).isEqualTo("p-1234");
        assertThat(event.activated()).as("onboarding says nothing about whether an account can sign in").isFalse();
    }

    /**
     * <b>A registration reports no onboarding state, and must not invent one from its own type.</b>
     *
     * <p>{@code state} is the one field the console renders verbatim, on the awaiting-a-record panel
     * that backlog item 46 added. This parser fell back to the event type where the payload carried
     * no {@code state}, so a clinician who had just registered — the exact case item 46 was reported
     * for — was shown as "no record in this directory · registration.created": a wire identifier
     * printed as a status. The type is stored in its own right as {@code lastEventType}; this field
     * is the far side's own word or nothing.
     */
    @Test
    void readsAProfessionalRegistration() {
        SiblingDomainEvent event = parser.parseProfessionalEvent(bytes(registrationCreated("acc-1"))).orElseThrow();

        assertThat(event.source()).isEqualTo(DirectorySource.HC_PROFESSIONAL);
        assertThat(event.type()).isEqualTo("registration.created");
        assertThat(event.subjectKey()).as("this stream is keyed on accountId throughout, never on the email").isEqualTo("acc-1");
        assertThat(event.email()).isEqualTo("k.boateng@example.com");
        assertThat(event.login()).isEqualTo("kboateng");
        assertThat(event.state()).as("registration.created carries no state, and the type is not one").isNull();
        assertThat(event.activated())
            .as(
                "UNKNOWN, not true. This answered true until 2026-09-07, inferred from the fact that a registration " +
                    "frame had arrived — which backlog item 47 forbids in those terms: activation is the account's own " +
                    "state, and an account deactivated afterwards would never correct the guess. `activated` is on " +
                    "AccountCreated, a different frame on this same topic — see readsThePhaseOneAccountCreated — so " +
                    "this envelope answers null and the other one answers."
            )
            .isNull();
        assertThat(event.accountCreatedDate())
            .as("phase 1's createdDate is not on the wire yet either, and it is not faked from occurredAt")
            .isNull();
        assertThat(event.accountModifiedDate()).isNull();
    }

    /**
     * <b>Phase 1's {@code AccountCreated}, in hc-professional's estate-shaped envelope, on the same
     * topic as everything above.</b>
     *
     * <p>The frame this consumer <em>dropped with a {@code warn}</em> until 2026-09-08, which was the
     * defect: {@code activated} is on this event and on no other, so de-inferring it — right in itself
     * — left the console's column reading "Not reported" permanently while the fact sat one frame
     * away on the same partition. Their own publisher predicted it in its javadoc: <em>"a consumer
     * still reading only {@code eventType} will log the new frames as unreadable."</em>
     *
     * <p><b>The two account dates are asserted null, and that is the finding rather than a gap in the
     * fixture.</b> Item 47 specifies {@code createdDate} and {@code modifiedDate} as part of phase 1
     * and hc-professional's gateway publishes neither, on this frame or on any other — measured on
     * their {@code origin/main} 2026-09-08, where {@code AccountCreated}'s {@code data} is
     * {@code authorities}, {@code langKey} and {@code activated}. The obvious substitute is the
     * envelope's {@code occurredAt}, and refusing it is what this assertion pins: it would be the
     * account's creation on this one type and the frame's own clock on every other, under a column
     * heading that reads "created".
     */
    @Test
    void readsThePhaseOneAccountCreated() {
        SiblingDomainEvent event = parser.parseProfessionalEvent(bytes(accountCreatedEvent("acc-1", true))).orElseThrow();

        assertThat(event.type()).isEqualTo("AccountCreated");
        assertThat(event.subjectKey()).as("the envelope's own subject, not a field inside data").isEqualTo("acc-1");
        assertThat(event.login()).as("login, under the name the contract gives it — never `username`").isEqualTo("kboateng");
        assertThat(event.email()).isEqualTo("k.boateng@example.com");
        assertThat(event.activated()).as("read from the event, and this is the whole of item 47's rule about it").isTrue();
        assertThat(event.state()).as("an account event is not a stage of onboarding, and nothing is invented for the field").isNull();
        assertThat(event.accountCreatedDate())
            .as("their gateway publishes no createdDate on any frame, and occurredAt is deliberately not substituted")
            .isNull();
        assertThat(event.accountModifiedDate()).isNull();
    }

    /**
     * An account reported as deactivated is read as deactivated, not as unknown.
     *
     * <p>The three states have to survive the parser or nothing downstream can tell them apart:
     * {@code true}, {@code false} and "the frame did not say". A {@code Boolean} rather than a
     * {@code boolean} is what carries the third, and it is why {@code asBoolean(false)} is not used
     * anywhere in this parser.
     *
     * <p>The third case is the one the fixture had no row for until this review: a self-service
     * registration is {@code activated: false} and an administrator-created account may be
     * {@code true}, so the field is genuinely read — but an {@code AccountCreated} published before
     * hc-professional's item 47 work carries it not at all, and that has to stay distinguishable.
     */
    @Test
    void readsTheThreeActivationStatesAndNeverFlattensTheThird() {
        assertThat(
            parser
                .parseProfessionalEvent(bytes(accountCreatedEvent("acc-1", false)))
                .orElseThrow()
                .activated()
        ).isFalse();
        assertThat(
            parser
                .parseProfessionalEvent(bytes(accountCreatedEvent("acc-1", null)))
                .orElseThrow()
                .activated()
        )
            .as("nobody has said, which is not the same as saying no")
            .isNull();
    }

    /**
     * <b>{@code AccountActivated} says the account can sign in, and reading it is not the inference
     * item 47 forbids.</b>
     *
     * <p>What was forbidden, and removed on 2026-09-07, was concluding activation from <em>a
     * registration frame having arrived</em>. This is the opposite: the event's entire content is
     * that the activation link was followed and the key consumed, so refusing to read it would hold
     * "not reported" against the one frame that answers the question. hc-patient's half of this
     * parser has always read its identically-named event the same way.
     *
     * <p>It also carries no {@code state}, which is what {@code DirectoryProjectionService} relies on
     * leaving alone: this frame arrives after {@code onboarding.state} in the same keyed sequence,
     * and an unconditional write here would have cleared the console's onboarding suffix.
     */
    @Test
    void readsAnActivationOnTheProfessionalTopic() {
        SiblingDomainEvent event = parser.parseProfessionalEvent(bytes(accountActivatedEvent("acc-1"))).orElseThrow();

        assertThat(event.type()).isEqualTo("AccountActivated");
        assertThat(event.activated()).isTrue();
        assertThat(event.state()).isNull();
        assertThat(event.subjectKey()).isEqualTo("acc-1");
    }

    /**
     * <b>Both of hc-professional's envelopes are read on that one topic, and the discriminator is
     * which key is present.</b>
     *
     * <p>Their {@code RegistrationEventPublisher} puts three frames on {@code hc.professional.registration}
     * per registration under two shapes, and says the older pair is <em>"not replaced and not
     * deprecated"</em> because hc-admin is its one live consumer. So this is not a migration with a
     * cut-over: both have to keep working indefinitely, and the same clinician has to come out of
     * both under one {@code accountId} or they get two links.
     *
     * <p>An estate-shaped type this service does not model is ignored like any other, at
     * {@code debug} — the rule both producers state, and what keeps them free to add to their own
     * topic.
     */
    @Test
    void readsBothEnvelopesOnTheRegistrationTopicUnderOneKey() {
        assertThat(
            parser
                .parseProfessionalEvent(bytes(registrationCreated("acc-1")))
                .orElseThrow()
                .subjectKey()
        ).isEqualTo("acc-1");
        assertThat(
            parser
                .parseProfessionalEvent(bytes(accountCreatedEvent("acc-1", true)))
                .orElseThrow()
                .subjectKey()
        ).isEqualTo("acc-1");
        assertThat(parser.parseProfessionalEvent(bytes(accountCreatedEvent("acc-1", true).replace("AccountCreated", "AccountSuspended"))))
            .as("a type this service does not model is ignored rather than guessed at")
            .isEmpty();
    }

    /**
     * {@code onboarding.state} carries the state in the payload rather than in the type, which is a
     * deliberate choice on the producer's side so a consumer switches on one field. It also carries
     * no email, which is the reason the subject key cannot be the address.
     */
    @Test
    void readsAnOnboardingStateChange() {
        SiblingDomainEvent event = parser.parseProfessionalEvent(bytes(onboardingState("acc-1", "COMPLETED"))).orElseThrow();

        assertThat(event.state()).isEqualTo("COMPLETED");
        assertThat(event.subjectKey()).isEqualTo("acc-1");
        assertThat(event.email()).isNull();
        assertThat(event.activated())
            .as("progressing through onboarding says nothing about signing in — and 'says nothing' is null, not false")
            .isNull();
    }

    // --- phase 2: the profile status, on hc.professional.entity ----------------------------------

    /**
     * <b>Phase 2 of the two-phase professional contract.</b>
     *
     * <p>{@code ProfileStatus{profileId, accountId, isComplete, isVerified, createdDate,
     * modifiedDate, lastModifiedBy}} — backlog item 47. Identifiers, two booleans and three
     * timestamps, and the assertion worth reading is the one about what is <em>not</em> here: no
     * role, no licence number, no name, no address, so nothing on this topic is a clinical credential
     * and nothing this parser produces could build a {@code Professional}.
     *
     * <p><b>The envelope is {@code ProfessionalEvent}: {@code type} and {@code data}</b>, not
     * {@code eventType} and {@code payload}. This test read the second until 2026-09-08 and passed,
     * because the fixture beside it was written from the same misreading — so the whole phase-2 path
     * was unreachable against the real producer with every assertion green. The fixture is now copied
     * off {@code DomainEventPublisher.publishProfileStatus}.
     *
     * <p>{@code lastModifiedBy} is <b>hc-professional's login</b>, not an accountId: their
     * {@code SpringSecurityAuditorAware} fills it from the JWT subject. Item 47's contract says
     * otherwise and is wrong about their code. It is carried through verbatim either way and never
     * resolved into a name — what changes is that it may not be joined to an identifier on this side.
     */
    @Test
    void readsAProfileStatus() {
        ProfileStatusEvent event = parser.parseProfessionalProfileEvent(bytes(profileStatus("acc-1", true, true))).orElseThrow();

        assertThat(event.type()).isEqualTo("ProfileStatus");
        assertThat(event.accountId()).as("the join to phase 1, and it is the only join there is").isEqualTo("acc-1");
        assertThat(event.profileId()).isEqualTo("prof-9");
        assertThat(event.complete()).isTrue();
        assertThat(event.verified()).isTrue();
        assertThat(event.createdDate()).isEqualTo(Instant.parse("2026-08-19T11:30:00Z"));
        assertThat(event.modifiedDate()).isEqualTo(Instant.parse("2026-09-02T14:47:00Z"));
        assertThat(event.lastModifiedBy()).as("their login, not an accountId and not a display name").isEqualTo("yasante");
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-09-02T14:47:05Z"));
    }

    /**
     * <b>A profile status that omits the two booleans leaves them unknown, and never false.</b>
     *
     * <p>The rule the whole display rests on: "not reported" and "reported as incomplete" are
     * different facts about a person, and rendering the first as the second asserts something about a
     * clinician from the absence of a message. That is items 27(a) and 46's prohibition one column
     * along, and it is why these are {@code Boolean} from the wire down rather than
     * {@code asBoolean(false)} at the edge.
     */
    @Test
    void aProfileStatusThatSaysNothingLeavesTheBooleansUnknown() {
        String silent = profileStatus("acc-1", true, true).replace("\"isComplete\":true,", "").replace("\"isVerified\":true,", "");

        ProfileStatusEvent event = parser.parseProfessionalProfileEvent(bytes(silent)).orElseThrow();

        assertThat(event.complete()).isNull();
        assertThat(event.verified()).isNull();
        assertThat(event.accountId())
            .as("the rest of the frame is still usable — one absent field is not a bad message")
            .isEqualTo("acc-1");
    }

    /**
     * A profile reported as incomplete is stored as incomplete, which is the other half of the pair.
     *
     * <p>Without this case, "unknown is not false" could be satisfied by a parser that answered null
     * for every value — the two assertions only mean something together.
     */
    @Test
    void aProfileReportedIncompleteIsFalseRatherThanUnknown() {
        ProfileStatusEvent event = parser.parseProfessionalProfileEvent(bytes(profileStatus("acc-1", false, false))).orElseThrow();

        assertThat(event.complete()).isFalse();
        assertThat(event.verified()).isFalse();
    }

    /**
     * <b>82% of that topic is {@code Task}, and none of it is written — and neither is a legacy
     * {@code entity.created} for a {@code Profile}.</b>
     *
     * <p>The last of those is the architect's decision 3 of 2026-09-08 and is the assertion worth
     * having. Those frames are real, they are already on the topic, and this consumer group reads
     * from the earliest offset — so accepting them would manufacture roughly thirty phantom "identity
     * not on file" rows on the day of deploy and roughly double the console's awaiting count, which
     * is the moved-figure failure item 46 § 2 forbids. They are keyed on the login rather than on a
     * {@code User.id} and carry none of {@code isComplete}, {@code isVerified} or
     * {@code lastModifiedBy}, so every column the table exists for would read "not reported" as well.
     *
     * <p>The filter is in the parser rather than downstream for the offset reason: unfiltered, the
     * first run of this subscription is thousands of frames reaching a write path that has nothing to
     * do. Item 35 measured the proportion.
     */
    @Test
    void ignoresEverythingOnThatTopicThatIsNotAProfileStatus() {
        assertThat(parser.parseProfessionalProfileEvent(bytes(entityCreated("Task", "acc-1")))).isEmpty();
        assertThat(parser.parseProfessionalProfileEvent(bytes(entityCreated("ProfessionalApplication", "acc-1")))).isEmpty();
        assertThat(parser.parseProfessionalProfileEvent(bytes(entityCreated("Profile", "acc-1"))))
            .as("the legacy frame for the right entity is still refused — decision 3, and the phantom rows are why")
            .isEmpty();
        assertThat(parser.parseProfessionalProfileEvent(bytes(profileStatus("acc-1", true, true))))
            .as("and a ProfileStatus is accepted, or every assertion above would pass against a parser that reads nothing")
            .isPresent();
    }

    /**
     * The other two types on that topic are somebody else's business, and are ignored by type.
     *
     * <p>{@code message.created} is hc-professional's own websocket nudge and {@code compliance.alert}
     * has no surface in this console. Refusing them by type rather than by entity means a frame whose
     * payload shape is unknown never reaches the field reads at all.
     */
    @Test
    void ignoresTheOtherTypesOnTheEntityTopic() {
        assertThat(parser.parseProfessionalProfileEvent(bytes(entityTyped("message.created")))).isEmpty();
        assertThat(parser.parseProfessionalProfileEvent(bytes(entityTyped("compliance.alert")))).isEmpty();
    }

    /**
     * <b>An accepted profile frame with no {@code accountId} is refused, and loudly.</b>
     *
     * <p>This is the failure backlog item 47 singles out: the two phases join on {@code accountId},
     * and a profile status that carries none can never be paired with an account. It is not a message
     * for somebody else — it has passed the type filter — so it is a {@code warn} rather
     * than the {@code debug} an unmodelled type gets. Storing it under a fabricated key, or under the
     * {@code profileId}, would produce a row that looks right and joins to nothing for ever.
     */
    @Test
    void refusesAProfileStatusWithNoAccountIdAndSaysSo() {
        Logger logger = (Logger) LoggerFactory.getLogger(SiblingEventParser.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            assertThat(parser.parseProfessionalProfileEvent(bytes(profileStatus(null, true, true)))).isEmpty();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(appender.list)
            .as("a profile that can never be joined to an account has to be reported, not dropped at debug")
            .anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).contains("accountId");
            });
    }

    /**
     * A blank {@code accountId} is the same as an absent one, and a padded one is the same as a
     * trimmed one.
     *
     * <p>Trimming is the only normalisation either phase applies, and both apply it identically —
     * which is the whole of "the two phases must match exactly". Lower-casing is deliberately not
     * done: an {@code accountId} is a UUID minted by a gateway, and folding case here would make this
     * side tolerant of a producer that had started sending it differently, hiding the one divergence
     * that has to be visible.
     */
    @Test
    void trimsTheJoinKeyOnBothPhasesAndRefusesABlankOne() {
        assertThat(parser.parseProfessionalProfileEvent(bytes(profileStatus("   ", true, true))))
            .as("whitespace is not an identifier")
            .isEmpty();
        assertThat(
            parser
                .parseProfessionalProfileEvent(bytes(profileStatus("  acc-1  ", true, true)))
                .orElseThrow()
                .accountId()
        ).isEqualTo("acc-1");
        assertThat(
            parser
                .parseProfessionalEvent(bytes(registrationCreated("  acc-1  ")))
                .orElseThrow()
                .subjectKey()
        )
            .as("and phase 1 trims to the same string, or the two would never pair")
            .isEqualTo("acc-1");
    }

    /**
     * A numeric {@code occurredAt} is read rather than ignored.
     *
     * <p>hc-patient serialises a real {@code Instant}, and whether that comes out as an ISO string or
     * an epoch number is a Jackson setting in a repository this one does not own. Reading only the
     * string form would not fail — it would fall back to "now", pushing the watermark to the present
     * so that every genuinely later event was then discarded as stale. The consumer would appear to
     * have stopped, with nothing in the log.
     */
    @Test
    void readsAnEpochTimestampAsWellAsAnIsoOne() {
        String numeric = accountCreated(PATIENT_EMAIL, null, false).replace("\"occurredAt\":null", "\"occurredAt\":1788249600.000000000");

        assertThat(parser.parsePatientEvent(bytes(numeric), null).orElseThrow().occurredAt()).isEqualTo(Instant.ofEpochSecond(1788249600L));
    }

    /**
     * A frame with no readable {@code occurredAt} is ignored rather than stamped "now".
     *
     * <p><b>Watermark poisoning, and it is the failure the old fallback caused rather than avoided.</b>
     * {@code occurredAt} is what {@code DirectoryLink.lastEventAt} is set from, and anything strictly
     * older than that is discarded. Stamping an unreadable frame with the present therefore freezes
     * its subject against every event older than the moment it arrived — and these consumer groups
     * read from the earliest offset, so during the backfill "now" is later than every frame in the
     * topic. One such frame would have discarded the rest of that person's history, silently, in the
     * middle of the read that history exists for.
     */
    @Test
    void ignoresAFrameWhoseTimestampItCannotRead() {
        assertThat(parser.parsePatientEvent(bytes(accountCreated(PATIENT_EMAIL, null, false)), null))
            .as("no occurredAt means no watermark to apply the event against")
            .isEmpty();
        assertThat(parser.parsePatientEvent(bytes(accountCreated(PATIENT_EMAIL, "the day before yesterday", false)), null))
            .as("an unparseable one is the same case, and used to be silently read as the present")
            .isEmpty();
        assertThat(parser.parseProfessionalEvent(bytes(registrationCreated("acc-1").replace("\"2026-09-01T08:00:00Z\"", "null"))))
            .as("the same rule on the other stream")
            .isEmpty();
    }

    /**
     * Anything unusable is ignored, never thrown.
     *
     * <p>An exception out of a Spring Cloud Stream consumer is retried and stalls the partition it
     * arrived on, so one bad frame would freeze every subject whose key hashes there. These topics
     * are other products' and carry types added without reference to this consumer.
     */
    @Test
    void ignoresEverythingItCannotUseInsteadOfThrowing() {
        assertThat(parser.parsePatientEvent(bytes("this is not json at all"), null)).isEmpty();
        assertThat(parser.parsePatientEvent(bytes("[1,2,3]"), null))
            .as("a JSON array is not an envelope")
            .isEmpty();
        assertThat(parser.parsePatientEvent(new byte[0], null)).isEmpty();
        assertThat(parser.parsePatientEvent(bytes("{\"type\":\"AccountCreated\",\"subject\":{}}"), null))
            .as("no subject key means nothing to apply it to")
            .isEmpty();
        assertThat(parser.parsePatientEvent(bytes("{\"subject\":{\"email\":\"a@b.co\"}}"), null))
            .as("no type")
            .isEmpty();

        assertThat(parser.parseProfessionalEvent(bytes("{}"))).isEmpty();
        assertThat(parser.parseProfessionalEvent(bytes("{\"eventType\":\"registration.created\",\"payload\":{}}")))
            .as("no accountId means nothing to key on")
            .isEmpty();
    }

    /**
     * An unreadable frame is described, never quoted.
     *
     * <p>The warn for a frame this parser cannot read is the one statement in this service whose
     * content nobody on this side vetted — the payload belongs to another product's topic. It logs a
     * fingerprint now; this pins that.
     *
     * <p><b>It asserts the whole rendered line rather than the fingerprint argument</b>, on purpose,
     * because the argument is not the only way the payload could reach the log: {@code e.toString()}
     * is Jackson's own message, and Jackson quoted the offending input in it until
     * {@code INCLUDE_SOURCE_IN_LOCATION} became disabled-by-default in 2.16. That is a library
     * default rather than something this code controls, so the assertion is on the property — no
     * payload content anywhere in the line — and would fail if the default were turned back on, or
     * if somebody restored the excerpt.
     */
    @Test
    void describesAnUnreadableFrameWithoutQuotingIt() {
        // Distinctive enough that a substring check cannot pass by accident, and shaped like the
        // thing that would actually hurt: a name and an address in a frame this parser gives up on.
        String secret = "Ama-Mensah-0244000000-ama.mensah@example.com";
        byte[] payload = bytes("{\"subject\": \"" + secret + "\", broken");

        List<ILoggingEvent> logged = capture(SiblingEventParser.class, () -> assertThat(parser.parsePatientEvent(payload, null)).isEmpty());

        assertThat(logged).as("an unreadable frame must say so — a silent drop is undiagnosable").isNotEmpty();
        assertThat(logged).allSatisfy(event -> {
            String line = event.getFormattedMessage();
            assertThat(line).as("no payload content may reach the log, from any argument").doesNotContain(secret);
            assertThat(line).doesNotContain("ama.mensah@example.com").doesNotContain("0244000000");
        });

        String warn = logged
            .stream()
            .filter(event -> event.getLevel() == Level.WARN)
            .map(ILoggingEvent::getFormattedMessage)
            .findFirst()
            .orElseThrow();
        assertThat(warn).as("still names the topic it arrived on").contains("patient-events");
        assertThat(warn)
            .as("still carries a handle that tells one frame from another")
            .containsPattern("frame-[0-9a-f]{12} \\(\\d+ bytes\\)");
    }

    /** Runs {@code work} with the class's logger at TRACE and hands back everything it emitted. */
    private static List<ILoggingEvent> capture(Class<?> type, Runnable work) {
        Logger logger = (Logger) LoggerFactory.getLogger(type);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Level original = logger.getLevel();
        logger.setLevel(Level.TRACE);
        logger.addAppender(appender);
        try {
            work.run();
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(original);
            appender.stop();
        }
        return appender.list;
    }

    // --- the wire formats, copied from the two publishers -----------------------------------------

    private static String accountCreated(String email, String occurredAt, boolean activated) {
        return (
            "{\"eventId\":\"evt-1\",\"type\":\"AccountCreated\",\"version\":1," +
            "\"occurredAt\":" +
            (occurredAt == null ? "null" : "\"" + occurredAt + "\"") +
            ",\"source\":\"patientGateway\"," +
            "\"subject\":{\"email\":\"" +
            email +
            "\",\"login\":\"amensah\",\"patientId\":null}," +
            "\"data\":{\"authorities\":\"ROLE_USER\",\"langKey\":\"en\",\"activated\":" +
            activated +
            "}}"
        );
    }

    /** hc-patient's {@code CareAngelResource.nominate}, first frame, verbatim from that call site. */
    private static String careAngelNominated(String email) {
        return (
            "{\"eventId\":\"evt-angel\",\"type\":\"AccountCreated\",\"version\":1," +
            "\"occurredAt\":\"2026-09-01T08:00:00Z\",\"source\":\"patientGateway\"," +
            "\"subject\":{\"email\":\"" +
            email +
            "\",\"login\":\"aangel\",\"patientId\":null}," +
            "\"data\":{\"authorities\":\"ROLE_USER,ROLE_ANGEL\",\"activated\":true,\"reason\":\"careAngelNomination\"}}"
        );
    }

    /** {@code DeletionRequestService.announce} — one type, a {@code change} discriminator. */
    private static String deletionRequest(String email, String change) {
        return (
            "{\"eventId\":\"evt-deletion\",\"type\":\"DeletionRequestChanged\",\"version\":1," +
            "\"occurredAt\":\"2026-09-01T12:00:00Z\",\"source\":\"hcPatientService\"," +
            "\"subject\":{\"email\":\"" +
            email +
            "\",\"login\":\"amensah\",\"patientId\":\"p-1234\"}," +
            "\"data\":{\"requestId\":\"req-1\",\"change\":\"" +
            change +
            "\"}}"
        );
    }

    /**
     * hc-patient's {@code MembershipResource.announceChosenPlan}, verbatim from that call site.
     *
     * <p>Their {@code PatientEventPublisher} builds the envelope; what this method reproduces is the
     * {@code data} map that method puts in it, key for key. The status is published as
     * {@code MembershipStatus.name()} rather than the enum, in their own comment's words so that
     * "the wire shape should not move if the enum's serialization ever does".
     */
    private static String planChosen(String email, String planCode, String status) {
        return (
            "{\"eventId\":\"evt-plan\",\"type\":\"PlanChosen\",\"version\":1," +
            "\"occurredAt\":\"2026-09-08T18:21:00Z\",\"source\":\"hcPatientService\"," +
            "\"subject\":{\"email\":\"" +
            email +
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

    /** A minimal well-formed envelope of an arbitrary type, for sweeping the disposition table. */
    private static String typed(String type, String email) {
        return (
            "{\"eventId\":\"evt-typed\",\"type\":\"" +
            type +
            "\",\"version\":1,\"occurredAt\":\"2026-09-01T08:00:00Z\",\"source\":\"hcPatientService\"," +
            "\"subject\":{\"email\":\"" +
            email +
            "\",\"login\":\"amensah\",\"patientId\":null},\"data\":{}}"
        );
    }

    private static String professionalTyped(String eventType) {
        return (
            "{\"eventId\":\"evt-other\",\"eventType\":\"" +
            eventType +
            "\",\"occurredAt\":\"2026-09-01T08:00:00Z\",\"source\":\"hc-professional-service\",\"actor\":\"system\"," +
            "\"payload\":{\"accountId\":\"acc-1\",\"alertType\":\"LICENCE_EXPIRING\"}}"
        );
    }

    private static String accountActivated(String email) {
        return (
            "{\"eventId\":\"evt-2\",\"type\":\"AccountActivated\",\"version\":1," +
            "\"occurredAt\":\"2026-09-01T09:00:00Z\",\"source\":\"patientGateway\"," +
            "\"subject\":{\"email\":\"" +
            email +
            "\",\"login\":\"amensah\",\"patientId\":null}," +
            "\"data\":{\"activatedAt\":\"2026-09-01T09:00:00Z\"}}"
        );
    }

    private static String onboardingStarted(String email, String patientId) {
        return (
            "{\"eventId\":\"evt-3\",\"type\":\"OnboardingStarted\",\"version\":1," +
            "\"occurredAt\":\"2026-09-01T10:00:00Z\",\"source\":\"hcPatientService\"," +
            "\"subject\":{\"email\":\"" +
            email +
            "\",\"login\":null,\"patientId\":\"" +
            patientId +
            "\"}," +
            "\"data\":{\"startedAt\":\"2026-09-01T10:00:00Z\"}}"
        );
    }

    private static String registrationCreated(String accountId) {
        return (
            "{\"eventId\":\"evt-4\",\"eventType\":\"registration.created\"," +
            "\"occurredAt\":\"2026-09-01T08:00:00Z\",\"source\":\"hc-professional-gateway\",\"actor\":\"anonymous\"," +
            "\"payload\":{\"accountId\":\"" +
            accountId +
            "\",\"login\":\"kboateng\",\"email\":\"k.boateng@example.com\"," +
            "\"langKey\":\"en\",\"origin\":\"self-service\"}}"
        );
    }

    /**
     * <b>Phase 2, copied field for field off hc-professional's {@code DomainEventPublisher}</b> at
     * {@code origin/main} 2026-09-08 — not off item 47's prose, which is what produced the shape this
     * fixture had until then and the reader that matched it.
     *
     * <p>{@code ProfessionalEvent}: {@code type} rather than {@code eventType}, {@code data} rather
     * than {@code payload}, a {@code subject} beside the body whose {@code email} and {@code login}
     * their service deliberately leaves null, and <b>no {@code entityType}</b>. The {@code subject}
     * repeats the {@code accountId} that {@code data} carries, which the reader does not read — see
     * {@code parseProfessionalProfileEvent} for why one field is read in one place.
     *
     * <p>{@code accountId} is nullable so the refusal case can drop it, which is the one field this
     * parser must never invent.
     */
    private static String profileStatus(String accountId, boolean complete, boolean verified) {
        return (
            "{\"eventId\":\"evt-6\",\"type\":\"ProfileStatus\",\"version\":1," +
            "\"occurredAt\":\"2026-09-02T14:47:05Z\",\"source\":\"hc-professional-service\"," +
            "\"subject\":{\"email\":null,\"login\":null," +
            (accountId == null ? "\"accountId\":null}," : "\"accountId\":\"" + accountId + "\"},") +
            "\"data\":{\"profileId\":\"prof-9\"," +
            (accountId == null ? "" : "\"accountId\":\"" + accountId + "\",") +
            "\"isComplete\":" +
            complete +
            ",\"isVerified\":" +
            verified +
            ",\"createdDate\":\"2026-08-19T11:30:00Z\",\"modifiedDate\":\"2026-09-02T14:47:00Z\"," +
            "\"lastModifiedBy\":\"yasante\"}}"
        );
    }

    /**
     * What hc-professional's {@code publishEntityCreated} emits: three fields, their <em>other</em>
     * envelope, and a type this consumer no longer accepts on that topic at all.
     */
    private static String entityCreated(String entityType, String accountId) {
        return (
            "{\"eventId\":\"evt-7\",\"eventType\":\"entity.created\"," +
            "\"occurredAt\":\"2026-09-02T14:47:05Z\",\"source\":\"hc-professional-service\",\"actor\":\"admin\"," +
            "\"payload\":{\"entityType\":\"" +
            entityType +
            "\",\"entityId\":\"ent-1\",\"accountId\":\"" +
            accountId +
            "\"}}"
        );
    }

    /** The two types on that topic that are not entity events at all. */
    private static String entityTyped(String eventType) {
        return (
            "{\"eventId\":\"evt-8\",\"eventType\":\"" +
            eventType +
            "\",\"occurredAt\":\"2026-09-02T14:47:05Z\",\"source\":\"hc-professional-service\"," +
            "\"actor\":\"admin\",\"payload\":{\"accountId\":\"acc-1\"}}"
        );
    }

    /**
     * <b>Phase 1's {@code AccountCreated}, copied off hc-professional's
     * {@code RegistrationEventPublisher}</b> at {@code origin/main} 2026-09-08.
     *
     * <p>The estate-shaped envelope, on the same topic as {@code registration.created} and beside it
     * rather than instead of it — their publisher's javadoc says so, so a registration puts three
     * frames on the topic under two envelopes. {@code data} is exactly {@code authorities},
     * {@code langKey} and {@code activated}: <b>no {@code createdDate} and no {@code modifiedDate}</b>,
     * which the fixture this replaced invented onto a {@code registration.created} that carries
     * neither. {@code activated} is nullable here so the "nobody has said" case can drop it, which is
     * the state every clinician is in until they consume one of these.
     */
    private static String accountCreatedEvent(String accountId, Boolean activated) {
        return (
            "{\"eventId\":\"evt-4b\",\"type\":\"AccountCreated\",\"version\":1," +
            "\"occurredAt\":\"2026-09-01T08:00:00Z\",\"source\":\"hc-professional-gateway\"," +
            "\"subject\":{\"email\":\"k.boateng@example.com\",\"login\":\"kboateng\",\"accountId\":\"" +
            accountId +
            "\"}," +
            "\"data\":{\"authorities\":\"ROLE_USER\",\"langKey\":\"en\"" +
            (activated == null ? "" : ",\"activated\":" + activated) +
            "}}"
        );
    }

    /** Phase 1's {@code AccountActivated}: the activation link was followed and the key consumed. */
    private static String accountActivatedEvent(String accountId) {
        return (
            "{\"eventId\":\"evt-4c\",\"type\":\"AccountActivated\",\"version\":1," +
            "\"occurredAt\":\"2026-09-01T09:20:00Z\",\"source\":\"hc-professional-gateway\"," +
            "\"subject\":{\"email\":\"k.boateng@example.com\",\"login\":\"kboateng\",\"accountId\":\"" +
            accountId +
            "\"}," +
            "\"data\":{\"activatedAt\":\"2026-09-01T09:20:00Z\"}}"
        );
    }

    private static String onboardingState(String accountId, String state) {
        return (
            "{\"eventId\":\"evt-5\",\"eventType\":\"onboarding.state\"," +
            "\"occurredAt\":\"2026-09-01T11:00:00Z\",\"source\":\"hc-professional-service\",\"actor\":\"admin\"," +
            "\"payload\":{\"accountId\":\"" +
            accountId +
            "\",\"state\":\"" +
            state +
            "\",\"applicationId\":\"app-1\",\"requestedRole\":\"NURSE\"}}"
        );
    }

    private static byte[] bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
