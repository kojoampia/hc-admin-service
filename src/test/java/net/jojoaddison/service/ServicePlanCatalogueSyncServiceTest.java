package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.ServicePlan;
import net.jojoaddison.repository.ServicePlanRepository;
import net.jojoaddison.service.AbofonsaContentClient.PublishedPlan;
import net.jojoaddison.service.dto.PlanCatalogueSyncDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The catalogue sync, over a mocked publisher.
 *
 * <p>Mocked rather than booted, because the only alternative is reaching {@code web.abofonsa.com}
 * from a test — which makes the result depend on a third party's deploy and, worse, turns a machine
 * with no network into a green run down the very path the sync treats as normal. The client is
 * disabled in {@code src/test/resources/config/application.yml} for the same reason, and the note
 * there says so.
 *
 * <p>What this cannot answer is whether the real catalogue can be read and whether it still has the
 * shape {@code AbofonsaContentClient} parses. That is a question only a running host can answer and
 * belongs to the quality stack.
 */
class ServicePlanCatalogueSyncServiceTest {

    private static final PublishedPlan PEAR = new PublishedPlan(
        "PEAR",
        "PEAR Plan",
        "A dependable weekday routine.",
        "3,000",
        "GHS",
        false,
        1
    );
    private static final PublishedPlan PAWPAW = new PublishedPlan(
        "PAWPAW",
        "PAWPAW Plan",
        "Daily clinical oversight.",
        "5,000",
        "GHS",
        true,
        2
    );

    private final AbofonsaContentClient client = mock(AbofonsaContentClient.class);
    private final ServicePlanRepository repository = mock(ServicePlanRepository.class);
    private final ServicePlanCatalogueSyncService service = new ServicePlanCatalogueSyncService(client, repository);

    /**
     * The whole "Abofonsa is down is a normal state" claim, asserted as <em>nothing was written</em>.
     *
     * <p>Not merely "it did not throw". A sync that swallowed the failure and then reconciled against
     * an empty published list would delete or blank the local catalogue, which is the failure this
     * design is built to make impossible — so the assertion that matters is that the repository was
     * never touched at all.
     */
    @Test
    void writesNothingWhenAbofonsaCannotBeRead() {
        when(client.isEnabled()).thenReturn(true);
        when(client.plans()).thenReturn(Optional.empty());

        PlanCatalogueSyncDTO result = service.sync();

        assertThat(result.reached()).isFalse();
        // Configured and unreachable: Abofonsa was dialled and did not answer. The integration test
        // covers the other half — not dialled at all — and the two must not read the same.
        assertThat(result.configured()).isTrue();
        assertThat(result.published()).isZero();
        verify(repository, never()).save(any());
        verify(repository, never()).findAll();
    }

    /**
     * A deployment that is not set up to read Abofonsa has not learned that Abofonsa is down.
     *
     * <p>Backlog item 24 is this repo's own record of collapsing those two: the console reported the
     * roster service as unreachable whenever a local switch was off, and an administrator went and
     * looked at the wrong machine. Same shape, one field along.
     */
    @Test
    void distinguishesNotConfiguredFromUnreachable() {
        when(client.isEnabled()).thenReturn(false);
        when(client.plans()).thenReturn(Optional.empty());

        PlanCatalogueSyncDTO result = service.sync();

        assertThat(result.reached()).isFalse();
        assertThat(result.configured()).isFalse();
    }

    /** A tier this service has never held becomes a plan, unpriced, with the publisher's currency. */
    @Test
    void createsAPlanItHasNeverHeld() {
        when(client.plans()).thenReturn(Optional.of(List.of(PEAR)));
        when(repository.findOneByCode("PEAR")).thenReturn(Optional.empty());
        when(repository.findAll()).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PlanCatalogueSyncDTO result = service.sync();

        ArgumentCaptor<ServicePlan> saved = ArgumentCaptor.forClass(ServicePlan.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getCode()).isEqualTo("PEAR");
        assertThat(saved.getValue().getName()).isEqualTo("PEAR Plan");
        assertThat(saved.getValue().getDisplayOrder()).isEqualTo(1);
        assertThat(saved.getValue().getCurrency()).isEqualTo("GHS");
        // The point of the whole design: a published price is a display string and never becomes a
        // number here. A plan arrives unpriced and an administrator prices it.
        assertThat(saved.getValue().getMonthlyPrice()).isNull();
        assertThat(result.created()).isEqualTo(1);
    }

