package net.jojoaddison.config;

import java.util.Arrays;
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
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Brings stored shift data up to the 2026-09-04 model: backfills the {@code wage_rate} column that
 * change added, and reports any stored shift value the {@link ShiftType} enum can no longer parse.
 *
 * <p>Written with {@code FLEXIBLE} on 2026-09-04 in the shape of hc-professional's
 * {@code config/ShiftTypeMigration}, because neither repo has Liquibase or Mongock and an
 * {@link ApplicationRunner} is the only migration seam either of them has.
 *
 * <h2>The enum change rewrote nothing; the schema change did</h2>
 *
 * <p>This class said it rewrote nothing at all until 2026-09-04, and covered only
 * {@code shift_assignment} and {@code duty_roster} — <b>the two collections that needed nothing, and
 * not the one that did.</b> That was right about the enum and wrong about the release: adding
 * {@code FLEXIBLE} retired no value and renamed none, so every stored {@code shift} string still
 * parses; but the same change gave {@link net.jojoaddison.domain.WageRate} a <b>required</b>
 * {@code shift_type}, and every row written before it has none. The class's own Javadoc said it
 * existed "for a forgotten pre-launch database" and that "the migration was empty" and "the
 * migration was forgotten" look identical from a green build — which is what happened, one
 * collection along.
 *
 * <p>The consequence was not a mapping failure. {@code WageRateService.rateTableUpTo} groups rates
 * into an {@code EnumMap}, which rejects a null key, so <b>one such row turned the wage-rates screen
 * and every earnings screen into a 500 with a stack trace naming an {@code EnumMap}</b> — reachable
 * from any rate created through the console's "Set new rate" on a dev or quality database, i.e. any
 * row without a seeded id that {@code DevelopmentDataInitializer} overwrites on restart.
 *
 * <h2>Why {@code DAY}, and what it costs</h2>
 *
 * <p>A row written before the dimension existed carried no opinion about shift types: it meant "what
 * this role is paid for a shift". There is no correct answer to what it becomes and guessing quietly
 * would be worse than refusing, so the choice is stated here.
 *
 * <p><b>Fanning the row out across the four worked shift types was rejected.</b> It preserves every
 * shift's valuation, and that is exactly its problem: it invents four authored prices nobody set and
 * asserts that a night pays what a day pays — the claim the second dimension exists to stop making.
 * Once written, those rows are indistinguishable from rates somebody decided on.
 *
 * <p><b>So the row lands in one cell and the rest stay unpriced.</b> {@code DAY} is that cell: it is
 * the baseline shift, and a flat per-role rate is more nearly a statement about a day than about a
 * night premium or a negotiated block. {@code OFF} would put the rate where nothing ever reads it —
 * {@code ShiftValuationService} drops an off day before resolving any rate — and {@code NIGHT} or
 * {@code FLEXIBLE} would claim a premium that was never authored.
 *
 * <p>The cost is real and is the honest shape of the loss: a night worked before the migration is
 * reported as <b>unpriced</b> rather than valued at the old flat rate. The console renders that
 * distinctly from a rate of zero and {@code ProfessionalEarningsDTO.unpricedShifts} counts it, so it
 * reads as "nobody set a price for this cell", which is true. An under-report that says so is worth
 * more than a plausible total nobody would query.
 *
 * <h2>Idempotent by construction</h2>
 *
 * <p>The backfill matches only documents where {@code shift_type} is missing or null, so a second run
 * matches nothing; the reporting half reads and never writes. Neither can be wrong about whether it
 * has run.
 *
 * <h2>Which profiles it is excluded from, and which it must not be</h2>
 *
 * <p>The exclusion is {@code !testdev & !testprod} — the two profiles {@code pom.xml} actually
 * activates for an integration-test run, via {@code -Dspring.profiles.active=${profile.test}} on
 * surefire/failsafe. It said {@code !test} until 2026-09-04, and <b>that expression excluded nothing
 * from a build and disabled the one deployment that needed it</b>: the Spring profile {@code test} is
 * never active under {@code ./mvnw verify}, while {@code dev,test} is exactly what the quality stack
 * runs. Twelve {@code wage_rate} rows kept a null {@code shift_type} through every restart there and
 * the wage-rates and earnings screens answered 500 until the stack was reseeded.
 *
 * <p><b>Nothing about an integration test ever depended on that annotation, and the next reader
 * should not re-derive that it did.</b> Spring Boot does not invoke {@link ApplicationRunner} beans
 * under {@code @SpringBootTest} at all — it is {@code SpringApplication.run} that calls them, and a
 * test context is built without it — so an IT's fixtures were never reachable from here whatever the
 * profile said. The expression above is the house idiom ({@code AsyncConfiguration} beside it) and is
 * strictly the safer of the two: it starts genuinely excluding IT contexts, which {@code !test} never
 * did, while letting the bean register on {@code dev,test} where the migration is the point.
 *
 * <p>{@code ShiftTypeMigrationIT} constructs the class directly rather than relying on either, which
 * is also the only way to hand it a value the enum cannot express.
 * {@code ShiftTypeMigrationProfileTest} pins the registration itself.
 */
