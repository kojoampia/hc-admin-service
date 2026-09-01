package net.jojoaddison.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.GeographicSpace;
import net.jojoaddison.repository.GeographicSpaceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The contract of the geographic-space reference read.
 *
 * <p>Filters off, like every other {@code *ResourceIT} here — the authority rule this endpoint turns
 * on is asserted in {@code ApiAuthorizationIT}, which is the only class in the suite that runs with
 * the chain on.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class GeographicSpaceReferenceResourceIT {

    private static final String API_URL = "/api/geographic-spaces";
    private static final String API_URL_ID = API_URL + "/{id}";

    @Autowired
    private MockMvc restGeographicSpaceMockMvc;

    @Autowired
    private GeographicSpaceRepository geographicSpaceRepository;

    @BeforeEach
    void seedATree() {
        geographicSpaceRepository.deleteAll();
        geographicSpaceRepository.save(new GeographicSpace().id("gs-it-ghana").name("Ghana").type("COUNTRY"));
        geographicSpaceRepository.save(new GeographicSpace().id("gs-it-accra").name("Accra").type("CITY").parentId("gs-it-ghana"));
    }

    @AfterEach
    void clearTree() {
        geographicSpaceRepository.deleteAll();
    }

    @Test
    void getAllGeographicSpacesIsPaginated() throws Exception {
        restGeographicSpaceMockMvc
            .perform(get(API_URL + "?sort=id,desc&page=0&size=1"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "2"))
            .andExpect(header().exists("Link"))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getGeographicSpaceReturnsItsPlaceInTheTree() throws Exception {
        restGeographicSpaceMockMvc
            .perform(get(API_URL_ID, "gs-it-accra"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("gs-it-accra"))
            .andExpect(jsonPath("$.name").value("Accra"))
            .andExpect(jsonPath("$.type").value("CITY"))
            .andExpect(jsonPath("$.parentId").value("gs-it-ghana"));
    }

    @Test
    void anUnknownSpaceIsNotFound() throws Exception {
        restGeographicSpaceMockMvc.perform(get(API_URL_ID, "gs-it-nowhere")).andExpect(status().isNotFound());
    }

    /**
     * <b>The projection is the authorisation argument, so it is asserted rather than assumed.</b>
     *
     * <p>These four values are readable by every authenticated caller on three stacks. The reason
     * that is acceptable is that they describe a place and not a person — an argument that holds
     * only for as long as the response really is these four values. The day somebody adds a field to
     * {@code GeographicSpace}, serialising the entity would have carried it here silently; the
     * strict comparison below is what turns that into a failing build instead. It is strict in both
     * directions on purpose: a missing field fails it too, and a root's null parent is a value the
     * client has to be able to read rather than infer from an absence.
     */
    @Test
    void theResponseCarriesTheFourReferenceFieldsAndNothingElse() throws Exception {
        restGeographicSpaceMockMvc
            .perform(get(API_URL_ID, "gs-it-accra"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"id\":\"gs-it-accra\",\"name\":\"Accra\",\"type\":\"CITY\",\"parentId\":\"gs-it-ghana\"}", true));

        restGeographicSpaceMockMvc
            .perform(get(API_URL_ID, "gs-it-ghana"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"id\":\"gs-it-ghana\",\"name\":\"Ghana\",\"type\":\"COUNTRY\",\"parentId\":null}", true));
    }
}
