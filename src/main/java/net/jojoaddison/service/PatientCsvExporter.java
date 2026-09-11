package net.jojoaddison.service;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import net.jojoaddison.domain.Address;
import net.jojoaddison.domain.DirectoryLink;
import net.jojoaddison.domain.Patient;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.repository.DirectoryLinkRepository;
import org.springframework.stereotype.Service;

/**
 * Writes the patient directory out as CSV, one row per patient, in the order the query hands them
 * over.
 *
 * <p><strong>The columns are the ones the directory screen shows</strong>, in the order it shows
 * them, plus the three dates and the case count the record screen carries. An export that quietly
 * held more than the screen would be a second, wider read of the same collection wearing the name of
 * the narrower one — and this endpoint is admin-only precisely because bulk extraction is a
 * different act from paging, so widening it by accident is the failure that matters here.
 *
 * <p>Derived cells are computed the same way the screen computes them, and that duplication is
 * deliberate rather than shared: the screen's versions live in TypeScript and cannot be called from
 * here, so the choice is between restating the rule and inventing a second one. Each is noted at its
 * method. Age is the exception worth naming — it is a function of today, so an export taken tomorrow
 * legitimately differs.
 *
 * <p>Rows are written as the cursor yields them rather than collected first. The directory is small
 * today; an export is the one endpoint with no page size, so it is the one place where the size of
 * the collection decides whether the service stays up.
 *
 * <h2>A patient with no {@code Profile} is named from its {@link DirectoryLink} — backlog item 62</h2>
 *
 * <p>Until 2026-09-10 the first column fell back to {@code patient.getId()}, so the three seeded
 * nameless patients exported as {@code a13}, {@code a14} and {@code a15} while the console showed an
 * address for two of them and said "identity not on file" for the third. <b>The console and the
 * export disagreed about the same record and nothing anywhere reported it</b> — item 45's sweep,
 * {@code record-identity.spec.ts}, is a Vitest spec over Angular templates and structurally cannot
 * see server-side Java, so the rule was enforced everywhere a <em>screen</em> renders and nowhere a
 * <em>document</em> does.
 *
 * <p>The rule is now the console's own: the profile name, then the address on the link, then the
 * login on the link, then {@link #IDENTITY_NOT_ON_FILE}. Never the record's id. See
 * {@link #displayName} for why the last of those is words rather than the empty cell every other
 * absent value in this file uses.
 *
 * <h2>⚠ If you are writing the second exporter, read this — backlog item 69</h2>
 *
 * <p><b>Item 45's rule is <em>never name a record by its key</em>, and this class is the only place
 * in the service that has ever broken it — twice.</b> Item 53 named a clinical lead by its id; item
 * 62 named the patient by its own. Both shipped, both were found by a person reading a downloaded
 * file, and nothing in the estate reported either: the sweep that enforces the rule,
 * {@code app/.../entities/directory/record-identity.spec.ts}, is a Vitest spec over Angular
 * templates and structurally cannot see Java.
 *
 * <p>The guard on this side is
 * {@code PatientCsvExporterTest.noCellInTheFileIsTheIdOfAnyRecordTheRowReaches}. It writes a row
 * wired to every record a {@link Patient} can reach, each carrying an id, and fails if any cell of
 * the output equals one — naming the column. <b>It asserts the output rather than the source</b>,
 * because an id where a name goes has no syntactic signature: {@code text(x.getId())} and
 * {@code text(x.getName())} are the same expression with a different method on it, and this estate's
 * two attempts at reading source text for a rule like this were defeated by a Prettier line break
 * (item 57) and by a {@code );} inside a string literal (item 59).
 *
 * <p><b>That sweep cannot find an exporter that does not exist yet, and nothing here pretends
 * otherwise.</b> There is exactly one exporter in this service — no interface, no base class, no
 * registry to derive a population from — so the check is bound to this class by construction. A
 * second exporter must bring its own copy of that sweep; it will not inherit one, and no build will
 * tell you it is missing. That residual is item 69's, stated here because this class javadoc is
 * where the next exporter's author will be reading when it becomes their problem.
 *
 * <h2>This file and the console name the same record differently, on purpose — backlog item 70</h2>
 *
 * <p>The console's patient list sends {@code resolveNames=true} and asks hc-patient, who own the
 * name, so a record reading {@code kojo@jac.net} here reads {@code Kojo Ampia-Addison} there at the
 * same moment. Item 70 weighed closing that and decided the two tiers are both correct — see
 * {@link #displayName} for the cost that was refused. <b>What the decision required was that the
 * difference stop living in one javadoc: {@code PatientNamingTiersTest} now drives both naming rules
 * over one link and fails if either tier moves or if the two converge, and
 * {@code PatientNamingTiersIT} asks the same of the two real endpoints.</b> Treat a failure in either
 * as a decision to confirm, not as a defect to fix.
 */
