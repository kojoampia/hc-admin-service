package net.jojoaddison.config;

import net.jojoaddison.domain.DirectoryLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * The one index this service creates, on {@code directory_link}'s natural key.
 *
 * <h2>Why it is created here rather than declared on the document</h2>
 *
 * <p>This service creates no indexes: there is no {@code @Indexed} anywhere and
 * {@code spring.data.mongodb.auto-index-creation} is left off, so a {@code @CompoundIndex} on
 * {@link DirectoryLink} would be a <b>comment rather than a constraint</b> — which is exactly what
 * the class javadoc there used to claim was fine. Turning auto-index-creation on globally would
 * create indexes for forty other collections that nobody has thought about, at startup, on every
 * environment. One explicit index for one collection is the smaller change and the honest one.
 *
 * <h2>Both halves of what it is for</h2>
 *
 * <p><b>It is the lookup index, and there was none.</b> {@code (source, external_key)} is read on
 * every single message — {@code findSubject} on the update path, and the upsert's own query on the
 * create path — against a collection documented as growing at the rate two other stacks create
 * accounts, and re-read in full on every backfill from the earliest offset. Without an index that is
 * a collection scan per event.
 *
 * <p><b>And it is unique, which is what makes the idempotency claim true.</b> MongoDB's
 * {@code findAndModify} with {@code upsert} is atomic per document, but two concurrent upserts that
 * match nothing <em>can both insert</em> — the documented requirement for "at most one" is a unique
 * index on the query field, and this service had none. The exposure was narrow, because every event
 * about one subject carries the same partition key and so arrives on one thread, and it was not
 * nothing: a consumer-group rebalance can put two instances briefly on one partition. With the index
 * the loser gets a {@code DuplicateKeyException}, which {@code DirectoryEventConsumers} now rethrows,
 * so the binder retries it and the retry finds the link and updates it.
 *
 * <h2>A duplicate in the data is reported, not fatal</h2>
 *
 * <p>Creating a unique index over a collection that already holds duplicates fails. Failing startup
 * on that would turn a data problem into an outage, in a service whose first screen is a dashboard,
 * so it is logged at {@code error} with what to do and the application carries on — the lookup is
 * then unindexed and the uniqueness unenforced, which is precisely the state before this class
 * existed. There are no such rows to find today: nothing has been deployed.
 */
@Component
public class DirectoryLinkIndexes {

    /** Named rather than left to MongoDB's field-concatenation default, so a reader can find it. */
    static final String SUBJECT_INDEX = "directory_link_subject_key";

    private static final Logger LOG = LoggerFactory.getLogger(DirectoryLinkIndexes.class);

    private final MongoTemplate mongoTemplate;

    public DirectoryLinkIndexes(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Creates the index if it is not there, on a started application.
     *
     * <p>{@code createIndex} is idempotent for an identical specification, so this runs on every boot
     * and does nothing on all but the first.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void createSubjectIndex() {
        try {
            mongoTemplate
                .indexOps(DirectoryLink.class)
                .createIndex(
                    new Index().on("source", Sort.Direction.ASC).on("external_key", Sort.Direction.ASC).unique().named(SUBJECT_INDEX)
                );
            LOG.debug("directory_link is indexed uniquely on (source, external_key)");
        } catch (RuntimeException e) {
            LOG.error(
                "Could not create the unique index {} on directory_link. Until it exists, every event costs a " +
                    "collection scan and two concurrent first sightings of one subject can both insert. If this is a " +
                    "duplicate-key failure, find the duplicates with an aggregation grouping on (source, external_key), " +
                    "merge them onto the one whose local_id names a live record, and restart.",
                SUBJECT_INDEX,
                e
            );
        }
    }
}
