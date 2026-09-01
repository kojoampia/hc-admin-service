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
 * the server owns belongs below the layer that can be bypassed.
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
 * {@code DutyRoster.geographicSpaceId} are both unvalidated ids — and inventing that rule here would
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
                throw new BadRequestAlertException(
                    "A geographic space cannot be its own ancestor",
                    ENTITY_NAME,
                    id != null && id.equals(ancestorId) ? "cyclicparent" : "cyclicancestry"
                );
            }
            ancestorId =
                geographicSpaceRepository
                    .findById(ancestorId)
                    .map(GeographicSpace::getParentId)
                    .flatMap(GeographicSpaceCycleGuard::presentAndDifferent)
                    .orElse(null);
        }
        return space;
    }

    /**
     * An empty parent id is the same as no parent.
     *
     * <p>Stored data is not required to have been written through this application, and {@code ""}
     * read back as a parent would send {@code findById} looking for a document that cannot exist —
     * ending the walk anyway, but by accident rather than because the chain reached a root.
     */
    private static Optional<String> presentAndDifferent(String parentId) {
        return parentId.isBlank() ? Optional.empty() : Optional.of(parentId);
    }
}
