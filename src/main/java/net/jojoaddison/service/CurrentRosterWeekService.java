package net.jojoaddison.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import net.jojoaddison.domain.RosterWeek;
import net.jojoaddison.repository.RosterWeekRepository;
import org.springframework.stereotype.Service;

/**
 * Which roster week the console is talking about.
 *
 * <p>This exists so that there is exactly one answer. The dashboard hero and the duty-roster screen
 * both state figures for "the week", they sit one click apart, and until this was written they chose
 * their week by different rules — the dashboard by date arithmetic over {@code shift_date}, the
 * roster grid by taking the most recent {@code RosterWeek} it could find. Two rules that agree on
 * seeded data and diverge the moment anybody drafts a week ahead is the worst version of this: the
 * numbers look reconciled until they quietly are not.
 *
 * <p><b>The rule is: the latest week that has started.</b> A week drafted for next month must not
 * redefine what the dashboard means by "the week" — the figure people act on is the one covering
 * today. Where no week has started at all (a fresh deployment whose first roster is still ahead of
 * it) the earliest week is returned instead, because a grid has to render something and "the roster
 * you are about to work" beats an empty screen.
 *
 * <p>Empty means there are no roster weeks at all, which is production's normal state and not an
 * error. Callers render "no roster" rather than a zero.
 */
@Service
public class CurrentRosterWeekService {

    private final RosterWeekRepository rosterWeekRepository;
    private final Clock clock;

    public CurrentRosterWeekService(RosterWeekRepository rosterWeekRepository, Clock clock) {
        this.rosterWeekRepository = rosterWeekRepository;
        this.clock = clock;
    }

    /**
     * The roster week in force today.
     *
     * <p>The injected {@code Clock}, like {@code ShiftValuationService} — not {@code
     * LocalDate.now()}. Every date this service compares has to come from one source, or a week
     * boundary read one way and written another moves the answer by a day at the edges, which
     * presents as the dashboard and the grid disagreeing every Monday morning: the exact failure this
     * class exists to remove. It also makes the rule testable, which a rule about "today" otherwise
     * is not.
     *
     * @return the latest week already started, else the earliest week on file, else empty.
     */
    public Optional<RosterWeek> inForce() {
        LocalDate today = LocalDate.now(clock);
        return rosterWeekRepository
            .findFirstByStartDateLessThanEqualOrderByStartDateDesc(today)
            .or(rosterWeekRepository::findFirstByOrderByStartDateAsc);
    }
}
