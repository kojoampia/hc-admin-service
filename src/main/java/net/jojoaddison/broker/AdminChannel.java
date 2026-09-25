package net.jojoaddison.broker;

/**
 * Facts that are true of every frame on {@code admin.event}, whatever its {@code type}.
 *
 * <p>This class exists because item 145 put a second and a third {@code type} on a channel that had
 * carried one. Until then the partition rule lived as a private helper on
 * {@code config.EntityChangeAnnouncer}, which was correct while that class was the channel's only
 * publisher and became the wrong home the moment it was not — three publishers each computing "the
 * same" key is three places for them to stop being the same, and the failure is invisible from here
 * because every producer stays healthy while ordering quietly stops holding.
 *
 * <h2>The rule, and why it is the same for a command as for a notification</h2>
 *
 * <p>Kafka orders within a partition and not across one, so the key decides what "in order" means on
 * this channel. Keying on {@code <entityType>/<entityId>} means every frame <em>about one record</em>
 * arrives in the order it was published — and since item 145 that includes frames of different types
 * about the same record. A {@code PlanVerified} for {@code DirectoryLink/dl-p12} and an
 * {@code EntityChanged} for {@code DirectoryLink/dl-p12} land on the same partition, so a consumer
 * cannot see the link's change and the decision taken against it out of order.
 *
 * <p><b>That property is why the architect's D1 chose a record-shaped subject for command frames</b>
 * (2026-09-25, backlog item 145) rather than keying a command on its addressee. Keying the plan
 * verification on the patient's address would have ordered it against nothing else on the channel.
 *
 * <p>Qualified by the entity type because ids here are not globally unique: the seed uses short
 * literals like {@code a13}, so keying on the id alone would put unrelated records from different
 * collections on one partition and, worse, make that look deliberate.
 *
 * <p>hc-vendor's {@code VendorEventPublisher} keys the same pair for the same stated reason, which is
 * convergence rather than copying — neither producer was written from the other.
 *
 * <p><b>Changing this later is not free</b>: changing a topic's partitioning changes the ordering
 * guarantee for frames already in flight. It was decided while the channel had no consumer reading it
 * in anger, and item 145's dual-publish does not change it.
 */
public final class AdminChannel {

    private AdminChannel() {}

    /**
     * The partition key for any frame on {@code admin.event}.
     *
     * @param entityType the subject's entity type — a domain class's simple name, never a collection
     *                   name; see {@link AdminEntityEvent}'s javadoc for why.
     * @param entityId   the subject's id, or null. A null id keys on the type alone, which is honest
     *                   rather than wrong: it keeps such frames together instead of spreading them.
     * @return the key, never null when {@code entityType} is non-null.
     */
    public static String partitionKey(String entityType, String entityId) {
        return entityId == null ? entityType : entityType + "/" + entityId;
    }
}