@Component
@Profile("!testdev & !testprod")
public class ShiftTypeMigration implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ShiftTypeMigration.class);

    /**
     * Where a {@link ShiftType} is stored, and under what field name.
     *
     * <p>The three names differ and the difference is the bug this list was missing: the roster
     * collections call it {@code shift}, {@code wage_rate} calls it {@code shift_type}. A sweep that
     * assumed one name would have reported {@code wage_rate} as clean forever.
     *
     * <p><b>{@code duty_roster} stays, and that is a decision rather than an oversight.</b> The
     * entity was deleted from this service on 2026-09-04; the collection was not, because deleting a
     * {@code @Document} class deletes no documents. The database this sweep is written for — a
     * pre-launch one that has not been started since before the model settled — is exactly the
     * database still holding those rows, and the sweep's half that touches this collection only
     * reports. Against a database that never had the collection it is one {@code count} returning
     * zero at startup; against one that did, it is the only line anywhere that would say so. Dropping
     * it would buy nothing and would silence the case it was written for.
     */
    private record ShiftColumn(String collection, String field) {}

    private static final List<ShiftColumn> COLUMNS = List.of(
        new ShiftColumn("shift_assignment", "shift"),
        new ShiftColumn("duty_roster", "shift"),
        new ShiftColumn("wage_rate", "shift_type")
    );

    /** The one column that gained a required value, and what a row written before it becomes. */
    private static final ShiftColumn BACKFILLED = new ShiftColumn("wage_rate", "shift_type");

    private static final ShiftType BACKFILL_VALUE = ShiftType.DAY;

    private final MongoTemplate mongoTemplate;

    public ShiftTypeMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        backfillMissingWageRateShiftType();
        reportUnknownValues();
    }

    /**
     * Every {@code wage_rate} row without a shift type becomes a {@link #BACKFILL_VALUE} rate.
     *
     * <p>{@code is(null)} matches a missing field as well as an explicitly null one, which is what
     * makes this cover both a row written before the field existed and a row written around the
     * validation. Logged at warn with the count, because it is a rewrite of stored money and should
     * be visible in a startup log somebody scrolls past.
     */
    private void backfillMissingWageRateShiftType() {
        Query missing = new Query(Criteria.where(BACKFILLED.field()).is(null));
        long count = mongoTemplate.count(missing, BACKFILLED.collection());
        if (count == 0) {
            LOG.debug("Every {} row carries a {} — nothing to backfill", BACKFILLED.collection(), BACKFILLED.field());
            return;
        }
        mongoTemplate.updateMulti(missing, new Update().set(BACKFILLED.field(), BACKFILL_VALUE.name()), BACKFILLED.collection());
        LOG.warn(
            "Backfilled {} to {} on {} {} row(s) that predate the shift-type dimension. " +
                "The other shift types stay unpriced for those roles rather than inheriting this rate, " +
                "so shifts worked at them are reported as unpriced rather than valued at a rate nobody set.",
            BACKFILLED.field(),
            BACKFILL_VALUE,
            count,
            BACKFILLED.collection()
        );
    }

    /**
     * Counts values outside the enum on every collection carrying one, and leaves them alone.
     *
     * <p>Its subject is a pre-launch database that has not been started since before the console's
     * model settled, where a retired string would otherwise surface as a Jackson mapping failure on
     * whichever read happened to touch the row first, naming the field and not the row.
     *
     * <p>Reporting rather than rewriting is deliberate: hc-professional's equivalent could map
     * {@code MORNING} and {@code AFTERNOON} because somebody decided which surviving window each one
     * overlapped most, and no such decision exists for a string nobody has seen. Rewriting it to a
     * plausible neighbour would destroy the only evidence of what it had been.
     */
    private void reportUnknownValues() {
        List<String> known = Arrays.stream(ShiftType.values()).map(Enum::name).toList();
        long total = 0;
        for (ShiftColumn column : COLUMNS) {
            // `exists` as well as `nin`, so a row with no shift at all is not reported as an unknown
            // value — it is a different defect and, for wage_rate, one the backfill above has
            // already dealt with.
            Query unknown = new Query(Criteria.where(column.field()).exists(true).nin(known));
            long count = mongoTemplate.count(unknown, column.collection());
            if (count > 0) {
                LOG.warn(
                    "{} row(s) in {} hold a {} value outside {} — they will fail to map when read. " +
                        "Nothing was rewritten: this migration reports, it does not guess.",
                    count,
                    column.collection(),
                    column.field(),
                    known
                );
                total += count;
            }
        }
        if (total == 0) {
            LOG.debug("Every stored shift value is one of {} — nothing to report", known);
        }
    }
}
