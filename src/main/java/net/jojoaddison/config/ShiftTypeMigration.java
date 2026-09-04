package net.jojoaddison.config;

import java.util.List;
import net.jojoaddison.domain.enumeration.ShiftType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import tech.jhipster.config.JHipsterConstants;

/**
 * Reports any stored {@code shift} value the {@link ShiftType} enum can no longer parse.
 *
 * <p>Added with {@code FLEXIBLE} on 2026-09-04, in the shape of hc-professional's
 * {@code config/ShiftTypeMigration}, because neither repo has Liquibase or Mongock and an
 * {@link ApplicationRunner} is the only migration seam either of them has.
 *
 * <p><b>It rewrites nothing, and that is the correct outcome rather than an unfinished one.</b> The
 * superset change <em>added</em> a value; it retired none and renamed none, so every document
 * already in a database still holds a string the enum accepts. There is no mapping to apply and no
 * data to move. hc-professional's equivalent had real work to do because DR1 retired {@code MORNING}
 * and {@code AFTERNOON}; this one never has.
 *
 * <p><b>So why does it exist at all?</b> Because "the migration was empty" and "the migration was
 * forgotten" are indistinguishable from a green build, and the next change to this enum may not be
 * additive. A named class that a reader finds when they go looking, saying in one place that the
 * 2026-09-04 change needed no rewrite, is worth more than the absence of one. It also gives the
 * <em>next</em> retirement somewhere obvious to go, which is how hc-professional's came to be
 * correct rather than remembered.
 *
 * <p>What it does do is <b>look</b>: it counts {@code shift} values on both collections that are not
 * in the enum and logs a warning naming them. That is a real check and it has a real subject — a
 * pre-launch database that has not been started since before the console's model settled, where a
 * retired string would otherwise surface as a Jackson mapping failure on whichever read happened to
 * touch the row first, naming the field and not the row.
 *
 * <p><b>Idempotent by construction.</b> It reads and never writes, so running it every start costs
 * two counted queries and cannot be wrong about whether it has run.
 *
 * <p>Excluded from the test profile so its logging cannot fire against an integration test's
 * fixtures mid-run. {@code ShiftTypeMigrationTest} constructs it directly instead, which is also the
 * only way to hand it a value the enum cannot express.
 */
@Component
@Profile("!" + JHipsterConstants.SPRING_PROFILE_TEST)
public class ShiftTypeMigration implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ShiftTypeMigration.class);

    /** Both collections carrying a {@code ShiftType}. The console's grid is {@code shift_assignment}. */
    private static final List<String> COLLECTIONS = List.of("shift_assignment", "duty_roster");

    private static final String FIELD = "shift";

    private final MongoTemplate mongoTemplate;

    public ShiftTypeMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> known = java.util.Arrays.stream(ShiftType.values()).map(Enum::name).toList();
        long total = 0;
        for (String collection : COLLECTIONS) {
            // `exists` as well as `nin`, so a row with no shift at all is not reported as an unknown
            // value — it is a different defect and a different fix.
            Query unknown = new Query(Criteria.where(FIELD).exists(true).nin(known));
            long count = mongoTemplate.count(unknown, collection);
            if (count > 0) {
                LOG.warn(
                    "{} row(s) in {} hold a {} value outside {} — they will fail to map when read. " +
                    "Nothing was rewritten: this migration reports, it does not guess.",
                    count,
                    collection,
                    FIELD,
                    known
                );
                total += count;
            }
        }
        if (total == 0) {
            LOG.debug("Every stored {} value is one of {} — nothing to migrate", FIELD, known);
        }
    }
}
