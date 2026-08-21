package net.jojoaddison.repository;

import java.time.LocalDate;
import java.util.Optional;
import net.jojoaddison.domain.RosterWeek;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the RosterWeek entity.
 */
@Repository
public interface RosterWeekRepository extends MongoRepository<RosterWeek, String> {
    /**
     * The most recent week that has already started. See {@link
     * net.jojoaddison.service.CurrentRosterWeekService} for why this and not simply the latest week.
     */
    Optional<RosterWeek> findFirstByStartDateLessThanEqualOrderByStartDateDesc(LocalDate date);

    /** The earliest week on file — the fallback when no week has started yet. */
    Optional<RosterWeek> findFirstByOrderByStartDateAsc();
}