@Service
public class PatientCsvExporter {

    /**
     * What the first column says when nothing this service holds can name the patient.
     *
     * <p>The console's wording, in words, and it is the one place in this file where an absence is
     * marked with a value rather than left empty. That reverses nothing: {@code clinicalLead}'s
     * javadoc argues at length for the empty cell and its argument is about a <b>secondary</b>
     * column — the row is still identified by its first column, still findable, and a reader can act
     * on "no lead named" and "a lead nothing can name" identically. This column is the identity, and
     * the two failures are not alike. A blank here does not make the row incomplete, it makes it
     * <b>anonymous</b>: the file carries no local-id column ({@code Id number} is the government
     * document number), so a blank first cell is a row that cannot be matched to anything at all,
     * and an administrator reading it has no way to tell it from a row whose export went wrong.
     *
     * <p>The other half of {@code clinicalLead}'s objection does not arise either. Its complaint was
     * that a dash would give <b>one column two markers for two states nobody can act on
     * differently</b>; there is one marker here for one state, and it is a sentence rather than a
     * character, so it explains itself in a format that carries no legend.
     */
    static final String IDENTITY_NOT_ON_FILE = "Identity not on file";

    /**
     * The identity map, for the rows that have no {@code Profile}.
     *
     * <p>Injected rather than reached through the resource, because the fallback is a shaping rule
     * and this class owns the shaping rules. It is read at most once per export — see
     * {@link LinkedIdentities}.
     */
    private final DirectoryLinkRepository directoryLinkRepository;

    public PatientCsvExporter(DirectoryLinkRepository directoryLinkRepository) {
        this.directoryLinkRepository = directoryLinkRepository;
    }

    /** The header row, and the definition of what a column means. */
    static final List<String> COLUMNS = List.of(
        "Patient",
        "Id number",
        "Age",
        "Sex",
        "Location",
        "Plan",
        "Sponsor",
        "Sponsor relationship",
        "Clinical lead",
        "Status",
        "Joined on",
        "Last active on",
        "Cases",
        "Archived"
    );

    /**
     * Writes a header and then a row per patient.
     *
     * @param patients the matched patients, still being read from the database
     * @param today the date age is computed against, passed in rather than read here so a test can
     *     assert an age without waiting for a birthday
     */
    public void write(Writer writer, Stream<Patient> patients, LocalDate today) throws IOException {
        writeRow(writer, COLUMNS);
        LinkedIdentities identities = new LinkedIdentities(directoryLinkRepository);
        // The stream holds a cursor. Closing it is the caller's job in principle, but a partial
        // write throws from inside the loop and would leak it, so it is closed here where it is read.
        try (Stream<Patient> rows = patients) {
            for (Patient patient : (Iterable<Patient>) rows::iterator) {
                writeRow(writer, row(patient, today, identities));
            }
        }
    }

    private List<String> row(Patient patient, LocalDate today, LinkedIdentities identities) {
        Profile profile = patient.getProfile();
        return List.of(
            displayName(patient, identities),
            text(profile == null ? null : profile.getIdNumber()),
            age(profile, today),
            profile == null ? "" : text(profile.getSex()),
            location(profile),
            patient.getPlan() == null ? "" : text(patient.getPlan().getName()),
            patient.getAngel() == null ? "" : text(patient.getAngel().getName()),
            patient.getAngel() == null ? "" : text(patient.getAngel().getRelationship()),
            clinicalLead(patient),
            text(patient.getStatus()),
            text(patient.getJoinedOn()),
            text(patient.getLastActiveOn()),
            text(patient.getCaseCount()),
            Boolean.TRUE.equals(patient.getIsArchived()) ? "yes" : "no"
        );
    }

