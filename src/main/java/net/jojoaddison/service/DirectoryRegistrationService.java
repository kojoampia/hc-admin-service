package net.jojoaddison.service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.jojoaddison.domain.DirectoryLink;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.service.dto.RegistrationTotalsDTO;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.stereotype.Service;

/**
 * Counts {@code directory_link} by source and activation state — the estate-wide registration figure
 * behind {@code GET /api/directory-links/registrations}.
 *
 * <p>{@link RegistrationTotalsDTO} carries the decisions: why there are three buckets rather than the
 * two backlog item 75 asked for, what is deliberately counted that a tidier figure would exclude, and
 * why the authorities are the ordinary ones. This class is the arithmetic.
 *
 * <h2>⚠ The third bucket is produced by the query, not repaired afterwards</h2>
 *
 * <p>MongoDB's {@code $group} on a field that is <b>missing</b> and on one that is explicitly
 * {@code null} both yield a {@code null} key, and both mean the same thing here — no event has said.
 * The two are therefore folded together deliberately rather than by accident: a link written before
 * the field existed and one written by a consumer that had nothing to record are the same state, and
 * distinguishing them would be a distinction about this service's own history rather than about the
 * account.
 *
 * <p><b>What must not happen is either of them landing in {@code notActivated}</b>, which is what a
 * {@code $group} read with {@code getBoolean(key, false)} — the obvious Java — would do, silently and
 * plausibly. {@link #bucket} is written to make that a compile-time impossibility rather than a
 * convention: it returns which of three counters to add to, and there is no default.
 */
@Service
public class DirectoryRegistrationService {

    private static final String ACTIVATED = "activated";

    private static final String SOURCE = "source";

    private static final String COUNT = "count";

    private static final String ID = "_id";

    private final MongoTemplate mongoTemplate;

    public DirectoryRegistrationService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * One aggregation over the whole collection, folded into the response.
     *
     * <p>{@code $group} rather than six {@code countDocuments} calls. Not for speed: six independent
     * counts over a collection two consumers are writing to can disagree with each other and with the
     * total, and a chart whose segments do not sum to the number printed beside them is the defect
     * this dashboard has already had twice. One pass cannot be internally inconsistent.
     */
    public RegistrationTotalsDTO totals() {
        Aggregation aggregation = Aggregation.newAggregation(Aggregation.group(SOURCE, ACTIVATED).count().as(COUNT));

        AggregationResults<Document> rows = mongoTemplate.aggregate(aggregation, DirectoryLink.class, Document.class);

        Map<DirectorySource, long[]> bySource = new EnumMap<>(DirectorySource.class);
        for (DirectorySource source : DirectorySource.values()) {
            bySource.put(source, new long[3]);
        }

        for (Document row : rows) {
            Document key = row.get(ID, Document.class);
            if (key == null) {
                continue;
            }
            DirectorySource source = sourceOf(key.getString(SOURCE));
            if (source == null) {
                // A link whose source this enum does not have. Skipped rather than bucketed anywhere:
                // it is a row from a stack this build does not know about, and adding it to a total it
                // has no column for would make the totals disagree with the rows they are made of.
                continue;
            }
            bySource.get(source)[bucket(key)] += count(row);
        }

        List<RegistrationTotalsDTO.SourceTotals> perSource = new ArrayList<>();
        long[] estate = new long[3];
        for (Map.Entry<DirectorySource, long[]> entry : bySource.entrySet()) {
            long[] counts = entry.getValue();
            perSource.add(
                new RegistrationTotalsDTO.SourceTotals(entry.getKey(), counts[0], counts[1], counts[2], counts[0] + counts[1] + counts[2])
            );
            for (int i = 0; i < estate.length; i++) {
                estate[i] += counts[i];
            }
        }

        return new RegistrationTotalsDTO(estate[0], estate[1], estate[2], estate[0] + estate[1] + estate[2], perSource);
    }

    /**
     * Which of the three counters this group key belongs in: {@code 0} activated, {@code 1} not
     * activated, {@code 2} <b>not reported</b>.
     *
     * <p>{@code Document.get} rather than {@code Document.getBoolean(key, false)}, and the difference
     * is the whole of item 75's warning: the defaulting form maps "no event has said" onto "the
     * account cannot sign in", which is a claim, and it does it invisibly. There is deliberately no
     * default branch here — an absent key and an explicit null both fall to {@code 2} because neither
     * is a {@code Boolean}.
     */
    private static int bucket(Document key) {
        Object activated = key.get(ACTIVATED);
        if (activated instanceof Boolean flag) {
            return flag ? 0 : 1;
        }
        return 2;
    }

    private static DirectorySource sourceOf(String stored) {
        if (stored == null) {
            return null;
        }
        try {
            return DirectorySource.valueOf(stored);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /** {@code $sum} answers with whichever numeric type MongoDB chose, so this reads a {@link Number}. */
    private static long count(Document row) {
        Object count = row.get(COUNT);
        return count instanceof Number number ? number.longValue() : 0L;
    }
}
