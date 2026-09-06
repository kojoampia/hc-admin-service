package net.jojoaddison.config;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.jojoaddison.domain.GeographicSpace;
import net.jojoaddison.repository.GeographicSpaceRepository;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

/**
 * Refuses to store a {@link GeographicSpace} that would contain itself.
 *
 * <h2>Why this is a callback rather than a service method</h2>
 *
 * <p>The obvious home for the rule is a {@code GeographicSpaceService.save}, and there is no such
 * service because there is nothing to call it: this collection has no write endpoint, and the only
 * writer today is {@code DevelopmentDataInitializer}, which calls {@code saveAll} on the repository.
 * A guard living in a service nobody calls is a comment that compiles — it would pass its own unit
 * test and protect nothing, and the first real writer would be whoever adds the CRUD resource and
 * does not know the service exists.
 *
 * <p>A {@code BeforeConvertCallback} sits under every writer instead, present and future, including
 * the seed. That is the same reasoning that put {@code AuditingEntityCallback},
 * {@link MessageLifecycleCallback} and {@link TaskLifecycleCallback} where they are: an invariant
 * the server owns belongs below the layer that can be bypassed. <b>"Every writer" is about position,
 * not about strength</b> — it means no caller can route around the check, not that the check holds
 * under every schedule. See the next section before relying on it.
 *
 * <h2>What it does not cover: concurrent writers</h2>
 *
 * <p>The walk reads stored ancestry and then the save is applied, so this is check-then-act with no
 * lock. Two writes racing — one setting {@code a.parentId = b}, the other {@code b.parentId = a} —
 * each read the other's <em>stored</em> parent, which is still null, and both pass. The collection
 * then holds the cycle this class exists to prevent, and the cost lands where the class doc above
 * says it does: every read that walks through that branch hangs, and the next save onto the branch is
 * refused with {@code cyclicancestry} — blaming a writer that did nothing wrong, long after the two
 * that did. It is a one-writer guard honestly, not a serialisable constraint.
 *
 * <p>Deliberately not fixed today, because it is unreachable today: this collection has no write
 * endpoint, and the only writer is {@code DevelopmentDataInitializer} calling {@code saveAll} on a
 * single thread at startup. Whoever adds the CRUD resource makes the write concurrent and inherits
 * the question — an optimistic {@code @Version} on {@link GeographicSpace}, or serialising reparenting
 * behind one lock, are the two shapes that close it. Documented rather than left to be discovered
 * from a hung request.
 *
 * <p><b>The consequence for readers is the part that matters now.</b> A walk up {@code parentId} must
 * not assume this guard held — the proximity ranking, and anything else that climbs the tree, has to
 * carry its own visited set or step bound and give up rather than loop. The {@code while} below is the
 * model: it grows a {@code seen} set every iteration, so it either reaches a root or throws. A reader
 * that trusts the write-side invariant is exactly the non-terminating request described above.
 *
 * <h2>What a cycle costs if it is allowed through</h2>
 *
 * <p>Proximity is a walk up {@code parentId} — same space, then same parent, then same ancestor.
 * A cycle makes that walk non-terminating, so the failure is not a wrong answer on one screen but a
 * hung request wherever the tree is read, arriving long after the save that caused it and pointing
 * at the reader rather than the writer. The rejection is deliberately at write time for that reason.
 *
 * <h2>What it does and does not check</h2>
 *
 * <p>It walks the stored ancestry of the incoming parent and refuses if the space being saved turns
 * up in it — self-parenting is the one-step case of that, not a separate rule. It also refuses if
 * the walk revisits any space, which means a cycle already in the collection: that is corrupt data
 * this save did not introduce, and continuing would be the non-terminating walk this class exists to
 * prevent.
 *
 * <p>It does not check that the parent exists. Nothing else in this service enforces referential
 * integrity between collections — {@code Team.geographicSpaceIds} and
 * {@code Professional.homeSpaceId} are both unvalidated ids — and inventing that rule here would
 * make this the one collection whose seed order matters.
 */
@Component
public class GeographicSpaceCycleGuard implements BeforeConvertCallback<GeographicSpace> {

    static final String ENTITY_NAME = "geographicSpace";

    private final GeographicSpaceRepository geographicSpaceRepository;

    /** Lazy: the repository is built on the very template that publishes this callback. */
    public GeographicSpaceCycleGuard(@Lazy GeographicSpaceRepository geographicSpaceRepository) {
        this.geographicSpaceRepository = geographicSpaceRepository;
    }

    @Override
    public GeographicSpace onBeforeConvert(GeographicSpace space, String collection) {
        String id = space.getId();
        String parentId = space.getParentId();
        // A blank parent is no parent, and it is normalised on the way IN as well as on the way out.
        // presentAndNonBlank below only cleans up what it reads back; without this, a save carrying
        // "" passed the walk (findById("") matches nothing), stored "", and
        // GeographicSpaceReferenceDTO served {"parentId": ""} — so a client using a null parent to
        // find the root of the tree saw a second root whose parent was the empty string. The
        // justification for tolerating it on read is that stored data need not have been written
        // through this application; that argument does not cover this application writing it.
        if (parentId != null && parentId.isBlank()) {
            space.setParentId(null);
            parentId = null;
        }
        if (parentId == null) {
            return space;
        }
        if (parentId.equals(id)) {
            throw new BadRequestAlertException("A geographic space cannot be its own parent", ENTITY_NAME, "cyclicparent");
        }
        // A space with no id yet cannot be its own ancestor, but the chain above it still has to
        // terminate — a save that adds a leaf onto an already-cyclic branch would otherwise be the
        // one write that gets through.
        Set<String> seen = new HashSet<>();
        if (id != null) {
            seen.add(id);
        }
        String ancestorId = parentId;
        while (ancestorId != null) {
            if (!seen.add(ancestorId)) {
                // Two different faults, so two messages and two keys that agree with them. The key
                // used to be cyclicparent for the first branch, under the "own ancestor" message
                // below — deliberate, because both branches are still about a parent id, but it
                // reads as a copy-paste slip to whoever is triaging the client error, and the pair
                // that matters to a caller is "what you sent" against "what was already stored".
                //
                // cyclicancestor and cyclicancestry are near-identical strings and that is not a
                // typo either way: an ancestor is the node (this space turned up as one of its
                // own), an ancestry is the chain (the chain above the parent loops, and this save
                // is not what did it). The messages use those two words for the same reason.
                if (id != null && id.equals(ancestorId)) {
                    throw new BadRequestAlertException("A geographic space cannot be its own ancestor", ENTITY_NAME, "cyclicancestor");
                }
                throw new BadRequestAlertException(
                    "The ancestry above this parent already contains a cycle",
                    ENTITY_NAME,
                    "cyclicancestry"
                );
            }
            ancestorId =
                geographicSpaceRepository
                    .findById(ancestorId)
                    .map(GeographicSpace::getParentId)
                    .flatMap(GeographicSpaceCycleGuard::presentAndNonBlank)
                    .orElse(null);
        }
        return space;
    }

    /**
     * An empty parent id is the same as no parent.
     *
     * <p>Stored data is not required to have been written through this application, and {@code ""}
     * read back as a parent would send {@code findById} looking for a document that cannot exist —
     * ending the walk anyway, but by accident rather than because the chain reached a root. What
     * <em>this</em> application writes is normalised at the top of
     * {@link #onBeforeConvert(GeographicSpace, String)} instead, so a blank never reaches storage
     * from here in the first place.
     */
    private static Optional<String> presentAndNonBlank(String parentId) {
        return parentId.isBlank() ? Optional.empty() : Optional.of(parentId);
    }
}
