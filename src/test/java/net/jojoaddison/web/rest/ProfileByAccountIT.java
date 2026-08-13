package net.jojoaddison.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.enumeration.IdType;
import net.jojoaddison.domain.enumeration.Sex;
import net.jojoaddison.repository.ProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Looking up a person by the account they sign in with.
 *
 * <p>The gateway's account carries login, name, email and language and nothing else; everything
 * else about a person lives here as a Profile, joined by {@code accountId}. Without this the console
 * can show an administrator their credentials but never their own details.
 *
 * <p>Two behaviours are worth pinning, and neither is about the happy path.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class ProfileByAccountIT {

    private static final String ENDPOINT = "/api/profiles/by-account/";
    private static final String ACCOUNT_ID = "6a6bf0c12c8c301d3aa8cb2e";

    @Autowired
    private MockMvc restMockMvc;

    @Autowired
    private ProfileRepository profileRepository;

    @AfterEach
    void cleanUp() {
        profileRepository.deleteAll();
    }

    /**
     * A Profile has seven required fields. Worth noticing rather than working around: it means the
     * console cannot quietly create a stub profile for an account — whoever creates one has to
     * supply a date of birth, a sex, an ID type and number, a mobile number and an email.
     */
    private static Profile profileFor(String accountId, String firstName, String lastName) {
        return new Profile()
            .accountId(accountId)
            .firstName(firstName)
            .lastName(lastName)
            .dateOfBirth(LocalDate.of(1985, 4, 12))
            .sex(Sex.FEMALE)
            .mobilePhone("+233200000000")
            .email(firstName.toLowerCase() + "@abofonsa.care")
            .idType(IdType.GHANA_CARD)
            .idNumber("GHA-000000000-0");
    }

    @Test
    void findsTheProfileLinkedToAnAccount() throws Exception {
        profileRepository.save(profileFor(ACCOUNT_ID, "Ama", "Mensah"));
        profileRepository.save(profileFor("someone-else", "Kofi", "Boateng"));

        restMockMvc
            .perform(get(ENDPOINT + ACCOUNT_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.firstName").value("Ama"))
            .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID));
    }

    /**
     * A 404 here is a normal answer, not a failure.
     *
     * <p>Most accounts have no profile — production has none at all — and the console reads this as
     * "offer to create one". If it ever became a 500 or an empty 200, the screen would either look
     * broken or silently offer to edit a record that does not exist.
     */
    @Test
    void answersNotFoundWhenTheAccountHasNoProfile() throws Exception {
        restMockMvc.perform(get(ENDPOINT + "an-account-with-no-profile")).andExpect(status().isNotFound());
    }

    /**
     * {@code by-account} must not be swallowed by the {@code /{id}} mapping above it.
     *
     * <p>Both routes match a single path segment. Ordering is what keeps them apart, and ordering is
     * the kind of thing a later edit reshuffles without noticing — at which point this endpoint
     * starts returning 404 for profiles that exist, because it is looking them up by the literal id
     * "by-account".
     */
    @Test
    void isNotShadowedByTheIdRoute() throws Exception {
        Profile stored = profileRepository.save(profileFor(ACCOUNT_ID, "Ama", "Mensah"));

        // The id route still works for a real id...
        restMockMvc.perform(get("/api/profiles/" + stored.getId())).andExpect(status().isOk());
        // ...and the account route is not being read as an id.
        restMockMvc.perform(get(ENDPOINT + ACCOUNT_ID)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(stored.getId()));
    }
}
