package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import net.jojoaddison.domain.Address;
import net.jojoaddison.domain.Angel;
import net.jojoaddison.domain.Patient;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.ServicePlan;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.domain.enumeration.Sex;
import org.junit.jupiter.api.Test;

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

    private final PatientCsvExporter exporter = new PatientCsvExporter();

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
     * A patient whose profile has not been filled in still identifies itself.
     *
     * <p>Falling back to the id rather than leaving the first column blank: mid-intake is a real
     * state, and a file where some rows have no identifier at all cannot be acted on — there is
     * nothing to click through to.
     */
    @Test
    void fallsBackToTheIdWhenThereIsNoName() throws Exception {
        Patient patient = new Patient().status(AccountStatus.PENDING);
        patient.setId("p-42");

        List<String> lines = write(patient);

        assertThat(lines.get(1)).startsWith("\"p-42\"");
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
        patient.setProfile(new Profile().firstName("Ama").lastName("Boateng").dateOfBirth(LocalDate.of(1986, 8, 25)).sex(Sex.FEMALE));

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

    /** Splits a fully-quoted row back into its cells. */
    private static List<String> cells(String line) {
        String inner = line.substring(1, line.length() - 1);
        return List.of(inner.split("\",\"", -1));
    }
}
