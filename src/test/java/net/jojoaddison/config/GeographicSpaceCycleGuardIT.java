package net.jojoaddison.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.GeographicSpace;
import net.jojoaddison.repository.GeographicSpaceRepository;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * The cycle guard is actually under a real save.
 *
 * <p>{@link GeographicSpaceCycleGuardTest} states the rule against a stub repository and is where
 * the cases live. This class asserts the one thing that test cannot: that
 * {@link GeographicSpaceCycleGuard} is registered as a {@code BeforeConvertCallback} and runs when
 * something writes through the repository — which is the whole reason the guard is a callback rather
 * than a method on a service nobody calls. Deleting the {@code @Component} annotation would leave
 * every case in the unit test green.
 *
 * <p>It saves through the repository rather than through an endpoint on purpose: there is no write
 * endpoint for this collection, and the writer that exists today is
 * {@link DevelopmentDataInitializer} calling {@code saveAll}. This is that path.
 */
@IntegrationTest
@WithMockUser
class GeographicSpaceCycleGuardIT {

    @Autowired
    private GeographicSpaceRepository geographicSpaceRepository;

    @AfterEach
    void clearSpaces() {
        geographicSpaceRepository.deleteAll();
    }

    @Test
    void anOrdinaryTreeSaves() {
        geographicSpaceRepository.save(new GeographicSpace().id("it-ghana").name("Ghana").type("COUNTRY"));
        geographicSpaceRepository.save(new GeographicSpace().id("it-accra").name("Accra").type("CITY").parentId("it-ghana"));

        assertThat(geographicSpaceRepository.findById("it-accra")).get().extracting(GeographicSpace::getParentId).isEqualTo("it-ghana");
    }

    @Test
    void closingTheLoopIsRefusedAtTheRepository() {
        geographicSpaceRepository.save(new GeographicSpace().id("it-ghana").name("Ghana").type("COUNTRY"));
        geographicSpaceRepository.save(new GeographicSpace().id("it-accra").name("Accra").type("CITY").parentId("it-ghana"));

        GeographicSpace ghana = geographicSpaceRepository.findById("it-ghana").orElseThrow();
        ghana.setParentId("it-accra");

        assertThatThrownBy(() -> geographicSpaceRepository.save(ghana)).isInstanceOf(BadRequestAlertException.class);

        // And the rejection is a rejection: the stored document still has no parent.
        assertThat(geographicSpaceRepository.findById("it-ghana")).get().extracting(GeographicSpace::getParentId).isNull();
    }
}