    /**
     * First and last name, then the identity on the patient's {@link DirectoryLink}, then words.
     *
     * <p>The directory screen's own order, restated here for the reason this class's javadoc gives —
     * the screen's version is TypeScript and cannot be called from Java. It is
     * {@code Patient.displayName} over {@code resolveLinkIdentity} in
     * {@code app/.../entities/directory/directory-link/directory-link.model.ts}, and the address
     * before the login is that function's decision rather than a new one: somebody who reports that
     * they registered and cannot be found gives their email, never their login.
     *
     * <p><b>Never the record's id.</b> That is what this did until backlog item 62 — the same defect
     * item 45 removed from the screen and item 53 removed from the clinical-lead column, arriving a
     * third time in the one column nothing had looked at. A 24-character ObjectId in a spreadsheet
     * reads as data rather than as an absence, and unlike a screen a downloaded file is not corrected
     * by a refresh.
     *
     * <h2>The address is deliberate, and it is a cost that was taken rather than a side effect</h2>
     *
     * <p>{@code DirectoryLinkResource}'s class javadoc permits a patient's address <b>on a screen</b>
     * partly because "retention is the collection, not a copy of it". A downloaded CSV is exactly a
     * copy with its own lifetime, so this decision does not follow from that one, and it is recorded
     * in both places rather than inferred in either. What makes it the right answer anyway is that
     * the alternatives are worse: a blank leaves the row anonymous (see {@link #IDENTITY_NOT_ON_FILE}),
     * and an id labelled as an id keeps the file and the screen disagreeing, which is the defect.
     * Item 43's rule is untouched — <b>never into a log, at any level</b> — and this reader is
     * narrower than the screen's, since the export is {@code ROLE_ADMIN} alone where the directory is
     * {@code ROLE_ADMIN} or {@code ROLE_OPERATOR}.
     *
     * <p><b>What this deliberately does not do is ask hc-patient for the name.</b> The console's list
     * sends {@code resolveNames=true} and shows a resolved name above the address (backlog item 50),
     * so on a stack where hc-patient answers, a row reading {@code kojo@jac.net} here reads
     * {@code Kojo Ampia-Addison} there. That residual is real and this change does not close it: a
     * live per-row lookup on a stream with no page size is the N-request fan-out item 53 refused, and
     * {@code DirectoryNameResolutionService} bounds itself per page — a bound an export has no page
     * to apply.
     *
     * <h2>Backlog item 70 settled that residual: the two tiers are both correct, and the difference
     * is asserted</h2>
     *
     * <p>The obvious close was to chunk {@code PatientResource}'s {@code mongoTemplate.stream} so the
     * per-page budget had a unit to spend against. <b>It was refused</b>, for three reasons worth
     * keeping because each of them is the kind that reads as an optimisation later: it puts a remote
     * fan-out on the one endpoint whose whole justification is being cheaper than paging the same
     * rows; a budget is a promise to a reader who is waiting, and nobody waits on a download the way
     * they wait on a screen, so the semantics would be invented rather than transferred; and it makes
     * a slow hc-patient a slow or truncated download, which is a worse failure than a file that names
     * by address.
     *
     * <p><b>⚠ Do not re-derive the cheap tier as expensive.</b> Item 53 deferred item 62 on an
     * "N-request fan-out" that conflated two different things — <em>resolving a name from
     * hc-patient</em> (HTTP, remote, worth a budget) with <em>reading the address off the link</em>
     * (Mongo, in process, one query for the whole file; see {@link LinkedIdentities}). Only the first
     * is expensive. That confusion cost this column a month with an ObjectId in it.
     *
     * <p>What item 70 required instead is that the difference be <b>checked rather than merely
     * written down here</b>: a javadoc is read by whoever is editing this method and by nobody who
     * moves the other tier. {@code PatientNamingTiersTest} hands one {@code DirectoryLink} to this
     * class and to {@code DirectoryNameResolutionService} and pins each tier in a case of its own,
     * then compares the two computed answers in a third — so a convergence in either direction is
     * reported, with a message saying which decision moved. {@code PatientNamingTiersIT} asks the
     * same of {@code GET /api/patients/export} and {@code GET /api/directory-links?resolveNames=true}
     * over a stored record. Both also pin the floor the tiers share: when hc-patient cannot name the
     * record, both say the address, which is why this one is narrower rather than worse.
     *
     * <p><b>Pin the two answers in separate cases if you add to either class.</b> Asserting "the file
     * says X", "the screen says Y" and "X ≠ Y" together makes the third follow from the two literals
     * rather than from the code — a tautology carrying the most important message in the class, which
     * is how the first draft of both was written.
     */
    private static String displayName(Patient patient, LinkedIdentities identities) {
        Profile profile = patient.getProfile();
        if (profile != null) {
            String name = Stream.of(profile.getFirstName(), profile.getLastName())
                .filter(Objects::nonNull)
                .filter(part -> !part.isBlank())
                .reduce((a, b) -> a + " " + b)
                .orElse("");
            if (!name.isBlank()) {
                return name;
            }
        }
        String linked = identities.identify(patient.getId());
        return linked == null ? IDENTITY_NOT_ON_FILE : linked;
    }

