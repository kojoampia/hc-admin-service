package net.jojoaddison.service.dto;

import java.io.Serializable;
import java.util.List;
import net.jojoaddison.domain.enumeration.DirectorySource;

/**
 * How many accounts the sibling stacks have told this service about, split by whether they can sign
 * in — the estate-wide half of backlog item 75's dashboard.
 *
 * <h2>⚠ Three buckets, not two, and the third is the whole point</h2>
 *
 * <p>The item asked for "registrations aggregated by activated / not-activated". <b>That split is
 * wrong for this data and reproducing it would repeat a defect this service has already had.</b>
 * {@code DirectoryLink.activated} is a boxed {@code Boolean} precisely so it can be {@code null}, and
 * {@code null} is a real, common and permanent state rather than a pending one: {@code activated}
 * arrives only on hc-professional's {@code AccountCreated}, so a clinician known to this service
 * <em>only</em> from an {@code onboarding.state} frame has never been told either way. Counting them
 * as "not activated" would assert something no event has said.
 *
 * <p>That is not hypothetical. {@code DirectoryLink.activated}'s own javadoc records the previous
 * version of this mistake — the reconciliation read {@code state != "AccountCreated"} as activated,
 * so five event types rebuilt as {@code ACTIVE} while the consumer said {@code false} for the same
 * frames. The console's professional table already renders the three states as three, and
 * {@code dl-prof-unreported} exists in the {@code test} fixture so the third is reachable on a stack.
 * A two-bucket total beside a three-state table is the tile-disagreeing-with-the-rows-underneath-it
 * defect this dashboard has had twice.
 *
 * <h2>What is counted, and what is deliberately not filtered out</h2>
 *
 * <p><b>Every link, of every subject kind, including erased ones.</b> Two exclusions were considered
 * and both rejected for the same reason: this figure sits one click from
 * {@code GET /api/directory-links}, and a total that silently disagrees with the list it links to is
 * worse than a total that needs a sentence of explanation.
 *
 * <ul>
 *   <li><b>Care angels.</b> A nomination publishes {@code AccountCreated} keyed on the <em>angel's</em>
 *       address, with {@code ROLE_ANGEL}, and this service stores it as a link with no local record.
 *       It is an account somebody can sign in with, so it is a registration; it is not a patient, and
 *       {@link #bySource} does not claim it is one.</li>
 *   <li><b>Erased subjects.</b> {@code erased_at} marks a subject hc-patient has already deleted the
 *       profile for. They registered; the link is kept because it holds the watermark. Dropping them
 *       here would make this total fall when a deletion completes, which reads as data loss.</li>
 * </ul>
 *
 * <p>{@link #bySource} is what makes the shape legible rather than a footnote: almost every
 * {@code notReported} row is an {@code HC_PROFESSIONAL} one, and split by source that is obvious
 * instead of mysterious.
 *
 * <h2>Its authorities are the ordinary ones, and that is not an oversight</h2>
 *
 * <p>Admin-or-operator, from {@code SecurityConfiguration}'s blanket read/write split like every
 * other {@code GET} here. These are counts with no identifier in them at all — no address, no login,
 * no id — so nothing in {@code DirectoryLinkResource}'s retention argument applies. The gateway's
 * half of the same screen is {@code ROLE_ADMIN} alone, because that one names logins as they were
 * entered; the asymmetry is deliberate and each end says so.
 *
 * @param activated an event has said the account can sign in.
 * @param notActivated an event has said it cannot.
 * @param notReported <b>no event has said either way.</b> Not a synonym for {@code notActivated} —
 *     see above.
 * @param total every link this service holds. Carried rather than left to be summed, so a client
 *     that drops a bucket can see that it has.
 */
public record RegistrationTotalsDTO(
    long activated,
    long notActivated,
    long notReported,
    long total,
    List<SourceTotals> bySource
) implements Serializable {
    /**
     * The same three buckets for one sibling stack.
     *
     * <p>Present for every {@link DirectorySource} the enum has, including one this service has never
     * heard from — all zeroes rather than a missing row. A source that vanishes from a response is
     * indistinguishable on a screen from a source that is quiet, and those need opposite responses:
     * the first is a broken consumer, the second is a slow week.
     */
    public record SourceTotals(
        DirectorySource source,
        long activated,
        long notActivated,
        long notReported,
        long total
    ) implements Serializable {}
}
