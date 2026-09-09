package net.jojoaddison.service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.jojoaddison.domain.DirectoryLink;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.domain.enumeration.NameResolution;
import net.jojoaddison.service.PatientServiceClient.ResolvedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Names one page of directory links from the stack that owns the names — backlog item 50.
 *
 * <p>The rule about <em>which</em> links are worth a lookup lives here rather than in
 * {@link PatientServiceClient} (which knows how to ask one question) or in
 * {@code DirectoryLinkResource} (which knows how to answer an HTTP request), because it is the part
 * a future caller is most likely to get wrong.
 *
 * <h2>Nothing is written</h2>
 *
 * <p>The resolved name is set on {@link DirectoryLink}'s <b>transient</b> fields and the document is
 * never saved. That is item 27(a)'s rule — hc-patient owns identity, a stored copy is a second
 * thing that can disagree with them — and it is why this class takes documents that have just been
 * read and hands them straight back.
 *
 * <h2>The cache is per request, and its lifetime is the decision rather than an implementation
 * detail</h2>
 *
 * <p>{@link #resolve(List)} dedupes by address <b>within one call</b> and keeps nothing afterwards.
 * A longer-lived cache is not an optimisation of this design, it is the design item 27(a) forbids:
 * a name held between requests is a stored copy of a name, and it goes stale exactly when it
 * matters — the request after somebody corrects their record on hc-patient. Deduping within a page
 * costs nothing and cannot go stale, because everything in it was read in the same second.
 *
 * <p>In practice the map rarely saves a call: {@code (source, external_key)} is uniquely indexed, so
 * two links on one page almost never carry the same address. It is here so that a caller who does
 * ask twice pays once, and so that the <em>lifetime</em> is written down where somebody would
 * otherwise reach for a {@code @Cacheable}.
 *
 * <h2>The budget, and the sixty-second lesson behind it</h2>
 *
 * <p>This runs on the request thread, one blocking call per nameless row, so a page of twenty rows
 * against a sibling that accepts connections and never answers is twenty read timeouts in series.
 * Backlog item 39a is the same shape one component along — a lazily-created Kafka binding blocked a
 * request thread for sixty seconds while returning 201 — and the answer there was to take the wait
 * off the caller's thread entirely. That is not available here, because the caller is waiting for
 * the answer. So the wait is <b>bounded</b> instead: once {@code resolve-budget-ms} has been spent,
 * the remaining links are marked {@link NameResolution#UNAVAILABLE} <em>without being dialled</em>,
 * which is true — nobody asked them — and the page still renders with the addresses item 45 put on
 * it.
 */
@Service
public class DirectoryNameResolutionService {

    private static final Logger LOG = LoggerFactory.getLogger(DirectoryNameResolutionService.class);

    private final PatientServiceClient patientServiceClient;
    private final long budgetMs;

    public DirectoryNameResolutionService(
        PatientServiceClient patientServiceClient,
        @Value("${application.patientservice.resolve-budget-ms:4000}") long budgetMs
    ) {
        this.patientServiceClient = patientServiceClient;
        this.budgetMs = budgetMs;
    }

    /**
     * Decorates every link on this page that a name can be asked for, and leaves the rest untouched.
     *
     * <p><b>Untouched, not marked.</b> A link nobody could have asked about — a clinician, or a
     * patient link carrying no address — gets no outcome at all, so an absent {@code nameResolution}
     * on the wire means "this row was never a candidate" and is not confusable with a lookup that
     * failed. The console branches on the presence of the field for exactly that reason.
     *
     * @param links the page as it was read, mutated in place.
     */
    public void resolve(List<DirectoryLink> links) {
        if (links == null || links.isEmpty()) {
            return;
        }
        if (!patientServiceClient.isEnabled()) {
            // Marked, not skipped: these rows ARE candidates, and a candidate with no outcome would
            // tell the console it was never one. What is skipped is the client, so a deployment with
            // no sibling stack spends nothing per row and the budget below is never entered.
            //
            // This branch is why isEnabled() exists. Without it the same answer arrived one layer
            // down — the client refuses when disabled — but every row counted as a lookup that had
            // been made, so the budget warning could report dozens of "lookups" on a deployment that
            // opened no socket at all. A figure like that sends a reader to the network.
            for (DirectoryLink link : links) {
                if (addressToAskAbout(link) != null) {
                    apply(link, ResolvedName.unavailable());
                }
            }
            return;
        }
        Map<String, ResolvedName> resolvedThisRequest = new HashMap<>();
        long deadline = System.nanoTime() + budgetMs * 1_000_000L;
        int asked = 0;
        int budgetSkipped = 0;

        for (DirectoryLink link : links) {
            String email = addressToAskAbout(link);
            if (email == null) {
                continue;
            }
            String key = email.toLowerCase(Locale.ROOT);
            ResolvedName resolved = resolvedThisRequest.get(key);
            if (resolved == null) {
                // `>=`, so a budget of zero deterministically asks nobody — which is what
                // DirectoryNameResolutionServiceTest configures, and `>` would make that case a race
                // against the clock's resolution.
                if (System.nanoTime() >= deadline) {
                    // Not dialled, so not asked and not cached: a later page with more budget must be
                    // free to try this address again.
                    budgetSkipped++;
                    apply(link, ResolvedName.unavailable());
                    continue;
                }
                resolved = patientServiceClient.resolveName(email);
                resolvedThisRequest.put(key, resolved);
                asked++;
            }
            apply(link, resolved);
        }

        if (budgetSkipped > 0) {
            // The one line worth an operator's attention, and it names no address. A page that
            // renders addresses instead of names because the budget ran out looks exactly like a
            // page that renders them because hc-patient has never heard of anybody.
            LOG.warn(
                "Name resolution budget of {}ms was spent after {} lookups; {} link(s) were reported unavailable without being asked",
                budgetMs,
                asked,
                budgetSkipped
            );
        }
    }

    /**
     * The address this link can be looked up by, or {@code null} when there is nothing to ask.
     *
     * <p>Two conditions, and both are narrower than they look:
     *
     * <ul>
     *   <li><b>{@code HC_PATIENT} only.</b> hc-patient's endpoint is keyed on an email address.
     *       An {@code HC_PROFESSIONAL} link's correlation key is an {@code accountId} — a UUID —
     *       and its {@code email} belongs to a person on a third stack that this endpoint knows
     *       nothing about, so asking would be a guaranteed 404 per clinician per page.</li>
     *   <li><b>{@code email}, never {@code externalKey}.</b> They hold the same value for a patient
     *       today, and relying on that is how a loosened source filter would come to send a
     *       clinician's UUID to an address lookup. The field that means "an address" is the one to
     *       read.</li>
     * </ul>
     */
    private static String addressToAskAbout(DirectoryLink link) {
        if (link.getSource() != DirectorySource.HC_PATIENT) {
            return null;
        }
        String email = link.getEmail();
        return email == null || email.isBlank() ? null : email.trim();
    }

    private static void apply(DirectoryLink link, ResolvedName resolved) {
        link.setNameResolution(resolved.outcome());
        link.setResolvedName(resolved.hasName() ? resolved.name() : null);
    }
}
