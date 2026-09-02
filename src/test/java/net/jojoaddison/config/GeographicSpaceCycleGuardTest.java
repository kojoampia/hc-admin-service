package net.jojoaddison.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.jojoaddison.domain.GeographicSpace;
import net.jojoaddison.repository.GeographicSpaceRepository;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * A geographic space may not contain itself.
 *
 * <p>The rule is enforced on write because the cost of breaking it is paid on read: proximity is a
 * walk up {@code parentId}, and a cycle turns that into a request that never returns, in a reader
 * that had nothing to do with the save. So these cases are about the <em>rejection</em> — that the
 * write is refused, at the moment it is made — rather than about a walk defending itself later.
 *
 * <p>Unit rather than integration, deliberately: the guard's whole behaviour is a walk over stored
 * parent ids, and a stub repository states the shape of each tree in three lines where a Testcontainers
 * fixture would bury it in saves. The one thing a unit test cannot show — that the callback is
 * actually invoked by a real {@code save} — is covered by {@link GeographicSpaceCycleGuardIT}.
 */
@ExtendWith(MockitoExtension.class)
class GeographicSpaceCycleGuardTest {

    private static final String COLLECTION = "geographic_spaces";

    @Mock
    private GeographicSpaceRepository geographicSpaceRepository;

    private final Map<String, GeographicSpace> stored = new HashMap<>();

    private GeographicSpaceCycleGuard guard;

    @BeforeEach
    void setUp() {
        stored.clear();
        lenient()
            .when(geographicSpaceRepository.findById(org.mockito.ArgumentMatchers.anyString()))
            .thenAnswer(invocation -> Optional.ofNullable(stored.get(invocation.<String>getArgument(0))));
        guard = new GeographicSpaceCycleGuard(geographicSpaceRepository);
    }

    private GeographicSpace store(String id, String parentId) {
        GeographicSpace space = new GeographicSpace().id(id).name(id).type("DISTRICT").parentId(parentId);
        stored.put(id, space);
        return space;
    }

    @Test
    void aRootSpaceIsAccepted() {
        GeographicSpace ghana = new GeographicSpace().id("gs-ghana").name("Ghana").type("COUNTRY");

        assertThat(guard.onBeforeConvert(ghana, COLLECTION)).isSameAs(ghana);
    }

    @Test
    void aSpaceUnderAnExistingAncestryIsAccepted() {
        store("gs-ghana", null);
        store("gs-greater-accra", "gs-ghana");
        store("gs-accra", "gs-greater-accra");
        GeographicSpace osu = new GeographicSpace().id("gs-osu").name("Osu").type("DISTRICT").parentId("gs-accra");

        assertThatCode(() -> guard.onBeforeConvert(osu, COLLECTION)).doesNotThrowAnyException();
    }

    @Test
    void aSpaceCannotBeItsOwnParent() {
        GeographicSpace accra = store("gs-accra", "gs-accra");

        assertThatThrownBy(() -> guard.onBeforeConvert(accra, COLLECTION))
            .isInstanceOf(BadRequestAlertException.class)
            .hasMessageContaining("cannot be its own parent")
            .extracting(error -> ((BadRequestAlertException) error).getErrorKey())
            .isEqualTo("cyclicparent");
    }

    /**
     * The case the one-step check misses, and the one a hand-built tree actually produces: a
     * reparenting deep enough that nobody notices the loop closing.
     *
     * <p>{@code cyclicancestor}, matching the message. It was {@code cyclicparent} under this same
     * "own ancestor" message until 2026-09-02 — defensible, since both this and
     * {@link #aSpaceCannotBeItsOwnParent} are faults in the parent id the caller sent, but a key and
     * a message that name different relationships read as a bug to whoever is triaging the error
     * rather than writing it. The distinction the keys carry is now what a caller can act on: two
     * keys for "the parent you sent", one for "what was already stored".
     */
    @Test
    void aSpaceCannotBeItsOwnGrandparent() {
        store("gs-accra", "gs-greater-accra");
        store("gs-greater-accra", "gs-ghana");
        GeographicSpace ghana = store("gs-ghana", null);

        ghana.setParentId("gs-accra");

        assertThatThrownBy(() -> guard.onBeforeConvert(ghana, COLLECTION))
            .isInstanceOf(BadRequestAlertException.class)
            .hasMessageContaining("cannot be its own ancestor")
            .extracting(error -> ((BadRequestAlertException) error).getErrorKey())
            .isEqualTo("cyclicancestor");
    }

