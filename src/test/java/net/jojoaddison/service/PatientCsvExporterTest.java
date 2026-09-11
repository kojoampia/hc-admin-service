package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.StringWriter;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.jojoaddison.domain.Address;
import net.jojoaddison.domain.Angel;
import net.jojoaddison.domain.CareActivity;
import net.jojoaddison.domain.DirectoryLink;
import net.jojoaddison.domain.Document;
import net.jojoaddison.domain.Hub;
import net.jojoaddison.domain.Patient;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.ServicePlan;
import net.jojoaddison.domain.Team;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.domain.enumeration.Sex;
import net.jojoaddison.repository.DirectoryLinkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.mapping.DBRef;

/**
 * The shaping rules for the patient export, away from a database.
 *
 * <p>{@link PatientExportIT} covers what the endpoint returns and which rows it selects. What is
 * asserted here is the per-cell behaviour, which is where the mistakes are: quoting, the fallbacks
 * for a record that is only half filled in, and the difference between an empty cell and a zero.
 * None of it needs Mongo, and an IT would exercise it one row at a time.
 */
class PatientCsvExporterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 24);

    private final CountingLinks links = new CountingLinks();

    private final PatientCsvExporter exporter = new PatientCsvExporter(links.repository());

    @Test
    void writesTheHeaderEvenWithNoRows() throws Exception {
        List<String> lines = write();

        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst()).startsWith("\"Patient\",\"Id number\",\"Age\"");
    }

    /**
     * Every cell is quoted, not only the ones that need it.
     *
     * <p>A per-cell decision is right until the first address holding a comma, which in Accra is
     * most of them. Quoting unconditionally has no failure mode; deciding has one that produces a
     * file that opens fine and has its columns shifted by one from the first such row down.
     */
    @Test
    void quotesEveryCellAndDoublesEmbeddedQuotes() throws Exception {
        Patient patient = new Patient().status(AccountStatus.ACTIVE);
        patient.setProfile(new Profile().firstName("Ama \"Maa\"").lastName("Boateng"));

        List<String> lines = write(patient);

        assertThat(lines.get(1)).startsWith("\"Ama \"\"Maa\"\" Boateng\",\"\",");
    }

    /** A name with a comma stays one cell. */
    @Test
    void aCommaInAValueDoesNotBecomeANewColumn() throws Exception {
        Patient patient = new Patient().status(AccountStatus.ACTIVE);
        Address address = new Address().townDistrict("Osu, Ako Adjei").cityState("Accra");
        patient.setProfile(new Profile().firstName("Ama").lastName("Boateng").address(address));

        List<String> lines = write(patient);

        assertThat(lines.get(1)).contains("\"Osu, Ako Adjei, Accra\"");
        // Fourteen columns, so thirteen separators outside the quotes — counted via the split a
        // naive reader would do, which is exactly the one that breaks when quoting is conditional.
        assertThat(lines.get(1).split("\",\"")).hasSize(PatientCsvExporter.COLUMNS.size());
    }

    /**
     * A patient learned from a sibling event exports the address off its link, as the console shows
     * it — backlog item 62.
     *
     * <p>This case replaced {@code fallsBackToTheIdWhenThereIsNoName}, which asserted the defect: the
     * first column held the record's own id, so the same three rows read {@code a13} in the file and
     * {@code naa.adjeley@mail.gh} on the screen, and nothing anywhere reported that the two
     * disagreed.
     */
    @Test
    void aLearnedPatientIsNamedByTheAddressOnItsLink() throws Exception {
        links.save("p-42", "naa.adjeley@mail.gh", null);
        Patient patient = new Patient().status(AccountStatus.PENDING);
        patient.setId("p-42");

        List<String> lines = write(patient);

        assertThat(cells(lines.get(1)).getFirst()).isEqualTo("naa.adjeley@mail.gh");
        assertThat(lines.get(1)).doesNotContain("p-42");
    }

    /**
     * A profile still wins over a link, which is the common row and the one a regression here would
     * be least visible on.
     *
     * <p>Asserted with a link present rather than absent: with no link the profile branch is the only
     * one that can answer, so the case would pass against an implementation that had the precedence
     * exactly backwards.
     */
    @Test
    void aPatientWithAProfileIsNamedByItEvenWhenALinkAlsoNamesThem() throws Exception {
        links.save("p-7", "ama.b@mail.gh", null);
        Patient patient = new Patient().status(AccountStatus.ACTIVE);
        patient.setId("p-7");
        patient.setProfile(new Profile().firstName("Ama").lastName("Boateng"));

        List<String> lines = write(patient);

        assertThat(cells(lines.get(1)).getFirst()).isEqualTo("Ama Boateng");
    }

    /**
     * A link with no address falls through to the login, which is what the console's
     * {@code resolveLinkIdentity} does one branch down.
     *
     * <p>Reachable rather than theoretical: an {@code onboarding.state} frame carries no email at
     * all, which is the state the {@code dl-prof-anon} fixture row exists for. Matching the screen
     * here is the whole of this item — a fallback the console has and the file does not is the same
     * disagreement one branch along.
     */
    @Test
    void aLinkWithNoAddressFallsThroughToTheLogin() throws Exception {
        links.save("p-43", "   ", "nadjeley");
        Patient patient = new Patient().status(AccountStatus.PENDING);
        patient.setId("p-43");

        List<String> lines = write(patient);

        assertThat(cells(lines.get(1)).getFirst()).isEqualTo("nadjeley");
    }

    /**
     * A patient with neither profile nor link says so in words.
     *
     * <p><b>Words rather than the empty cell every other absent value in this file uses</b>, and the
     * exception is argued at {@code displayName}: this is the row's only identity column, so a blank
     * makes the row anonymous rather than incomplete. {@code a14} is that state in the {@code test}
     * fixture, and it is permanent rather than pending.
     */
    @Test
    void aPatientNothingCanNameSaysSoRatherThanCarryingItsId() throws Exception {
        Patient patient = new Patient().status(AccountStatus.PENDING);
        patient.setId("68b4f2a19c3d5e7f81a02c44");

        List<String> lines = write(patient);

        assertThat(cells(lines.get(1)).getFirst()).isEqualTo(PatientCsvExporter.IDENTITY_NOT_ON_FILE);
        assertThat(lines.get(1)).doesNotContain("68b4f2a19c3d5e7f81a02c44");
    }

    /**
     * <b>No cell in the file is any patient's id, on any row.</b>
     *
     * <p>The defect's own shape rather than a restatement of the three cases above: they each pin one
     * branch, and this reads every cell of every row against every id in the export. An id moving from
     * the first column to another one — or a fourth branch acquiring the old fallback — passes all
     * three and fails this.
     *
     * <p>Deliberately over a mixed set. A sweep over nameless rows alone would go green the day the
     * fallback moved onto the named path instead.
     */
    @Test
    void noCellInTheFileIsAPatientsOwnId() throws Exception {
        links.save("a13", "naa.adjeley@mail.gh", null);
        Patient named = new Patient().status(AccountStatus.ACTIVE);
        named.setId("a12");
        named.setProfile(new Profile().firstName("Ama").lastName("Boateng"));
        Patient linked = new Patient().status(AccountStatus.PENDING);
        linked.setId("a13");
        Patient unknown = new Patient().status(AccountStatus.PENDING);
        unknown.setId("a14");

        List<String> lines = write(named, linked, unknown);

        List<String> ids = List.of("a12", "a13", "a14");
        for (String line : lines.subList(1, lines.size())) {
            assertThat(cells(line)).as("a patient's id reached the file: %s", line).doesNotContainAnyElementsOf(ids);
        }
    }

    /**
     * <b>No cell of this file is the raw id of any record the exported row can reach</b> — backlog
     * item 69, and the server-side half of item 45's rule.
     *
     * <h2>What this adds over the three cases above it</h2>
     *
     * <p>Item 45's rule is <em>never render a record's id where its name goes</em>, and on the
     * console it is swept by {@code record-identity.spec.ts}, which walks every screen and fails on
     * an id cut into initials. <b>That sweep is a Vitest spec over Angular templates and
     * structurally cannot see Java.</b> It has now been breached twice on this side, both times in
     * this class — item 53 in the clinical-lead column, item 62 in the patient column — and both
     * times by a person looking rather than by a check.
     *
     * <p>The three cases above pin the three branches of {@link PatientCsvExporter#displayName}, and
     * {@code noCellInTheFileIsAPatientsOwnId} widens that to every cell of every row. All four are
     * about the <b>patient's own</b> id. This one is about <b>every record the row reaches</b>: the
     * profile, its address, the plan, the sponsor, the clinical lead and the lead's own profile, plus
     * the hub, the documents and the care activities no column reads today. A fourteenth column added
     * tomorrow that names a related record by its key — which is precisely what item 53 was — fails
     * here, in a message that says which column it is.
     *
     * <h2>Why the output rather than the source</h2>
     *
     * <p>An id where a name goes has no syntactic signature in Java: {@code text(x.getId())} and
     * {@code text(x.getName())} are the same expression with a different method on it. Items 57 and
     * 59 are this estate's record of what happens to checks that read source text — one discriminator
     * defeated by a Prettier line break, another by a {@code );} inside a string literal — so this
     * reads the bytes the exporter writes and compares them against ids it put into the fixture.
     * There is nothing here for a formatter to defeat.
     *
     * <h2>The fixture's breadth is derived from {@link Patient}, and its depth is not</h2>
     *
     * <p>{@link #everyRelationshipOnAPatientIsWiredIntoTheSweep} is the half that keeps this from
     * quietly narrowing: it reads {@code Patient}'s own {@code @DBRef} fields and fails if the
     * fixture leaves one unset, so a relationship added to the domain model is in the sweep the day
     * it exists rather than the day somebody remembers. <b>Depth is not derived</b>, and the residual
     * is stated rather than assumed away, per item 59's lesson that a limit should be measured before
     * it is accepted: every field this exporter reads today is at most two hops from the patient, and
     * every object on those two hops carries an id below. The hole is a future column reading a third
     * hop — {@code clinicalLead.getTeam().getId()}, say. Closing it is one more line in
     * {@link #fullyRelatedPatient}, not a redesign.
     */
    @Test
    void noCellInTheFileIsTheIdOfAnyRecordTheRowReaches() throws Exception {
        links.save(NAMELESS_ID, "naa.adjeley@mail.gh", null);
        Patient nameless = fullyRelatedPatient(NAMELESS_ID, "nameless", null, null);
        // A named row in the same file, so a fallback that moves onto the named path is caught too —
        // a sweep over nameless rows alone goes green the day the defect changes branches.
        Patient named = fullyRelatedPatient(NAMED_ID, "named", "Ama", "Boateng");

        List<String> lines = write(nameless, named);

        Map<String, String> ids = idsReachableFrom(nameless, named);
        // The fixture has to be carrying ids at all, or every assertion below is vacuous. Counted
        // against the graph rather than a literal, so it grows with `fullyRelatedPatient`.
        assertThat(ids).as("the sweep found no ids in its own fixture, so it would pass against anything").hasSizeGreaterThan(10);
        for (int row = 1; row < lines.size(); row++) {
            List<String> cells = cells(lines.get(row));
            for (int column = 0; column < cells.size(); column++) {
                String whatTheIdNames = ids.get(cells.get(column));
                if (whatTheIdNames != null) {
                    fail(
                        "Column \"%s\" of row %d holds the raw id of a %s (%s). Backlog item 45: an unresolved record is " +
                            "reported as unresolved, never named by its key — see PatientCsvExporter.displayName.",
                        PatientCsvExporter.COLUMNS.get(column),
                        row,
                        whatTheIdNames,
                        cells.get(column)
                    );
                }
            }
        }
        // And the rows really were exported, so the loop above ran over something. Named by the two
        // identities the file is supposed to carry rather than by a count.
        assertThat(cells(lines.get(1)).getFirst()).isEqualTo("naa.adjeley@mail.gh");
        assertThat(cells(lines.get(2)).getFirst()).isEqualTo("Ama Boateng");
    }

    /**
     * Every relationship a {@link Patient} declares is wired into the sweep's fixture.
     *
     * <p>This is what stops {@link #noCellInTheFileIsTheIdOfAnyRecordTheRowReaches} from narrowing
     * silently. A list of relationships maintained by hand is the shape {@code PaginationIT} was
     * rewritten to remove: it stops covering things the moment one is added, and nothing reports it.
     * So the fixture is checked against {@code Patient}'s own {@code @DBRef} fields, and adding a
     * relationship to the domain model fails here until it is given an id in
     * {@link #fullyRelatedPatient}.
     *
     * <p>Read off the field annotation rather than the getters, because {@code @DBRef} is what makes
     * a field a separate document with an id of its own — which is the only kind of value this rule
     * is about.
     */
    @Test
    void everyRelationshipOnAPatientIsWiredIntoTheSweep() throws Exception {
        Patient patient = fullyRelatedPatient(NAMELESS_ID, "nameless", null, null);

        int relationships = 0;
        for (Field field : Patient.class.getDeclaredFields()) {
            if (!field.isAnnotationPresent(DBRef.class)) {
                continue;
            }
            relationships++;
            Object value = read(field, patient);
            assertThat(value)
                .as(
                    "Patient.%s is a relationship the sweep's fixture leaves unset, so a column naming it by its id " +
                        "would export one and nothing here would see it — give it an id in fullyRelatedPatient()",
                    field.getName()
                )
                .isNotNull();
            if (value instanceof Collection<?> elements) {
                assertThat(elements)
                    .as("Patient.%s is empty in the sweep's fixture, which covers it no better than leaving it null", field.getName())
                    .isNotEmpty();
            }
        }
        // A loop that iterates nothing passes every assertion inside it. Asserted as a floor rather
        // than a figure, because this test's job is to notice relationships being added.
        assertThat(relationships)
            .as("no @DBRef field was found on Patient, so this test and the sweep it guards assert nothing")
            .isGreaterThanOrEqualTo(7);
    }

    /**
     * The links are read <b>once for the whole file</b>, not once per row.
     *
     * <p>Counted rather than asserted in prose, because this is an export with no page size: the one
     * endpoint here where a per-row read is a fan-out over the whole collection, and the reason item
     * 53 deferred this rather than fixing it cheaply. The stub below records every call it receives,
     * so a lookup moved inside the loop fails here with a number rather than passing silently and
     * slowly.
     */
    @Test
    void theLinksAreReadOnceForTheWholeFileRatherThanOncePerRow() throws Exception {
        links.save("a13", "naa.adjeley@mail.gh", null);
        links.save("a15", "kojo@jac.net", null);

        write(nameless("a13"), nameless("a14"), nameless("a15"));

        assertThat(links.reads()).isEqualTo(1);
    }

    /**
     * And not read at all when every row already has a name.
     *
     * <p>The same property the console has — one request per page, none when the page is fully named
     * — and the half that a "one query" assertion alone does not cover. It matters most on the export
     * a real directory produces, where the learned rows are the minority: a directory of named
     * patients costs this endpoint exactly what it cost before item 62.
     */
    @Test
    void theLinksAreNotReadAtAllWhenEveryRowHasAProfile() throws Exception {
        Patient patient = new Patient().status(AccountStatus.ACTIVE);
        patient.setId("a12");
        patient.setProfile(new Profile().firstName("Ama").lastName("Boateng"));

        write(patient);

        assertThat(links.reads()).isZero();
    }

    /** A patient with no name and no id cannot be looked up, and must not be reported as one. */
    @Test
    void aPatientWithNoIdIsUnidentifiedRatherThanBlank() throws Exception {
        List<String> lines = write(new Patient().status(AccountStatus.PENDING));

        assertThat(cells(lines.get(1)).getFirst()).isEqualTo(PatientCsvExporter.IDENTITY_NOT_ON_FILE);
    }

    /**
     * No date of birth exports blank, not zero.
     *
     * <p>Zero is a number, and a column of ages with zeros in it will be averaged by somebody. The
     * absence has to survive into the file as an absence.
     */
    @Test
    void anUnknownAgeIsBlankRatherThanZero() throws Exception {
        Patient patient = new Patient().status(AccountStatus.ACTIVE);
        patient.setProfile(new Profile().firstName("Ama").lastName("Boateng"));

        List<String> lines = write(patient);

        assertThat(cells(lines.get(1)).get(2)).isEmpty();
    }

    @Test
    void ageIsWholeYearsAtTheGivenDate() throws Exception {
        Patient patient = new Patient().status(AccountStatus.ACTIVE);
        // Birthday tomorrow: still 39, which is the case a naive year subtraction gets wrong.
        patient.setProfile(
            new Profile()
                .firstName("Ama")
                .lastName("Boateng")
                .dateOfBirth(LocalDate.of(1986, 8, 25))
                .sex(Sex.FEMALE)
        );

        List<String> lines = write(patient);

        assertThat(cells(lines.get(1)).get(2)).isEqualTo("39");
        assertThat(cells(lines.get(1)).get(3)).isEqualTo("FEMALE");
    }

    /**
     * An address with only a region falls through to it rather than exporting blank.
     *
     * <p>The town-and-city join returns an empty string when both parts are absent, and an empty
     * string is present but useless. Chained with a null-coalescing operator it would win, and the
     * cell would read as "no address recorded" for a patient who has one.
     */
    @Test
    void locationFallsThroughToTheRegion() throws Exception {
        Patient patient = new Patient().status(AccountStatus.ACTIVE);
        patient.setProfile(new Profile().firstName("Ama").lastName("Boateng").address(new Address().region("Ashanti")));

        List<String> lines = write(patient);

        assertThat(cells(lines.get(1)).get(4)).isEqualTo("Ashanti");
    }

    /**
     * The clinical lead exports as a name.
     *
     * <p>The console shows a licence number here until the names it fetches separately arrive,
     * because the reference is serialised without its profile. That is a constraint on the wire, not
     * on the database, so the file is allowed to be better than the screen — and this asserts it is.
     */
    @Test
    void theClinicalLeadExportsAsANameNotALicenceNumber() throws Exception {
        Professional lead = new Professional().licenceNumber("MDC-9912");
        lead.setProfile(new Profile().firstName("Nii").lastName("Osae"));

        Patient patient = new Patient().status(AccountStatus.ACTIVE);
        patient.setProfile(new Profile().firstName("Ama").lastName("Boateng"));
        patient.setClinicalLead(lead);

        List<String> lines = write(patient);

        assertThat(cells(lines.get(1)).get(8)).isEqualTo("Nii Osae");
    }

    /** With no profile on the lead, the licence number is better than an id and far better than blank. */
    @Test
    void theClinicalLeadFallsBackToTheLicenceNumber() throws Exception {
        Patient patient = new Patient().status(AccountStatus.ACTIVE);
        patient.setProfile(new Profile().firstName("Ama").lastName("Boateng"));
        patient.setClinicalLead(new Professional().licenceNumber("MDC-9912"));

        List<String> lines = write(patient);

        assertThat(cells(lines.get(1)).get(8)).isEqualTo("MDC-9912");
    }

    /**
     * A lead with nothing to name it by exports an empty cell, never its id.
     *
     * <p>{@code licenceNumber} is {@code @NotNull} without {@code @NotBlank} — no field in this
     * service carries {@code @NotBlank} at all — so {@code ""} passes the validator on the way in and
     * is a state the database really holds. The old answer to that was the lead's id, which is a
     * 24-character ObjectId in production. In a file somebody keeps and forwards that reads as data
     * rather than as an absence, which is the whole of backlog item 45's rule.
     *
     * <p>Empty rather than a dash, for the reason argued at {@code clinicalLead}: thirteen of the
     * fourteen columns already say "not recorded" by being empty, and this column itself already does
     * for a patient with no lead at all.
     */
    @Test
    void aLeadWithABlankLicenceExportsAnEmptyCellRatherThanItsId() throws Exception {
        Professional lead = new Professional().licenceNumber("");
        lead.setId("68b4f2a19c3d5e7f81a02c44");

        Patient patient = new Patient().status(AccountStatus.ACTIVE);
        patient.setProfile(new Profile().firstName("Ama").lastName("Boateng"));
        patient.setClinicalLead(lead);

        List<String> lines = write(patient);

        assertThat(cells(lines.get(1)).get(8)).isEmpty();
        // Read across the whole row, not only the cell: an id leaking into any column of a file that
        // leaves the building is the defect, and the cell assertion alone would not see it move.
        assertThat(lines.get(1)).doesNotContain("68b4f2a19c3d5e7f81a02c44");
    }

    /**
     * A null licence and a blank one are the same absence, and both stay out of the file.
     *
     * <p>Asserted separately because the storage treats them differently — {@code @NotNull} refuses
     * one and admits the other — while the reader of the file cannot tell them apart and should not
     * have to. A guard written for only one of the two is the shape this defect already had.
     */
    @Test
    void aLeadWithNoLicenceAtAllExportsAnEmptyCellRatherThanItsId() throws Exception {
        Professional lead = new Professional();
        lead.setId("68b4f2a19c3d5e7f81a02c44");

        Patient patient = new Patient().status(AccountStatus.ACTIVE);
        patient.setProfile(new Profile().firstName("Ama").lastName("Boateng"));
        patient.setClinicalLead(lead);

        List<String> lines = write(patient);

        assertThat(cells(lines.get(1)).get(8)).isEmpty();
        assertThat(lines.get(1)).doesNotContain("68b4f2a19c3d5e7f81a02c44");
    }

    /**
     * A licence of nothing but spaces is an absence too, and does not export as spaces.
     *
     * <p>This is the case that makes the {@code isBlank()} half of the guard load-bearing. With the
     * fallback gone, {@code ""} and a null licence both render empty whether or not blankness is
     * checked, so neither of the two cases above can tell the halves apart — dropping
     * {@code isBlank()} keeps them both green. A whitespace licence is exactly as storable as an
     * empty one ({@code @Size(max = 40)} counts spaces, {@code @NotNull} admits them) and without the
     * check it exports a cell containing spaces: not empty, not a licence, and invisible to whoever
     * opens the file.
     */
    @Test
    void aLeadWhoseLicenceIsOnlyWhitespaceExportsAnEmptyCell() throws Exception {
        Patient patient = new Patient().status(AccountStatus.ACTIVE);
        patient.setProfile(new Profile().firstName("Ama").lastName("Boateng"));
        patient.setClinicalLead(new Professional().licenceNumber("   "));

        List<String> lines = write(patient);

        assertThat(cells(lines.get(1)).get(8)).isEmpty();
    }

    /**
     * One absence marker in the column, not two.
     *
     * <p>"No clinical lead" and "a clinical lead with nothing to name it by" are different facts
     * about the record and the same fact about the cell: it does not tell the reader who the lead is.
     * A CSV carries no legend that could explain a second marker, so the two render identically —
     * and this is the assertion that fails if somebody later reaches for a dash on one of them.
     */
    @Test
    void anUnnameableLeadReadsTheSameAsNoLeadAtAll() throws Exception {
        Patient withNoLead = new Patient().status(AccountStatus.ACTIVE);
        withNoLead.setProfile(new Profile().firstName("Ama").lastName("Boateng"));

        Professional lead = new Professional().licenceNumber("");
        lead.setId("68b4f2a19c3d5e7f81a02c44");
        Patient withAnUnnameableLead = new Patient().status(AccountStatus.ACTIVE);
        withAnUnnameableLead.setProfile(new Profile().firstName("Ama").lastName("Boateng"));
        withAnUnnameableLead.setClinicalLead(lead);

        List<String> lines = write(withNoLead, withAnUnnameableLead);

        assertThat(cells(lines.get(2)).get(8)).isEqualTo(cells(lines.get(1)).get(8));
    }

    @Test
    void carriesThePlanTheSponsorAndTheStatus() throws Exception {
        Patient patient = new Patient().status(AccountStatus.SUSPENDED).caseCount(3);
        patient.setProfile(new Profile().firstName("Ama").lastName("Boateng"));
        patient.setPlan(new ServicePlan().name("PAWPAW Plan"));
        patient.setAngel(new Angel().name("Kofi Boateng").relationship("Son"));

        List<String> cells = cells(write(patient).get(1));

        assertThat(cells.get(5)).isEqualTo("PAWPAW Plan");
        assertThat(cells.get(6)).isEqualTo("Kofi Boateng");
        assertThat(cells.get(7)).isEqualTo("Son");
        assertThat(cells.get(9)).isEqualTo("SUSPENDED");
        assertThat(cells.get(12)).isEqualTo("3");
    }

    /**
     * Rows end CRLF, which is what RFC 4180 says and what Excel expects on every platform.
     *
     * <p>Asserted because a bare newline works everywhere a developer looks at the file and fails in
     * the one place it is going to be opened.
     */
    @Test
    void rowsEndWithCrLf() throws Exception {
        StringWriter writer = new StringWriter();
        exporter.write(writer, Stream.of(new Patient().status(AccountStatus.ACTIVE)), TODAY);

        assertThat(writer.toString()).endsWith("\r\n");
        assertThat(writer.toString().replace("\r\n", "")).doesNotContain("\n");
    }

    private List<String> write(Patient... patients) throws Exception {
        StringWriter writer = new StringWriter();
        exporter.write(writer, Stream.of(patients), TODAY);
        return List.of(writer.toString().split("\r\n"));
    }

    /** A patient with an id and nothing else — the shape a sibling event leaves behind. */
    private static Patient nameless(String id) {
        Patient patient = new Patient().status(AccountStatus.PENDING);
        patient.setId(id);
        return patient;
    }

    /**
     * The two record ids {@link #noCellInTheFileIsTheIdOfAnyRecordTheRowReaches} sweeps for.
     *
     * <p>Twenty-four hex characters, which is what a Mongo {@code ObjectId} is and what an
     * administrator saw in production. Short ids like {@code a13} would work for the equality
     * assertion and would understate what the defect looks like in a spreadsheet — the whole reason
     * item 45 calls it unreadable rather than merely wrong.
     */
    private static final String NAMELESS_ID = "68b4f2a19c3d5e7f81a02c13";

    private static final String NAMED_ID = "68b4f2a19c3d5e7f81a02c12";

    /**
     * A patient wired to every record {@link Patient} declares a relationship to, each carrying an
     * id of its own.
     *
     * <p>Built for {@link #noCellInTheFileIsTheIdOfAnyRecordTheRowReaches}, so what matters is that
     * every related document has an <b>id</b> — the values beside it are only there to keep the
     * exported cells readable. Relationships no column reads today (the hub, the documents, the care
     * activities) are wired anyway: the sweep is about the column somebody adds next, and a
     * relationship left out of the fixture is one that column could name by its key for free.
     *
     * @param firstName and {@code lastName} null for the nameless row, which is the state that sends
     *     {@code displayName} down its fallbacks and the one both breaches were found on.
     */
    private static Patient fullyRelatedPatient(String id, String tag, String firstName, String lastName) {
        Address address = new Address().townDistrict("Osu").cityState("Accra").region("Greater Accra");
        address.setId("id-address-" + tag);
        Profile profile = new Profile().firstName(firstName).lastName(lastName).idNumber("GHA-000000-0").address(address);
        profile.setId("id-profile-" + tag);

        Profile leadProfile = new Profile().firstName("Nii").lastName("Osae");
        leadProfile.setId("id-lead-profile-" + tag);
        Professional lead = new Professional().licenceNumber("MDC-9912").profile(leadProfile);
        lead.setId("id-lead-" + tag);

        ServicePlan plan = new ServicePlan().name("PAWPAW Plan");
        plan.setId("id-plan-" + tag);
        Angel angel = new Angel().name("Kofi Boateng").relationship("Son");
        angel.setId("id-angel-" + tag);
        Hub hub = new Hub().name("Accra Central");
        hub.setId("id-hub-" + tag);

        Patient patient = new Patient().status(AccountStatus.ACTIVE).caseCount(2);
        patient.setId(id);
        patient.setProfile(profile);
        patient.setClinicalLead(lead);
        patient.setPlan(plan);
        patient.setAngel(angel);
        patient.setHub(hub);
        patient.setDocuments(Set.of(new Document().id("id-document-" + tag)));
        patient.setCareActivities(Set.of(new CareActivity().id("id-activity-" + tag)));
        return patient;
    }

    /**
     * Every id reachable from the exported records, and what each one names.
     *
     * <p>Walked rather than listed, following the {@code @DBRef} fields that make a value a separate
     * document. That is the whole difference between this and a check somebody has to extend: the
     * set grows with the fixture and with the domain model, and {@code Angel.patient} and
     * {@code Profile.patient} point back at the root, so the walk has to tolerate cycles rather than
     * assume a tree.
     *
     * <p>Keyed by the id so the assertion is a map lookup per cell rather than a scan, and valued by
     * the class name so the failure can say <em>whose</em> id reached the file — "a Professional"
     * sends a reader to the clinical-lead column, where "an id" sends them nowhere.
     */
    private static Map<String, String> idsReachableFrom(Object... roots) {
        Map<String, String> found = new LinkedHashMap<>();
        Set<Object> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object root : roots) {
            collectIds(root, seen, found);
        }
        return found;
    }

    private static void collectIds(Object value, Set<Object> seen, Map<String, String> found) {
        if (value == null || !value.getClass().getName().startsWith("net.jojoaddison.domain.") || !seen.add(value)) {
            return;
        }
        Object id = invokeGetId(value);
        if (id instanceof String text && !text.isBlank()) {
            found.putIfAbsent(text, value.getClass().getSimpleName());
        }
        for (Field field : value.getClass().getDeclaredFields()) {
            if (!field.isAnnotationPresent(DBRef.class)) {
                continue;
            }
            Object related = read(field, value);
            if (related instanceof Collection<?> elements) {
                elements.forEach(element -> collectIds(element, seen, found));
            } else {
                collectIds(related, seen, found);
            }
        }
    }

    private static Object invokeGetId(Object value) {
        try {
            return value.getClass().getMethod("getId").invoke(value);
        } catch (ReflectiveOperationException e) {
            // A domain class with no getId() is not a document and has no key to leak.
            return null;
        }
    }

    private static Object read(Field field, Object target) {
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("could not read " + field, e);
        }
    }

    /**
     * The link collection, and a count of how many times it was asked.
     *
     * <p>The count is the assertion {@link #theLinksAreReadOnceForTheWholeFileRatherThanOncePerRow}
     * and {@link #theLinksAreNotReadAtAllWhenEveryRowHasAProfile} are made of, and it is a count
     * rather than a Mockito {@code verify} so that a regression reports <em>how many</em> reads there
     * were. "Wanted 1, was 3" names a per-row lookup; "wanted 1, was 15" on a real directory would be
     * the fan-out this endpoint has no page size to bound.
     */
    private static final class CountingLinks {

        private final List<DirectoryLink> stored = new ArrayList<>();
        private final DirectoryLinkRepository repository = mock(DirectoryLinkRepository.class);
        private int reads;

        private CountingLinks() {
            when(repository.findBySource(any())).thenAnswer(invocation -> {
                reads++;
                DirectorySource source = invocation.getArgument(0);
                return stored
                    .stream()
                    .filter(link -> link.getSource() == source)
                    .toList();
            });
        }

        /** One HC_PATIENT link naming a local record, as the projection would have written it. */
        void save(String localId, String email, String login) {
            DirectoryLink link = new DirectoryLink();
            link.setSource(DirectorySource.HC_PATIENT);
            link.setExternalKey(email);
            link.setLocalId(localId);
            link.setEmail(email);
            link.setLogin(login);
            stored.add(link);
        }

        DirectoryLinkRepository repository() {
            return repository;
        }

        int reads() {
            return reads;
        }
    }

    /** Splits a fully-quoted row back into its cells. */
    private static List<String> cells(String line) {
        String inner = line.substring(1, line.length() - 1);
        return List.of(inner.split("\",\"", -1));
    }
}
