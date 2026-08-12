package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

/**
 * PromQL survives the trip into a URI.
 *
 * <p>It did not, and the way it failed is worth pinning. Spring's {@code uriBuilder} treats
 * <code>{...}</code> as a URI template variable, and PromQL label selectors are made of braces — so
 * {@code count(up{job="mongodb"})} had {@code {job="mongodb"}} read as a placeholder and never
 * reached Mimir.
 *
 * <p>What made it expensive was how selectively it broke. The one query with no braces,
 * {@code sum(kafka_consumer_connection_count)}, worked perfectly. So in production two capabilities
 * reported Live and the two using label selectors reported Unknown — which reads like a Mimir
 * problem, or a permissions problem, or anything except an encoding one. A brace-free smoke test
 * would have passed.
 *
 * <p>These assertions therefore use the real queries the service sends, braces and all.
 */
class ObservabilityClientUriTest {

    private static final String BASE = "http://mimir:9009";

    @Test
    void bracesAreEncodedRatherThanTreatedAsTemplateVariables() {
        URI uri = ObservabilityClient.queryUri(BASE, "count(up{job=\"mongodb\", database=~\"hc-.*\"})");

        assertThat(uri.toString()).doesNotContain("{").doesNotContain("}");
        assertThat(uri.toString()).contains("%7B").contains("%7D");
    }

    /** The query that already worked must keep working — it is the one with no braces to mangle. */
    @Test
    void aQueryWithoutBracesIsUnharmed() {
        URI uri = ObservabilityClient.queryUri(BASE, "sum(kafka_consumer_connection_count)");

        assertThat(uri.toString()).isEqualTo(BASE + "/prometheus/api/v1/query?query=sum%28kafka_consumer_connection_count%29");
    }

    /**
     * The uptime query is the worst case: braces, brackets, a subquery, quotes and arithmetic.
     */
    @Test
    void theUptimeQuerySurvivesIntact() {
        String promql =
            "avg_over_time((count(count by (job) (jvm_memory_used_bytes{job=~\"hc-(admin|patient)-(gateway|service)\"})) " +
            "or vector(0))[7d:5m]) / 6 * 100";

        URI uri = ObservabilityClient.queryUri(BASE, promql);

        // Nothing left that a URI template or a query-string parser could misread.
        assertThat(uri.toString()).doesNotContain("{").doesNotContain("}").doesNotContain("[").doesNotContain("]");
        assertThat(uri.toString()).doesNotContain("\"").doesNotContain(" ");
        // And it is still one query parameter, not several: a bare & or = would split it.
        assertThat(uri.getQuery()).startsWith("query=");
        assertThat(uri.toString().indexOf("&")).isEqualTo(-1);
    }

    @Test
    void theBaseUrlIsPreserved() {
        assertThat(ObservabilityClient.queryUri(BASE, "up").toString()).startsWith("http://mimir:9009/prometheus/api/v1/query?");
    }
}
