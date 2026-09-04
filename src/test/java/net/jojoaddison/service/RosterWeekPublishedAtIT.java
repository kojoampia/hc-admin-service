package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.RosterWeek;
import net.jojoaddison.repository.RosterWeekRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code RosterWeek.publishedAt} is the server's, not the client's — decision 8 of
 * {@code duty-roster-resolution.md} § 9.1.
 *
 * <p>It joins {@code Message.readAt}, {@code Task.closedAt} and {@code PlatformService.lastProbedAt}
 * and inherits their trap, arriving by a different route. Those three are absent from their DTOs, so
 * nothing on the wire can carry them; {@code RosterWeek} is serialised as the domain entity with no
 * DTO at all, so the field <em>is</em> on the wire and {@code RosterWeekResource} discards it. Both
 * shapes leave the callback holding a null on every update, which is the interesting half:
 *
 * <ul>
 *   <li><b>An edit must not move the stamp.</b> A callback stamping "now" on finding it empty walks
 *       the publication date forward on every save, so a week published in May reports as published
 *       on whichever afternoon somebody last corrected its label — and nothing on screen looks
 *       wrong. {@link #anEditDoesNotWalkTheStampForward} is the case.
 *   <li><b>Withdrawing must clear it.</b> A timestamp beside {@code published: false} says the week
 *       is out when it is not.
 *   <li><b>A client cannot set it.</b> Not on {@code PUT}, which sends a whole document, and not on
 *       {@code PATCH}, which used to copy it.
 * </ul>
 *
 * <p>{@code RosterWeekResourceIT} deliberately does <em>not</em> assert this field round-trips —
 * that assertion is the vulnerability, exactly as it was for {@code createdBy}/{@code createdDate},
 * which the entity ITs also leave to a dedicated class.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class RosterWeekPublishedAtIT {

    private static final String ENTITY_API_URL_ID = "/api/roster-weeks/{id}";

    /** Well before any test run, so "the server stamped it now" is never mistaken for this. */
    private static final Instant CLIENT_CLAIM = Instant.parse("2020-01-01T00:00:00Z");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RosterWeekRepository rosterWeekRepository;

    @BeforeEach
    void clear() {
        rosterWeekRepository.deleteAll();
    }

    private RosterWeek storedWeek(boolean published) {
        return rosterWeekRepository.save(
            new RosterWeek().label("Week of 10 August 2026").startDate(LocalDate.of(2026, 8, 10)).published(published)
        );
    }

    private RosterWeek reread(RosterWeek week) {
        return rosterWeekRepository.findById(week.getId()).orElseThrow();
    }

    @Test
    void publishingStampsTheWeek() throws Exception {
        RosterWeek week = storedWeek(false);
        assertThat(week.getPublishedAt()).isNull();

        mvc
            .perform(
                patch(ENTITY_API_URL_ID, week.getId())
                    .contentType("application/merge-patch+json")
                    .content("{\"id\":\"%s\",\"published\":true}".formatted(week.getId()))
            )
            .andExpect(status().isOk());

        assertThat(reread(week).getPublishedAt()).isNotNull();
    }

    /**
     * A {@code PATCH} edit keeps the publication date.
     *
     * <p><b>This case does not prove the re-read, and saying so is the point of the pair.</b> The
     * {@code PATCH} handler loads the stored document and mutates it, so {@code publishedAt} is
     * already populated when the callback runs — deleting the re-read leaves this green. It is
     * {@link #aPutEditDoesNotWalkTheStampForward} that fails, and that was established by running
     * the mutation rather than by reading the code. Both are kept: this one covers the console's
     * actual call, and that one covers the rule.
     */
    @Test
    void aPatchEditDoesNotWalkTheStampForward() throws Exception {
        RosterWeek week = storedWeek(true);
        Instant publishedAt = reread(week).getPublishedAt();
        assertThat(publishedAt).isNotNull();

        mvc
            .perform(
                patch(ENTITY_API_URL_ID, week.getId())
                    .contentType("application/merge-patch+json")
                    .content("{\"id\":\"%s\",\"label\":\"Renamed once\"}".formatted(week.getId()))
            )
            .andExpect(status().isOk());
        mvc
            .perform(
                patch(ENTITY_API_URL_ID, week.getId())
                    .contentType("application/merge-patch+json")
                    .content("{\"id\":\"%s\",\"label\":\"Renamed twice\"}".formatted(week.getId()))
            )
            .andExpect(status().isOk());

        RosterWeek after = reread(week);
        assertThat(after.getLabel()).isEqualTo("Renamed twice");
        assertThat(after.getPublishedAt()).isEqualTo(publishedAt);
    }

    /** Withdrawing a week clears the stamp; republishing takes a new one. */
    @Test
    void withdrawingClearsTheStampAndRepublishingTakesANewOne() throws Exception {
        RosterWeek week = storedWeek(true);
        Instant first = reread(week).getPublishedAt();

        mvc
            .perform(
                patch(ENTITY_API_URL_ID, week.getId())
                    .contentType("application/merge-patch+json")
                    .content("{\"id\":\"%s\",\"published\":false}".formatted(week.getId()))
            )
            .andExpect(status().isOk());
        assertThat(reread(week).getPublishedAt()).isNull();

        mvc
            .perform(
                patch(ENTITY_API_URL_ID, week.getId())
                    .contentType("application/merge-patch+json")
                    .content("{\"id\":\"%s\",\"published\":true}".formatted(week.getId()))
            )
            .andExpect(status().isOk());
        assertThat(reread(week).getPublishedAt()).isNotNull().isNotEqualTo(first);
    }

    /**
     * A {@code PATCH} carrying a timestamp is ignored — the console sent exactly this until
     * 2026-09-04, {@code { published: true, publishedAt: now }}, and was believed.
     */
    @Test
    void aPatchCannotSetTheStamp() throws Exception {
        RosterWeek week = storedWeek(false);

        mvc
            .perform(
                patch(ENTITY_API_URL_ID, week.getId())
                    .contentType("application/merge-patch+json")
                    .content("{\"id\":\"%s\",\"published\":true,\"publishedAt\":\"%s\"}".formatted(week.getId(), CLIENT_CLAIM))
            )
            .andExpect(status().isOk());

        assertThat(reread(week).getPublishedAt()).isNotNull().isNotEqualTo(CLIENT_CLAIM);
    }

    /**
     * <b>The restamp, and the case that actually proves the re-read.</b>
     *
     * <p>Two things at once, because on this path they are the same mechanism. {@code PUT} builds a
     * fresh entity from the request body, so the callback sees {@code publishedAt} null whatever the
     * client sent — a callback that stamped "now" on finding it empty would both ignore the client's
     * claim (correct) and move the publication date to the moment of the edit (silently wrong).
     * Verified by mutation: replacing {@code storedPublishedAt(…)} with {@code Instant.now(clock)}
     * fails this case and no other in the class.
     */
    @Test
    void aPutEditDoesNotWalkTheStampForward() throws Exception {
        RosterWeek week = storedWeek(true);
        Instant publishedAt = reread(week).getPublishedAt();

        mvc
            .perform(
                put(ENTITY_API_URL_ID, week.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        ("{\"id\":\"%s\",\"label\":\"Week of 10 August 2026\",\"startDate\":\"2026-08-10\"," +
                            "\"published\":true,\"publishedAt\":\"%s\"}").formatted(week.getId(), CLIENT_CLAIM)
                    )
            )
            .andExpect(status().isOk());

        assertThat(reread(week).getPublishedAt()).isEqualTo(publishedAt);
    }
}
