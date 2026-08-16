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

    /** Collects the criteria that were actually supplied. Nulls and blanks are simply absent. */
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
