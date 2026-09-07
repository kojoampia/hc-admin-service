package net.jojoaddison.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Opaque, stable handles for the two things this service must be able to talk about in a log and
 * must not copy into one: a sibling stack's <b>correlation key</b>, and an <b>unreadable frame's
 * bytes</b>.
 *
 * <h2>The decision, and why it was needed</h2>
 *
 * <p>{@code SiblingDomainEvent.subjectKey} is <b>a lowercased email address</b> for every patient —
 * the DTO's own javadoc says so, and it is an email precisely because it has to identify one person
 * across two systems. Until 2026-09-07 {@code DirectoryProjectionService} logged it verbatim at
 * {@code INFO} on the creation path, and {@code application-prod.yml} sets {@code net.jojoaddison:
 * INFO}, so production wrote a patient's address into its logs for every patient it learned about.
 *
 * <p><b>Those logs are not local, and that was established rather than assumed.</b> On the
 * production host the line reached Loki by two independent paths — the OpenTelemetry Java agent
 * (v2.30.0, whose {@code otel.logs.exporter} defaults to {@code otlp}) and Alloy's container-log
 * scrape — into a store shared by six products, unauthenticated, {@code multitenancy_enabled:
 * false}, with fourteen days of retention. So the answer to "may the correlation key appear in a
 * log" is <b>no</b>: it is not one host's rotated file, it is a queryable estate-wide index of who
 * has registered.
 *
 * <h2>Why a hash and not simply the record id</h2>
 *
 * <p>The obvious fix is to log only the {@code Patient} id the call created, which the creation line
 * already carried. It is not enough, and dropping the subject entirely would have made this code
 * <em>less</em> diagnosable than the defect it fixed. The question an operator actually arrives
 * with is the reverse of the one an id answers: somebody reports that they registered and cannot be
 * seen, and the only handle that person has is their email address. With an id alone there is no
 * query — you cannot search a log for a record you cannot name.
 *
 * <p>So the subject is logged as a <b>deterministic digest of the key</b>. It is not reversible by
 * reading it, and it stays searchable by anyone holding the address, who reproduces it in one line:
 *
 * <pre>{@code
 * printf '%s' "$(echo 'Ama.Mensah@Example.COM' | tr 'A-Z' 'a-z')" | sha256sum | cut -c1-12
 * }</pre>
 *
 * <p>The {@code tr} is load-bearing: the parser normalises a key by trimming and lower-casing it
 * before it ever reaches this class, so a digest taken over the address as somebody typed it will
 * not match. {@link #subject(String)} normalises again rather than trusting its caller, so the two
 * ends cannot drift.
 *
 * <h2>What this is not</h2>
 *
 * <p><b>Pseudonymisation, not anonymisation, and the difference is the point rather than a
 * caveat.</b> The digest is unsalted, so somebody who already suspects an address can confirm it is
 * present. That is not an oversight — it is the same property as the paragraph above, seen from the
 * other side, and it is what a salt would take away: a salted digest is unreproducible without
 * distributing the salt to whoever is diagnosing, differs between production and quality, and turns
 * a one-line shell check into a secret-handling exercise. The harm this closes is the one that
 * actually existed: the log is no longer a <em>list</em> of the addresses of everyone who
 * registered, and no longer discloses an address to a reader who did not already have it.
 *
 * <p>Twelve hex characters is 48 bits. At this service's scale a collision is not a practical
 * concern, and a collision's cost is two subjects reading alike in a log line, not a wrong write —
 * nothing here is keyed on the digest, which is only ever rendered.
 */
public final class LogPseudonym {

    /** Long enough that two subjects will not collide in practice, short enough to read in a line. */
    private static final int DIGEST_CHARS = 12;

    private LogPseudonym() {}

    /**
     * A stable handle for a correlation key — an email for a patient, an {@code accountId} for a
     * clinician. Safe to log; see the class javadoc for how to reproduce one from an address.
     *
     * @param subjectKey the key, in any casing. Normalised before hashing, so a caller that has not
     *                   been through the parser still produces the same handle.
     * @return {@code subj-<12 hex>}, or {@code subj-none} when there is no key to name.
     */
    public static String subject(String subjectKey) {
        if (subjectKey == null || subjectKey.isBlank()) {
            return "subj-none";
        }
        return "subj-" + digest(subjectKey.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * A stable handle for a frame this service could not read.
     *
     * <p>It replaces an excerpt of the payload, and the reasoning is in
     * {@code SiblingEventParser#read}. What it buys over an excerpt is the question an operator
     * actually has — <em>is this one frame being redelivered, or many different ones?</em> — which
     * two frames differing after the hundredth character answer identically under truncation and
     * distinctly here.
     *
     * @param payload the raw bytes as they arrived.
     * @return {@code frame-<12 hex> (<n> bytes)}.
     */
    public static String frame(byte[] payload) {
        int length = payload == null ? 0 : payload.length;
        return "frame-" + digest(payload == null ? new byte[0] : payload) + " (" + length + " bytes)";
    }

    private static String digest(byte[] input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input);
            return HexFormat.of().formatHex(hash).substring(0, DIGEST_CHARS);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required of every JRE. Unreachable, and rethrowing keeps the signature
            // free of a checked exception no caller could act on.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
