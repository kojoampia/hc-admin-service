package net.jojoaddison.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import net.jojoaddison.domain.DirectoryLink;
import net.jojoaddison.domain.Patient;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.repository.DirectoryLinkRepository;
import net.jojoaddison.repository.PatientRepository;
import net.jojoaddison.service.dto.RoundCustomerDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * The patients a round can be planned for: those this service can address on hc-patient's stack.
 *
 * <h2>What this closes, and why the entry that forbade it no longer applies</h2>
 *
 * <p>Backlog item 22: a round's {@code visits[].customerId} is a {@code patientservice}
 * {@code Profile.patientId}, and the console asked an administrator to type those ids in by hand,
 * comma separated. Honest, and poor — nobody knows another stack's ids by heart, and a typo is
 * accepted, filed, and produces a day plan for nobody.
 *
 * <p><b>Item 22 forbids a picker, and this is one. Read why that is not a contradiction.</b> The
 * entry says: "Do not close this by making the field a dropdown of hc-admin patients", and the
 * failure it describes is a dropdown that sends {@code Patient.id} — "an id that means nobody",
 * producing a round "a plausible wrong id" long. That failure is about the <em>value</em>, not about
 * the control. This picker sends {@link DirectoryLink#getExternalId()}, which <b>is</b> hc-patient's
 * {@code patientId} for the person, arriving on their {@code OnboardingStarted} — the one event that
 * binds an address to a patient id. Sending it is the opposite of the mistake the entry names.
 *
 * <p>The entry's premise was true when it was written on 2026-09-04 and stopped being true on
 * 2026-09-07: {@code directory_link} — the join it says this service does not hold — arrived with
 * backlog items 45 to 47, and {@link DirectoryLink}'s own javadoc says it was deliberately kept
 * <em>beside</em> {@code Patient} so that "item 22 stays free to decide the cross-stack identity
 * model". This is that decision being taken, on the collection that was left free for it.
 *
 * <h2>Absent, never guessed — the rule that makes the partial coverage safe</h2>
 *
 * <p><b>A patient with no link carrying an {@code externalId} is not offered at all.</b> Not offered
 * with their local id, not offered with a blank, not offered greyed out: absent. There is no id this
 * service could put on their visit that hc-professional would recognise, and a round is a real day's
 * work for a clinician who will arrive somewhere — so the honest answer is that this screen cannot
 * plan for them, and the console says so in words above the picker.
 *
 * <p>That is a real and permanent state rather than a pending one. Today's {@code test} fixture has
 * fifteen patients and six such links; in production the ratio moves as accounts complete onboarding,
 * and it never reaches all of them — an administrator can create a {@code Patient} here that no
 * hc-patient account corresponds to.
 *
 * <h2>The filters, and which one is load-bearing</h2>
 *
 * <ul>
 *   <li><b>{@code external_id} present — the one that decides.</b> It is the value that goes on the
 *       wire; without it there is nothing to send. It is deliberately the test rather than "a link
 *       exists": a care-angel link and an erased link both exist and carry neither an
 *       {@code external_id} nor a {@code local_id}, so filtering on the link would offer rows with
 *       nothing to send.</li>
 *   <li><b>{@code erased_at} absent — a second rule with its own reason, not a restatement.</b>
 *       {@code DirectoryProjectionService.recordEvent} unsets {@code external_id} when hc-patient
 *       reports an erasure, so this filter is today redundant with the one above. It is here anyway
 *       because the two answer different questions: that unset is one write path's behaviour and
 *       could change or have missed a row written before 2026-09-05, and what must never happen is
 *       offering a clinician a home visit to somebody the far side has erased. Belt and braces on
 *       purpose.</li>
 *   <li><b>{@code local_id} resolving to a {@code Patient} that is not archived.</b> The picker is
 *       over <em>this directory's</em> patients; a link whose record is missing (the crash between
 *       the two un-transacted writes {@link DirectoryLink} documents) has no row to offer, and an
 *       archived patient is one an administrator has taken out of the directory.</li>
 * </ul>
 *
 * <p>There is deliberately no {@code subject_kind} filter. A {@code CARE_ANGEL} link never carries an
 * {@code external_id} — a nomination publishes {@code AccountCreated}, which has no patient id on it
 * — and never carries a {@code local_id} either, so both filters above already exclude one. A third
 * test asserting the same exclusion would be two rules for one question, which is the shape this
 * repository has been bitten by before.
 *
 * <h2>Two reads for the whole page, and only the remote tier is expensive</h2>
 *
 * <p>One {@code findBySource(HC_PATIENT)} and one {@code findAllById}, then the join in memory. That
 * is {@code PatientCsvExporter.LinkedIdentities}' shape and item 62's lesson: reading the link is a
 * Mongo query in this process, and it is <em>resolving a name from hc-patient</em> that is the remote
 * fan-out worth budgeting. <b>This service does not do that</b> — deliberately, and see
 * {@link #displayName} — so nothing here reaches another stack.
 */
@Service
public class RoundCustomerService {

    private final DirectoryLinkRepository directoryLinkRepository;
    private final PatientRepository patientRepository;

    public RoundCustomerService(DirectoryLinkRepository directoryLinkRepository, PatientRepository patientRepository) {
        this.directoryLinkRepository = directoryLinkRepository;
        this.patientRepository = patientRepository;
    }

    /**
     * One page of plannable patients, sorted by the name the console will show.
     *
     * <p><b>Sorted and paged here rather than by MongoDB, because the sort key is on neither
     * document.</b> A row's name comes from the {@code Patient}'s {@code Profile} and falls back to
     * the address on its {@code DirectoryLink}, so ordering by it is not a query either collection
     * can answer. The set is bounded by the number of hc-patient accounts this service has seen
     * complete onboarding, and both reads are already whole-collection by source — so assembling and
     * slicing costs nothing the reads did not.
     *
     * <p>It is paged at all because every list endpoint in this service is, and because
     * {@code PaginationIT} sweeps single-segment {@code /api} paths from the handler mapping and will
     * find this one the moment it exists — which is the property that sweep was rewritten to have.
     *
     * @param pageable the page wanted.
     * @return that page, with the total across every plannable patient.
     */
    public Page<RoundCustomerDTO> plannableCustomers(Pageable pageable) {
        Map<String, DirectoryLink> byLocalId = addressableLinksByLocalId();
        if (byLocalId.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        List<RoundCustomerDTO> rows = new ArrayList<>();
        for (Patient patient : patientRepository.findAllById(byLocalId.keySet())) {
            if (Boolean.TRUE.equals(patient.getIsArchived())) {
                continue;
            }
            DirectoryLink link = byLocalId.get(patient.getId());
            rows.add(new RoundCustomerDTO(link.getExternalId().strip(), displayName(patient, link)));
        }
        rows.sort(BY_NAME_THEN_ID);

        return new PageImpl<>(slice(rows, pageable), pageable, rows.size());
    }

    /**
     * Every {@code HC_PATIENT} link that carries an id the far stack would recognise, by the local
     * record it names.
     *
     * <p>Built by hand rather than with {@code toMap}, which throws on a duplicate key. Two links
     * claiming one record should not happen — {@code DirectoryProjectionService.createAndClaim}
     * claims atomically — but a picker is the wrong place to find out, and refusing the whole list
     * over it would take every patient off the screen to report one stale row.
     */
    private Map<String, DirectoryLink> addressableLinksByLocalId() {
        Map<String, DirectoryLink> byLocalId = new HashMap<>();
        for (DirectoryLink link : directoryLinkRepository.findBySource(DirectorySource.HC_PATIENT)) {
            if (link.getErasedAt() != null) {
                continue;
            }
            if (isBlank(link.getExternalId()) || isBlank(link.getLocalId())) {
                continue;
            }
            byLocalId.putIfAbsent(link.getLocalId().strip(), link);
        }
        return byLocalId;
    }

    /**
     * First and last name, then the address on the link, then null.
     *
     * <p>The directory screen's order and the export's, for the third time in this product — see
     * {@code PatientCsvExporter.displayName} and {@code resolveLinkIdentity} in
     * {@code app/.../entities/directory/directory-link/directory-link.model.ts}. The address before
     * the login is that function's decision rather than a new one: somebody who reports that they
     * registered and cannot be found gives their email, never their login.
     *
     * <p><b>Never the record's id</b>, which is the rule items 45, 53 and 62 each restored after it
     * was broken. Null instead, and the console renders words.
     *
     * <p><b>And never a name asked of hc-patient.</b> The console's patient list sends
     * {@code resolveNames=true} and shows the person above the address (item 50); this does not, and
     * the difference is deliberate rather than an omission. A picker is opened to choose one row out
     * of a list and closed again — putting a remote fan-out behind opening a dropdown makes a slow
     * sibling a screen that will not open, for a name the administrator is not reading. Item 70
     * settled that two surfaces may legitimately name the same patient differently; this is a third
     * surface taking the cheap tier, and it says so here rather than leaving a reader to infer it.
     */
    private static String displayName(Patient patient, DirectoryLink link) {
        Profile profile = patient.getProfile();
        if (profile != null) {
            String name = Stream.of(profile.getFirstName(), profile.getLastName())
                .filter(Objects::nonNull)
                .filter(part -> !part.isBlank())
                .reduce((first, second) -> first + " " + second)
                .orElse("");
            if (!name.isBlank()) {
                return name;
            }
        }
        // Written out rather than chained, because `strip()` yields "" for a whitespace-only field:
        // falsy to a reader, present to a null check, and a chain would offer it as somebody's name.
        if (!isBlank(link.getEmail())) {
            return link.getEmail().strip();
        }
        if (!isBlank(link.getLogin())) {
            return link.getLogin().strip();
        }
        return null;
    }

    /**
     * Nameless rows last, then by name, then by the id — so the order is total.
     *
     * <p>The tie-break is not decoration: two patients can share a name, and a comparator that
     * leaves them equal lets the same page return them in a different order on each request, which
     * reads as rows moving under the cursor.
     */
    private static final Comparator<RoundCustomerDTO> BY_NAME_THEN_ID = Comparator.comparing(
        RoundCustomerDTO::name,
        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
    ).thenComparing(RoundCustomerDTO::customerId);

    private static List<RoundCustomerDTO> slice(List<RoundCustomerDTO> rows, Pageable pageable) {
        if (pageable.isUnpaged()) {
            return rows;
        }
        int from = (int) Math.min(pageable.getOffset(), rows.size());
        int to = Math.min(from + pageable.getPageSize(), rows.size());
        return rows.subList(from, to);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
