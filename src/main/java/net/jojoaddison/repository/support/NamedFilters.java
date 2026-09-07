package net.jojoaddison.repository.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Builds a paged query from the handful of named filters the console actually sends.
 *
 * <p>This is not a criteria framework and must not become one. Each resource still declares its
 * filters as explicit {@code @RequestParam}s with real names, so the API surface is a fixed list
 * that can be read off the method signature. What lives here is only the mechanical part: combining
 * however many of them arrived into one query, and paging it.
 *
 * <p>It exists because the alternative does not scale. The archived filter was served by a
 * repository method per case — {@code findArchived}, {@code findNotArchived} — which is fine for one
 * boolean and impossible for four independent optional filters, where every combination needs its
 * own method and every new filter doubles them.
 *
 * <p><b>An unknown request parameter is silently ignored by Spring.</b> That is how
 * {@code status.equals} came to be accepted and dropped on every list endpoint: the client sent it,
 * the response looked healthy, and the filter did nothing. Anything added here has to be declared on
 * the handler, or it will do the same.
 */
public final class NamedFilters {

    private NamedFilters() {}

    /**
     * Collects the criteria that were actually supplied. Nulls and blanks are simply absent.
     *
     * <p><b>The four value operators do not agree on what "blank" means, and only {@code equals} is
     * currently used with strings.</b> {@code equals} drops a blank string; {@code notEquals} keeps
     * it and would build a real {@code $ne: ""}; {@code in} passes blank elements through;
     * {@code contains} drops blanks and additionally trims. Nothing is wrong today — both
     * {@code notEquals} callers pass Booleans — but the first String {@code notEquals} will behave
     * opposite to {@code equals}, which is not what its name suggests. Decide that deliberately when
     * it happens rather than inheriting it by accident.
     *
     * <p>Dropping a blank is also not free at the call site: a filter that vanishes is a query that
     * returns everything. Where a filter decides <em>who the caller is</em> rather than what they are
     * browsing, reject the blank at the handler instead — {@code VendorResource}'s
     * {@code accountId.equals} does, and says why.
     *
     * <p><b>A blank never reaches a non-String operator as a blank, which is why the rule above is
     * not enough on its own.</b> Spring's own converters answer {@code null} for the empty string —
     * measured on this classpath, {@code DefaultConversionService.convert("", Boolean.class)} and the
     * same for an enum both return {@code null} — so {@code ?unlinked=} and {@code ?source=} arrive
     * here already indistinguishable from having been left off. Nothing in this class can tell the
     * two apart, and nothing in it should try: the handler is the only place that still holds the raw
     * request. {@code DirectoryLinkResource} refuses both, and says why there.
     */
    public static final class Builder {

        private final List<Criteria> criteria = new ArrayList<>();

        public Builder equals(String field, Object value) {
            if (value != null && !(value instanceof String text && text.isBlank())) {
                criteria.add(Criteria.where(field).is(value));
            }
            return this;
        }

        /**
         * {@code $ne} rather than {@code is(!value)}, because a document written before the field
         * existed does not carry it and {@code field: false} matches none of them.
         */
        public Builder notEquals(String field, Object value) {
            if (value != null) {
                criteria.add(Criteria.where(field).ne(value));
            }
            return this;
        }

        public Builder in(String field, Collection<?> values) {
            if (values != null && !values.isEmpty()) {
                criteria.add(Criteria.where(field).in(values));
            }
            return this;
        }

        /**
         * Whether the field is unset — {@code TRUE} for the documents that do not carry a value,
         * {@code FALSE} for the ones that do. A null asks nothing, like every other operator here.
         *
         * <p><b>Null means "was not asked", and that is not the same as "was left blank" — the
         * caller has to have decided which it is before calling.</b> This javadoc said a null was
         * what lets a handler pass an absent {@code @RequestParam} straight through, which conflated
         * the two: a {@code Boolean @RequestParam} sent as {@code ?unlinked=} binds null as well, so
         * a handler passing its parameter straight through drops the filter and answers with the
         * whole collection. That is item 45's own review finding, one parameter along, and it is why
         * {@code DirectoryLinkResource} rejects the blank before it gets here.
         *
         * <p>{@code is(null)} matches a <b>missing</b> field as well as an explicitly null one, and
         * that is the behaviour wanted rather than a tolerated approximation: nothing in this service
         * writes an explicit null, so an unset field is an absent one.
         * {@code DirectoryProjectionService.createAndClaim} relies on the same match to claim a link,
         * and says so at the query it builds.
         *
         * <p>It is here rather than at a call site because it has to compose with the other filters —
         * "this source, and no local record" is one query, and a handler that ran its own second query
         * would page and count the two independently.
         */
        public Builder isNull(String field, Boolean unset) {
            if (unset != null) {
                criteria.add(unset ? Criteria.where(field).is(null) : Criteria.where(field).ne(null));
            }
            return this;
        }

        /** Case-insensitive substring. Quoted, so a search for "a.b" is not read as a pattern. */
        public Builder contains(String field, String value) {
            if (value != null && !value.isBlank()) {
                criteria.add(Criteria.where(field).regex(java.util.regex.Pattern.quote(value.trim()), "i"));
            }
            return this;
        }

        public Query toQuery() {
            Query query = new Query();
            if (!criteria.isEmpty()) {
                query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
            }
            return query;
        }

        public boolean isEmpty() {
            return criteria.isEmpty();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Runs the query for one page, and counts the whole match.
     *
     * <p>The count is the point of most of these filters: the directory tiles and the desk's status
     * counters read {@code X-Total-Count} from a one-row request, so a count that ignores the filter
     * makes every tile show the collection total — which is exactly what they did.
     */
    public static <T> Page<T> page(MongoTemplate mongoTemplate, Class<T> type, Builder builder, Pageable pageable) {
        Query query = builder.toQuery();
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), type);
        List<T> content = mongoTemplate.find(query.with(pageable), type);
        return new PageImpl<>(content, pageable, total);
    }
}