    /**
     * A cycle already in the collection, which this save did not create.
     *
     * <p>Rejected rather than ignored, and the walk is what forces the choice: continuing past a
     * space already seen is the non-terminating loop the class exists to prevent, so the only
     * options are to refuse the write or to hang. It is reported under its own key, because
     * "somebody else's rows are broken" is a different message from "the parent you sent is wrong".
     */
    @Test
    void aLeafOntoAnAlreadyCyclicBranchIsRefused() {
        store("gs-a", "gs-b");
        store("gs-b", "gs-a");
        GeographicSpace leaf = new GeographicSpace().id("gs-leaf").name("Leaf").type("DISTRICT").parentId("gs-a");

        assertThatThrownBy(() -> guard.onBeforeConvert(leaf, COLLECTION))
            .isInstanceOf(BadRequestAlertException.class)
            .hasMessageContaining("ancestry above this parent already contains a cycle")
            .extracting(error -> ((BadRequestAlertException) error).getErrorKey())
            .isEqualTo("cyclicancestry");
    }

    /**
     * A space being created has no id yet, so it cannot be its own ancestor — but the chain above it
     * still has to terminate, which is why the walk runs anyway.
     */
    @Test
    void anUnsavedSpaceIsAcceptedUnderAValidParent() {
        store("gs-accra", null);
        GeographicSpace fresh = new GeographicSpace().name("Osu").type("DISTRICT").parentId("gs-accra");

        assertThatCode(() -> guard.onBeforeConvert(fresh, COLLECTION)).doesNotThrowAnyException();
    }

    /**
     * A parent naming nothing stored ends the walk rather than failing it. Referential integrity
     * between collections is enforced nowhere else in this service — {@code Team.geographicSpaceIds}
     * and {@code DutyRoster.geographicSpaceId} are both unvalidated ids — and enforcing it here
     * would make this the one collection whose seed order matters.
     */
    @Test
    void anUnknownParentIsNotACycle() {
        GeographicSpace orphan = new GeographicSpace().id("gs-osu").name("Osu").type("DISTRICT").parentId("gs-nowhere");

        assertThatCode(() -> guard.onBeforeConvert(orphan, COLLECTION)).doesNotThrowAnyException();
    }

    /** An empty parent id is no parent, not a lookup for a document that cannot exist. */
    @Test
    void aBlankStoredParentEndsTheWalk() {
        store("gs-accra", "");
        GeographicSpace osu = new GeographicSpace().id("gs-osu").name("Osu").type("DISTRICT").parentId("gs-accra");

        assertThatCode(() -> guard.onBeforeConvert(osu, COLLECTION)).doesNotThrowAnyException();
    }

    /**
     * And a blank arriving on a save is turned into no parent, rather than stored as one.
     *
     * <p>Tolerating {@code ""} on read is justified by stored data not necessarily having come
     * through this application. Nothing justifies writing it: the walk would pass — {@code findById("")}
     * matches nothing — the empty string would be stored, and {@code GeographicSpaceReferenceDTO}
     * would serve {@code "parentId": ""} to a client that uses a null parent to find the root of the
     * tree. It would read as a second root, and every walk starting there would stop one level early
     * with no error anywhere.
     *
     * <p>Asserted on the entity rather than through the repository because that is what the callback
     * returns: Spring Data converts the object it hands back, so mutating it here is what reaches
     * Mongo.
     */
    @Test
    void aBlankParentOnTheSavedSpaceIsNormalisedToNoParent() {
        GeographicSpace osu = new GeographicSpace().id("gs-osu").name("Osu").type("DISTRICT").parentId("   ");

        GeographicSpace saved = guard.onBeforeConvert(osu, COLLECTION);

        assertThat(saved.getParentId()).isNull();
    }
}