    /**
     * A plan already held is renamed and re-ordered, and its price is left exactly alone.
     *
     * <p>This is the assertion that would fail if somebody later "completed" the sync by parsing
     * {@code priceAmount} — the published figure is 5,000 and the local one is deliberately not.
     */
    @Test
    void movesNameAndOrderButNeverThePrice() {
        ServicePlan stored = new ServicePlan().code("PAWPAW").name("Bridge Plus").displayOrder(9).monthlyPrice(new BigDecimal("680"));
        when(client.plans()).thenReturn(Optional.of(List.of(PAWPAW)));
        when(repository.findOneByCode("PAWPAW")).thenReturn(Optional.of(stored));
        when(repository.findAll()).thenReturn(List.of(stored));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PlanCatalogueSyncDTO result = service.sync();

        assertThat(stored.getName()).isEqualTo("PAWPAW Plan");
        assertThat(stored.getDisplayOrder()).isEqualTo(2);
        assertThat(stored.getMonthlyPrice()).isEqualByComparingTo("680");
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.unpricedCodes()).isEmpty();
    }

    /**
     * A run that changes nothing writes nothing.
     *
     * <p>{@code AuditLogCallback} writes an {@code AuditLog} row for every save across every
     * collection, so a sync that re-saved every plan on every pass would file audit rows saying
     * nothing happened, four times a day, for ever.
     */
    @Test
    void savesNothingWhenTheCatalogueAlreadyAgrees() {
        ServicePlan stored = new ServicePlan().code("PEAR").name("PEAR Plan").displayOrder(1).monthlyPrice(new BigDecimal("3000"));
        when(client.plans()).thenReturn(Optional.of(List.of(PEAR)));
        when(repository.findOneByCode("PEAR")).thenReturn(Optional.of(stored));
        when(repository.findAll()).thenReturn(List.of(stored));

        PlanCatalogueSyncDTO result = service.sync();

        verify(repository, never()).save(any());
        assertThat(result.unchanged()).isEqualTo(1);
    }

    /**
     * A plan Abofonsa no longer publishes is reported and kept.
     *
     * <p>{@code Patient.plan} is a {@code @DBRef}, so deleting it would dangle every patient holding
     * it at whatever hour the scheduler ran. Withdrawing a plan is an administrator's decision made
     * against the directory, never a consequence of a third party editing a page.
     */
    @Test
    void keepsAPlanThatIsNoLongerPublished() {
        ServicePlan retired = new ServicePlan().code("BRIDGE_FAMILY").name("Bridge Family").monthlyPrice(new BigDecimal("1240"));
        ServicePlan pear = new ServicePlan().code("PEAR").name("PEAR Plan").displayOrder(1);
        when(client.plans()).thenReturn(Optional.of(List.of(PEAR)));
        when(repository.findOneByCode("PEAR")).thenReturn(Optional.of(pear));
        when(repository.findAll()).thenReturn(List.of(retired, pear));

        PlanCatalogueSyncDTO result = service.sync();

        verify(repository, never()).delete(any());
        assertThat(result.unpublishedCodes()).containsExactly("BRIDGE_FAMILY");
        // pear has no monthlyPrice in this fixture, which is what a just-created plan looks like.
        assertThat(result.unpricedCodes()).containsExactly("PEAR");
    }

    /**
     * A tier this service cannot store is refused, and the tiers after it are still reconciled.
     *
     * <p>{@code ServicePlan.name} is {@code @Size(max = 60)} and a save is validated by
     * {@code ValidatingMongoEventListener}, so an over-long name thrown rather than refused would
     * abort the run at whatever position the bad tier happened to occupy — leaving the reconciliation
     * half done and the reason a stack trace rather than a count.
     */
    @Test
    void refusesAPublishedTierItCannotStoreWithoutAbandoningTheRest() {
        PublishedPlan tooLong = new PublishedPlan("LONG", "N".repeat(61), null, "1", "GHS", false, 4);
        when(client.plans()).thenReturn(Optional.of(List.of(tooLong, PEAR)));
        when(repository.findOneByCode("PEAR")).thenReturn(Optional.empty());
        when(repository.findAll()).thenReturn(new ArrayList<>());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PlanCatalogueSyncDTO result = service.sync();

        verify(repository, never()).findOneByCode("LONG");
        assertThat(result.refusedCodes()).containsExactly("LONG");
        assertThat(result.created()).isEqualTo(1);
    }
}
