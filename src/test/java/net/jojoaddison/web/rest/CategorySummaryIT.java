package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Category;
import net.jojoaddison.domain.ServiceActivity;
import net.jojoaddison.repository.CategoryRepository;
import net.jojoaddison.repository.ServiceActivityRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /api/categories/summary} and the catalogue screen's activity filter.
 *
 * <p>Both exist for the same screen and fail the same way if the DBRef path is wrong — every count
 * reads zero and every category looks empty, which is indistinguishable from a catalogue nobody has
 * filled in. That is why the counts here are asserted against known non-zero numbers rather than
 * against "some number".
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class CategorySummaryIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ServiceActivityRepository serviceActivityRepository;

    @Autowired
    private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    private Category clinical;
    private Category equipment;
    private Category empty;

    @BeforeEach
    void seed() {
        serviceActivityRepository.deleteAll();
        categoryRepository.deleteAll();

        clinical = categoryRepository.save(new Category().name("Clinical visits").iconKey("stetho"));
        equipment = categoryRepository.save(new Category().name("Equipment").iconKey("card"));
        empty = categoryRepository.save(new Category().name("Transport").iconKey("pin"));

        serviceActivityRepository.saveAll(
            List.of(
                activity("Routine nursing visit", clinical, true),
                activity("Doctor home visit", clinical, true),
                activity("Wound dressing", clinical, true),
                // Withdrawn: counted as an activity, not as a live one. The seed has this case too.
                activity("Hospital bed hire", equipment, false),
                activity("Oxygen concentrator hire", equipment, true)
            )
        );
        activityWithNoPublishedField("Unreviewed draft", equipment);
    }

    /**
     * Leaves an activity document carrying no {@code published} field at all.
     *
     * <p>Saved normally and then stripped, rather than inserted raw. Two reasons. {@code published}
     * is {@code @NotNull}, so the validating save listener rejects a null on the way in — the
     * constraint governs what this application writes <em>today</em> and does not retro-fit the field
     * onto documents written before it existed, which MongoDB has no schema to do either. And a
     * hand-built document has to guess how Spring Data encodes the category DBRef; it stores
     * {@code $id} as an ObjectId when the id looks like one, so a hand-written String {@code $id}
     * silently matches no query and the fixture quietly is not there.
     *
     * <p>The {@code $unset} goes through the mapper for the same reason, so the id is converted the
     * way it was stored. This is the document {@code CategorySummaryService#live} chooses
     * {@code is(true)} over {@code ne(false)} for, and no fixture built purely through the repository
     * could contain one — so without this the decision would go untested.
     */
    private void activityWithNoPublishedField(String name, Category category) {
        ServiceActivity saved = serviceActivityRepository.save(activity(name, category, true));
        mongoTemplate.updateFirst(
            org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("id").is(saved.getId())
            ),
            new org.springframework.data.mongodb.core.query.Update().unset("published"),
            ServiceActivity.class
        );
    }

    @AfterEach
    void tearDown() {
        serviceActivityRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    /**
     * Three live of three for clinical; one live of three for equipment; nothing at all for
     * transport.
     *
     * <p>The equipment row is the useful one: a card reading "Equipment · 3" when only one activity
     * can actually be booked overstates the catalogue, which is why {@code live} is carried beside
     * the total rather than left to the client to work out.
     */
    @Test
    void countsAreCountedOnTheActivitySide() throws Exception {
        mvc
            .perform(get("/api/categories/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categories[?(@.categoryId=='" + clinical.getId() + "')].activities").value(3))
            .andExpect(jsonPath("$.categories[?(@.categoryId=='" + clinical.getId() + "')].live").value(3))
            .andExpect(jsonPath("$.categories[?(@.categoryId=='" + equipment.getId() + "')].activities").value(3))
            .andExpect(jsonPath("$.categories[?(@.categoryId=='" + equipment.getId() + "')].live").value(1))
            // A category holding nothing reports zero. It exists and is empty, which the card says.
            .andExpect(jsonPath("$.categories[?(@.categoryId=='" + empty.getId() + "')].activities").value(0))
            .andExpect(jsonPath("$.categories[?(@.categoryId=='" + empty.getId() + "')].live").value(0));
    }

    /**
     * {@code categoryId.equals} filters server-side, and the count in {@code X-Total-Count} follows
     * the filter.
     *
     * <p>The header is the half that regresses quietly. An unknown request parameter is silently
     * ignored by Spring, which is exactly how {@code status.equals} came to be accepted and dropped
     * on every list endpoint — the response looked healthy and the filter did nothing. A body-only
     * assertion would pass against that, because an unfiltered first page still contains the rows
     * being looked for.
     */
    @Test
    void activitiesFilterByCategory() throws Exception {
        mvc
            .perform(get("/api/service-activities?categoryId.equals=" + clinical.getId()))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "3"))
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[*].name", not(hasItem("Hospital bed hire"))));

        mvc
            .perform(get("/api/service-activities?categoryId.equals=" + empty.getId()))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "0"))
            .andExpect(jsonPath("$", hasSize(0)));
    }

    /**
     * The unfiltered list still answers with everything, so adding the filter did not narrow the
     * endpoint for callers that do not send it.
     */
    @Test
    void withoutTheFilterEveryActivityIsListed() throws Exception {
        mvc.perform(get("/api/service-activities")).andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "6"));
    }

    /**
     * The LIVE toggle round-trips through the existing {@code PATCH}, in both directions.
     *
     * <p>No new endpoint was added for it: {@code partialUpdateServiceActivity} already copies
     * {@code published}. The direction worth testing is <strong>on to off</strong>, because the
     * handler skips fields that arrive null and {@code false} is one keystroke away from being
     * treated as absent. If it ever were, the toggle would light up in the browser, save without
     * error, and revert on reload — a defect that looks like a caching problem and is not.
     *
     * <p>Asserted against the stored document rather than the response body, because the response is
     * built from the same in-memory object either way and would agree with itself.
     */
    @Test
    void theLiveToggleRoundTripsThroughPatch() throws Exception {
        ServiceActivity live = serviceActivityRepository
            .findAll()
            .stream()
            .filter(a -> Boolean.TRUE.equals(a.getPublished()))
            .findFirst()
            .orElseThrow();

        mvc
            .perform(
                patch("/api/service-activities/" + live.getId())
                    .contentType("application/merge-patch+json")
                    .content("{\"id\":\"" + live.getId() + "\",\"published\":false}")
            )
            .andExpect(status().isOk());
        assertThat(serviceActivityRepository.findById(live.getId()).orElseThrow().getPublished()).isFalse();

        mvc
            .perform(
                patch("/api/service-activities/" + live.getId())
                    .contentType("application/merge-patch+json")
                    .content("{\"id\":\"" + live.getId() + "\",\"published\":true}")
            )
            .andExpect(status().isOk());
        assertThat(serviceActivityRepository.findById(live.getId()).orElseThrow().getPublished()).isTrue();
    }

    /**
     * Withdrawing an activity drops it out of {@code live} without dropping it out of
     * {@code activities} — the card's two numbers move independently, which is the whole point of
     * carrying both.
     */
    @Test
    void withdrawingAnActivityMovesOnlyTheLiveCount() throws Exception {
        ServiceActivity target = serviceActivityRepository
            .findAll()
            .stream()
            .filter(a -> "Wound dressing".equals(a.getName()))
            .findFirst()
            .orElseThrow();

        mvc
            .perform(
                patch("/api/service-activities/" + target.getId())
                    .contentType("application/merge-patch+json")
                    .content("{\"id\":\"" + target.getId() + "\",\"published\":false}")
            )
            .andExpect(status().isOk());

        mvc
            .perform(get("/api/categories/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categories[?(@.categoryId=='" + clinical.getId() + "')].activities").value(3))
            .andExpect(jsonPath("$.categories[?(@.categoryId=='" + clinical.getId() + "')].live").value(2));
    }

    /**
     * The path is a literal segment, not a category whose id is "summary".
     */
    @Test
    void summaryIsNotReadAsACategoryId() throws Exception {
        mvc.perform(get("/api/categories/summary")).andExpect(status().isOk()).andExpect(jsonPath("$.categories").isArray());
    }

    private static ServiceActivity activity(String name, Category category, Boolean published) {
        return new ServiceActivity()
            .name(name)
            .unit("per visit")
            .unitPrice(new BigDecimal("180"))
            .duration("60 min")
            .published(published)
            .category(category);
    }
}