    /**
     * Whole years to {@code today}, or blank.
     *
     * <p>Blank rather than {@code 0}: a patient with no date of birth recorded is not a newborn, and
     * zero in a spreadsheet column will be averaged by somebody.
     */
    private static String age(Profile profile, LocalDate today) {
        LocalDate born = profile == null ? null : profile.getDateOfBirth();
        if (born == null || born.isAfter(today)) {
            return "";
        }
        return String.valueOf(java.time.temporal.ChronoUnit.YEARS.between(born, today));
    }

    /**
     * Town and city, else the region — the directory's {@code location} rule.
     *
     * <p>Written out rather than chained, because joining absent parts yields an empty string, which
     * is falsy but present: chained, a patient with an address holding only a country would export a
     * blank cell that reads as "no address recorded" instead of falling through to the region.
     */
    private static String location(Profile profile) {
        Address address = profile == null ? null : profile.getAddress();
        if (address == null) {
            return "";
        }
        String townAndCity = Stream.of(address.getTownDistrict(), address.getCityState())
            .filter(Objects::nonNull)
            .filter(part -> !part.isBlank())
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
        if (!townAndCity.isBlank()) {
            return townAndCity;
        }
        return text(address.getRegion());
    }

    /**
     * The lead's name, falling back to their licence number and then to nothing at all.
     *
     * <p>The console shows a licence number here until the names it fetches separately land, because
     * {@code Patient.clinicalLead} is serialised with {@code @JsonIgnoreProperties("profile")}. That
     * constraint is on the wire, not on the database: reading the profile through the reference is
     * free here, so the file carries the name the screen has to go and fetch.
     *
     * <p><strong>It never falls back to the lead's id.</strong> That is what it did until backlog
     * item 53, and the state is reachable rather than theoretical: no field in this service carries
     * {@code @NotBlank}, so {@code ""} passes {@code licenceNumber}'s {@code @NotNull} and is stored.
     * The cell then held a 24-character ObjectId, which reads as data — and this is a file an
     * administrator downloads, keeps and may forward, so unlike a screen it is not corrected by a
     * refresh. Item 45's rule is that an unresolved record is reported as unresolved, never named by
     * its key.
     *
     * <p><strong>Empty rather than an em dash, which is what the two console sites use.</strong> A
     * spreadsheet is not a screen. Every other absent value in this file is an empty cell — the id
     * number, the age, the sex, the location, the plan, both sponsor columns, and this column itself
     * in this method's first branch, when there is no lead at all. A dash would make this the only
     * column that marks absence with a character, and it would give one column two markers for two
     * states a reader cannot act on differently and that no CSV has a legend to explain. It would be a
     * value: it sorts with the names, survives {@code COUNTA}, defeats {@code ISBLANK} and a
     * filter's "Blanks", and a pivot table groups it beside real clinicians. That is a milder form of
     * the same "reads as data" failure the id had. Encoding is not the reason — the response carries
     * a UTF-8 BOM and would render the dash correctly.
     */
    private static String clinicalLead(Patient patient) {
        if (patient.getClinicalLead() == null) {
            return "";
        }
        Profile profile = patient.getClinicalLead().getProfile();
        if (profile != null) {
            String name = Stream.of(profile.getFirstName(), profile.getLastName())
                .filter(Objects::nonNull)
                .filter(part -> !part.isBlank())
                .reduce((a, b) -> a + " " + b)
                .orElse("");
            if (!name.isBlank()) {
                return name;
            }
        }
        String licence = patient.getClinicalLead().getLicenceNumber();
        return licence == null || licence.isBlank() ? "" : licence;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static void writeRow(Writer writer, List<String> cells) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(quote(cells.get(i)));
        }
        // CRLF, which is what RFC 4180 specifies and what Excel expects on every platform.
        writer.write(line.append("\r\n").toString());
    }

    /**
     * Quotes a cell, always.
     *
     * <p>Unconditional rather than only-when-needed. A name holding a comma, a quote or a newline
     * has to be quoted, and deciding per cell means a rule that is right until the first Ghanaian
     * address with a comma in it — which is most of them. Doubling the embedded quote is the whole
     * escape mechanism CSV has.
     */
    private static String quote(String cell) {
        return '"' + cell.replace("\"", "\"\"") + '"';
    }

    /**
     * The identities on the {@code HC_PATIENT} links, keyed by the record each one names.
     *
     * <h2>One read for the whole file, and none at all when the file needs none</h2>
     *
     * <p><b>This is the reason backlog item 53 deferred item 62 rather than fixing it cheaply</b>, so
     * it is the part to get right. The export is the one endpoint in this service with no page size:
     * a lookup per row is a fan-out that grows with the collection, on a handler whose whole
     * justification is that it is cheaper than paging the same rows out.
     *
     * <p>So the collection is read <b>once</b>, into a map, and it is read <b>lazily</b> — the first
     * row that has no {@code Profile} pays for it and every later one is free, and an export where
     * every patient is named never asks at all. That is the console's property exactly (its
     * {@code loadLinks} sends one request per page and none when the page is fully named), and here
     * it is worth more: today's directory is fifteen patients of whom three are nameless, so the
     * common export costs nothing it did not cost before.
     *
     * <p>Reading every {@code HC_PATIENT} link rather than the ids on this page is deliberate and is
     * the opposite of the console's choice, because the constraints are opposite. A screen knows its
     * twenty ids before it asks; a stream does not know its ids until it has finished, and gathering
     * them first is the materialisation this handler exists not to do. The collection is small — it
     * grows at the rate accounts are created on hc-patient, and the query is by {@code source} — and
     * one bounded read beats an unbounded number of small ones.
     *
     * <p><b>{@code HC_PATIENT} only, which is narrower than the console asks and is not a
     * disagreement.</b> {@code findByLocalIds} filters by id alone, so it would also match a link of
     * another source naming this id. No such link can exist: no {@code HC_PROFESSIONAL} link has a
     * {@code localId} at all, both types on that topic being {@code LINK_ONLY}, and if backlog item 35
     * ever gives them one it will name a {@code Professional} and not a {@code Patient}. Asking for
     * the source this file is about is the honest query; if that ever stops being true, this is the
     * line that has to change and the console's is not.
     */
    private static final class LinkedIdentities {

        private final DirectoryLinkRepository directoryLinkRepository;
        private Map<String, DirectoryLink> byLocalId;

        private LinkedIdentities(DirectoryLinkRepository directoryLinkRepository) {
            this.directoryLinkRepository = directoryLinkRepository;
        }

        /**
         * How this record's link names it, or {@code null} when nothing does.
         *
         * <p>A patient with no id cannot be looked up and does not provoke the read — matching
         * nothing is not a question worth a query, and the caller says "identity not on file" either
         * way.
         */
        private String identify(String localId) {
            if (localId == null || localId.isBlank()) {
                return null;
            }
            return identity(load().get(localId));
        }

        private Map<String, DirectoryLink> load() {
            if (byLocalId == null) {
                // Built by hand rather than with `toMap`, which throws on a duplicate key. Two links
                // claiming one record should not happen — `createAndClaim` claims atomically — but an
                // export is the wrong place to find out, and refusing the whole file over it would
                // turn a stale row into a failed download.
                byLocalId = new HashMap<>();
                for (DirectoryLink link : directoryLinkRepository.findBySource(DirectorySource.HC_PATIENT)) {
                    if (link.getLocalId() != null && !link.getLocalId().isBlank()) {
                        byLocalId.putIfAbsent(link.getLocalId(), link);
                    }
                }
            }
            return byLocalId;
        }

        /**
         * The console's {@code resolveLinkIdentity}: the address, then the login, then nothing.
         *
         * <p>{@code externalKey} is deliberately not a third fallback, for the reason that function
         * gives — for a patient it equals the address and adds nothing, and for anything else it is a
         * UUID, which is the unreadable-identifier-as-a-name defect this item removes, one field
         * along.
         *
         * <p>Written out rather than chained, because {@code strip()} yields {@code ""} for a
         * whitespace-only field: falsy to a reader, present to a null check, and a chain would export
         * it as somebody's name.
         */
        private static String identity(DirectoryLink link) {
            if (link == null) {
                return null;
            }
            String email = link.getEmail() == null ? null : link.getEmail().strip();
            if (email != null && !email.isEmpty()) {
                return email;
            }
            String login = link.getLogin() == null ? null : link.getLogin().strip();
            if (login != null && !login.isEmpty()) {
                return login;
            }
            return null;
        }
    }
}
