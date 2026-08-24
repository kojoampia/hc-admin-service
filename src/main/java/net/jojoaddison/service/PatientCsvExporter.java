package net.jojoaddison.service;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import net.jojoaddison.domain.Address;
import net.jojoaddison.domain.Patient;
import net.jojoaddison.domain.Profile;
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
 */
@Service
public class PatientCsvExporter {

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
        // The stream holds a cursor. Closing it is the caller's job in principle, but a partial
        // write throws from inside the loop and would leak it, so it is closed here where it is read.
        try (Stream<Patient> rows = patients) {
            for (Patient patient : (Iterable<Patient>) rows::iterator) {
                writeRow(writer, row(patient, today));
            }
        }
    }

    private List<String> row(Patient patient, LocalDate today) {
        Profile profile = patient.getProfile();
        return List.of(
            displayName(patient),
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
     * First and last name, falling back to the id.
     *
     * <p>The same rule as the directory's {@code displayName}, and the fallback matters for the same
     * reason: a patient whose profile has not been filled in yet is a real state, and a blank first
     * column would make that row unidentifiable in a file nobody can click through.
     */
    private static String displayName(Patient patient) {
        Profile profile = patient.getProfile();
        if (profile != null) {
            String name = Stream
                .of(profile.getFirstName(), profile.getLastName())
                .filter(Objects::nonNull)
                .filter(part -> !part.isBlank())
                .reduce((a, b) -> a + " " + b)
                .orElse("");
            if (!name.isBlank()) {
                return name;
            }
        }
        return text(patient.getId());
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
        String townAndCity = Stream
            .of(address.getTownDistrict(), address.getCityState())
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
     * The lead's name, falling back to their licence number and then their id.
     *
     * <p>The console shows a licence number here until the names it fetches separately land, because
     * {@code Patient.clinicalLead} is serialised with {@code @JsonIgnoreProperties("profile")}. That
     * constraint is on the wire, not on the database: reading the profile through the reference is
     * free here, so the file carries the name the screen has to go and fetch.
     */
    private static String clinicalLead(Patient patient) {
        if (patient.getClinicalLead() == null) {
            return "";
        }
        Profile profile = patient.getClinicalLead().getProfile();
        if (profile != null) {
            String name = Stream
                .of(profile.getFirstName(), profile.getLastName())
                .filter(Objects::nonNull)
                .filter(part -> !part.isBlank())
                .reduce((a, b) -> a + " " + b)
                .orElse("");
            if (!name.isBlank()) {
                return name;
            }
        }
        String licence = patient.getClinicalLead().getLicenceNumber();
        return licence == null || licence.isBlank() ? text(patient.getClinicalLead().getId()) : licence;
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
}
