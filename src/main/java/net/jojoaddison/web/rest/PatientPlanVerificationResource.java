package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import net.jojoaddison.broker.PlanVerificationEvent;
import net.jojoaddison.domain.DirectoryLink;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.domain.enumeration.DirectorySubjectKind;
import net.jojoaddison.repository.DirectoryLinkRepository;
import net.jojoaddison.service.PatientPlanVerificationService;
import net.jojoaddison.service.dto.PatientPlanVerificationDTO;
import net.jojoaddison.service.dto.PatientPlanVerificationRequest;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The administrator's decision that a patient's chosen membership tier stands.
 *
 * <p>Backlog item 54, and the other end of item 48. A patient picks a tier in the patient portal,
 * hc-patient writes a {@code Membership} with {@code status: PENDING} and announces it, the console
 * lists the choice as awaiting a decision — and this is the only thing that answers it.
 *
 * <h2>{@code POST} and nothing else, which is the contract rather than an unfinished surface</h2>
 *
 * <p><b>A decision is recorded, not toggled</b>, and this console removed exactly such a toggle on
 * purpose. {@code professional-detail.ts:291} records the reasoning from the other verification in
 * this product: it was a {@code PATCH {verification: 'PENDING'}} until 2026-08-24, and *"that path no
 * longer exists: the field is written only by the server, from a recorded decision, so the console
 * asks for the decision and the projection follows"*. So there is no {@code PUT}, no {@code PATCH}
 * and no {@code DELETE} here either, and no status parameter to set — asking is the whole API.
 *
 * <p>There is also no {@code GET}. {@link ProfessionalVerificationResource} has one because that
 * decision is stored in a history collection; this one stores nothing at all, so there is nothing to
 * read back. What an administrator can see is the plan choice itself, on
 * {@code GET /api/directory-links?planStatus=PENDING}.
 *
 * <h2>{@code 202}, not {@code 201} and not {@code 200}</h2>
 *
 * <p>Nothing was created, so {@code 201} would be a lie and there is no {@code Location} to point at.
 * {@code 200} would claim the work is done, and it is not: the publish is handed to
 * {@code OutboundEventPublisher}'s executor and this method returns before the broker has been
 * touched, deliberately, because a caller waiting on an absent broker waits sixty seconds under a
 * shared lock (backlog item 39a). <b>{@code 202 Accepted} is the honest answer</b> — the decision has
 * been taken and queued, and whether it reached hc-patient is a {@code WARN} in this service's log
 * and not something this response can know.
 *
 * <h2>What it deliberately does not do</h2>
 *
 * <p><b>It does not activate anything, and must not be labelled as though it did.</b>
 * {@code VERIFIED} is not {@code ACTIVE}: hc-patient's own clients test for {@code ACTIVE} when
 * deciding whether a patient holds a plan, and what {@code VERIFIED} means beside it is an open
 * question on their side. This end announces a verification and asserts nothing about what they do
 * with it.
 *
 * <p><b>There is no refusal path</b>, and its absence is a decision recorded on
 * {@link PlanVerificationEvent}: the payload lost its {@code isVerified} flag precisely because it
 * could only ever be {@code true}. Nothing today describes what an administrator does when a choice
 * should not stand. When that is decided it is a second control here, not this one inverted.
 *
 * <p>Authorities come from the blanket read/write split in {@code SecurityConfiguration} and are not
 * restated: a {@code POST} under {@code /api/**} reaches {@code ROLE_ADMIN} alone, which is the right
 * shape for an act that speaks to another product in this console's name. An operator may see the
 * plan choices and may not decide them.
 */
@RestController
@RequestMapping("/api/patient-plan-verifications")
public class PatientPlanVerificationResource {

    private static final Logger LOG = LoggerFactory.getLogger(PatientPlanVerificationResource.class);

    private static final String ENTITY_NAME = "patientPlanVerification";

    private final PatientPlanVerificationService planVerificationService;

    private final DirectoryLinkRepository directoryLinkRepository;

    public PatientPlanVerificationResource(
        PatientPlanVerificationService planVerificationService,
        DirectoryLinkRepository directoryLinkRepository
    ) {
        this.planVerificationService = planVerificationService;
        this.directoryLinkRepository = directoryLinkRepository;
    }

    /**
     * {@code POST /api/patient-plan-verifications} : verify a patient's plan choice and tell
     * hc-patient.
     *
     * <p>Every refusal below is a {@code 400} rather than a {@code 404}, on the same reading
     * {@link ProfessionalVerificationResource} takes: the link is a <em>field of the decision being
     * recorded</em>, not the resource being addressed, so an unusable one is a bad body and not a
     * missing endpoint. The resource being addressed is this collection, and it exists.
     *
     * <p><b>Note what is deliberately not logged.</b> The link id is safe; the address on it is not,
     * and the service names the patient by a digest for the reason
     * {@link net.jojoaddison.service.LogPseudonym} gives.
     *
     * @param request the decision, naming the plan choice it is about
     * @return {@code 202 (Accepted)} and what was announced.
     */
    @PostMapping("")
    public ResponseEntity<PatientPlanVerificationDTO> verifyPatientPlan(@Valid @RequestBody PatientPlanVerificationRequest request) {
        LOG.debug("REST request to verify the plan choice on directory link {}", request.linkId());

        DirectoryLink link = directoryLinkRepository
            .findById(request.linkId())
            .orElseThrow(() -> new BadRequestAlertException("No such directory link", ENTITY_NAME, "linknotfound"));

        // Not every link is a patient. `patient-events` carries care-angel nominations under the same
        // AccountCreated type, and `hc.professional.registration` is a different stream entirely —
        // neither has a membership, and publishing a plan verification keyed on a care angel's
        // address would ask hc-patient to verify a plan for somebody who has never chosen one.
        if (link.getSource() != DirectorySource.HC_PATIENT || link.getSubjectKind() != DirectorySubjectKind.PATIENT) {
            throw new BadRequestAlertException("That link is not a patient's", ENTITY_NAME, "notapatientlink");
        }

        // A membership that names no tier is a real stored state and not a half-written one:
        // hc-patient's `Membership.plan` and `.name` carry no @NotNull and their administrative CRUD
        // path can create one with neither. It is refused rather than published as `{"plan":null}`,
        // which would read as a well-formed verification of nothing and would defeat the consistency
        // check the payload's single field exists to enable. `dl-plan-a5` in the `test` fixture is
        // exactly this row, so the branch is reachable on every stack.
        if (link.getPlanCode() == null || link.getPlanCode().isBlank()) {
            throw new BadRequestAlertException("That plan choice names no tier", ENTITY_NAME, "notiernamed");
        }

        // The address is what the exchange is keyed on, so a link without one cannot be answered at
        // all. Reachable: `dl-prof-anon` shows a link whose only event carried no address, and while
        // that one is a clinician and refused above, nothing guarantees a patient link always has one.
        if (link.getExternalKey() == null || link.getExternalKey().isBlank()) {
            throw new BadRequestAlertException("That link has no subject to key an event on", ENTITY_NAME, "nosubjectkey");
        }

        PlanVerificationEvent published = planVerificationService.record(link);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
            new PatientPlanVerificationDTO(published.getData().plan(), link.getPlanMembershipId())
        );
    }
}
