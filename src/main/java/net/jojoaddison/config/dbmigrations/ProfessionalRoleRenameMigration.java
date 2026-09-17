package net.jojoaddison.config.dbmigrations;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Rewrites every stored {@code CAREGIVER} to {@code CARER} — backlog item 35, decision D3.
 *
 * <h2>Why a rename of an enum constant is a migration at all</h2>
 *
 * <p>{@link net.jojoaddison.domain.enumeration.ProfessionalRole} is persisted: {@code Professional}
 * and {@code WageRate} each declare it {@code @NotNull @Field("role")}, and Spring Data stores an
 * enum as its {@link Enum#name()}. So the rename leaves every row written before it holding a string
 * the class can no longer parse, and the failure surfaces on <b>whichever read touches such a row
 * first</b> as a conversion error naming the field and not the row — the shape
 * {@code ShiftTypeMigration} was written to report for {@code shift}, one collection along.
 *
 * <p>The cost is worst where it is least visible. {@code dev} and {@code test} seed 25 wage rates and
 * two professionals under the old name, so without this the seed itself fails to map and
 * <b>CI goes red for a reason that names Jackson</b>; and in production a wage rate that cannot be
 * read is a shift that cannot be valued, which {@code ShiftValuationService} reports as
 * <em>unpriced</em> rather than as an error. A wage bill quietly missing a role's rows looks exactly
 * like a role nobody has priced yet.
 *
 * <h2>Which collections, and how that list was arrived at</h2>
 *
 * <p>Two, and the list is the whole survey rather than the two that came to mind:
 * {@code grep -rn ProfessionalRole src/main/java/net/jojoaddison/domain/} names
 * {@code Professional} and {@code WageRate} and nothing else, both storing it under
 * {@code @Field("role")}. The {@code role} fields on the DTOs are not persisted, and
 * {@code RoundPlanDtos} crosses the wire to hc-professional rather than into a collection.
 *
 * <p><b>{@code duty_roster.duty} is deliberately not in the list.</b> That collection outlives the
 * entity deleted on 2026-09-04 — deleting a {@code @Document} class deletes no documents — and it
 * held the retired {@code DutyRole}, a different vocabulary ({@code CARE, VENDOR, MEDIC,
 * ADMINISTRATOR} …) that never had a {@code CAREGIVER} in it. Sweeping it would be a rename applied
 * to a word that means something else.
 *
 * <h2>A one-shot change unit, and idempotent anyway</h2>
 *
 * <p>Unlike {@code ServicePlanCodeBackfillMigration} this is not {@code runAlways}: it is a genuine
 * one-time reshaping with no reporting half, and once the code no longer emits {@code CAREGIVER}
 * nothing can write the old value again. It is idempotent regardless — the criterion is
 * {@code role: "CAREGIVER"}, so a second execution matches nothing and writes nothing, which is what
 * makes a partial run (the two {@code updateMulti} calls are not in one transaction) safe to resume.
 *
 * <p>Written in raw documents through {@link MongoTemplate} rather than through the mapped types,
 * for the reason the mapped types are the problem: reading a {@code CAREGIVER} row as a
 * {@code WageRate} is the conversion failure this exists to prevent, so it could not load the rows it
 * has to fix. A field-targeted {@code $set} also leaves every other field alone — including
 * {@code amount}, which is money somebody authored — where a mapped {@code save} would rewrite the
 * whole document from the fields the class declares today.
 */
@ChangeUnit(id = "professional-role-caregiver-to-carer", order = "002", author = "hc-admin")
public class ProfessionalRoleRenameMigration {

    private static final Logger LOG = LoggerFactory.getLogger(ProfessionalRoleRenameMigration.class);

    /** Every collection that stores a {@code ProfessionalRole}, and the field it stores it under. */
    static final List<String> COLLECTIONS = List.of("professional", "wage_rate");

    private static final String ROLE = "role";

    private static final String OLD_NAME = "CAREGIVER";

    private static final String NEW_NAME = "CARER";

    private final MongoTemplate mongoTemplate;

    public ProfessionalRoleRenameMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Execution
    public void migrate() {
        rename(OLD_NAME, NEW_NAME);
    }

    /**
     * Puts {@code CAREGIVER} back.
     *
     * <p>Unlike its two neighbours in this package this rollback really is the inverse, and is safe
     * to be: a rename loses nothing, there is nothing an operator can have edited in between that it
     * would discard, and the only rows it can touch are the ones the forward run created — no
     * document held {@code CARER} before this shipped, because the constant did not exist.
     *
     * <p><b>It is still only meaningful beside a rollback of the code.</b> Reversing the data while
     * this version of the service is deployed reinstates exactly the unreadable rows the forward run
     * removed. Mongock calls it when the forward execution throws, which is the case it is for: the
     * two writes are not atomic, so a failure between them leaves one collection renamed and the
     * other not.
     */
    @RollbackExecution
    public void rollback() {
        LOG.warn(
            "Rolling {} back to {} — this only makes sense alongside a rollback of the service itself, " +
                "since this version cannot read the old value",
            NEW_NAME,
            OLD_NAME
        );
        rename(NEW_NAME, OLD_NAME);
    }

    /**
     * One targeted {@code $set} per collection, reported per collection.
     *
     * <p>Counted before the write rather than taken from the update result so that the log line can
     * be written when there is something to say and skipped when there is not — a startup log that
     * says "renamed 0 rows" on every deploy forever is a line people learn to scroll past.
     */
    private void rename(String from, String to) {
        long total = 0;
        for (String collection : COLLECTIONS) {
            Query holdingOldName = new Query(Criteria.where(ROLE).is(from));
            long count = mongoTemplate.count(holdingOldName, collection);
            if (count == 0) {
                continue;
            }
            mongoTemplate.updateMulti(holdingOldName, new Update().set(ROLE, to), collection);
            LOG.warn("Renamed {} to {} on {} {} row(s)", from, to, count, collection);
            total += count;
        }
        if (total == 0) {
            LOG.debug("No {} stored under {} in {} — nothing to rename", ROLE, from, COLLECTIONS);
        }
    }
}
