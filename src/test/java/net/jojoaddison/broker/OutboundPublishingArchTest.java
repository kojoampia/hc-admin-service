package net.jojoaddison.broker;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import net.jojoaddison.HcAdminServiceApp;

/**
 * {@link OutboundEventPublisher} is the only class that may hold a {@code StreamBridge}.
 *
 * <p>Backlog item 39a was three copies of one shape — {@code MessageService},
 * {@code ProfessionalVerificationService} and {@code HcAdminServiceKafkaResource} each published on
 * the request thread — and a fourth was deleted a fortnight earlier with {@code DutyRosterService},
 * which had been sending to {@code roster-events}, a binding declared in no configuration and read by
 * nothing. The shape reproduces because it reads perfectly well: inject the bridge, call {@code send},
 * catch and log. Nothing about it looks like a sixty-second wait.
 *
 * <p>So the guard is on the dependency rather than on any one method. A rule stated this way needs no
 * maintenance as entities and resources are generated — the same reason {@code PaginationIT} derives
 * its paths from the handler mapping instead of listing them. A list of known publishers would have to
 * be extended by hand, and a test whose coverage is extended by hand silently stops covering things.
 */
@AnalyzeClasses(packagesOf = HcAdminServiceApp.class, importOptions = DoNotIncludeTests.class)
class OutboundPublishingArchTest {

    // prettier-ignore
    @ArchTest
    static final ArchRule onlyTheOutboundEventPublisherTouchesTheBroker = noClasses()
        .that()
        .doNotHaveFullyQualifiedName(OutboundEventPublisher.class.getName())
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName("org.springframework.cloud.stream.function.StreamBridge")
        .because(
            "publishing on the request thread costs the caller up to sixty seconds against an unreachable broker "
            + "(backlog item 39a); route it through OutboundEventPublisher, which hands the send to a "
            + "single-threaded executor and never lets a broker delay a response"
        );
}
