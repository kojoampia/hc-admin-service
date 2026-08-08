package net.jojoaddison.repository;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.Team;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Team entity.
 */
@Repository
public interface TeamRepository extends MongoRepository<Team, String> {
    @Query("{}")
    Page<Team> findAllWithEagerRelationships(Pageable pageable);

    @Query("{}")
    List<Team> findAllWithEagerRelationships();

    @Query("{'id': ?0}")
    Optional<Team> findOneWithEagerRelationships(String id);

    /**
     * The hard geographic constraint in {@code DutyRosterService.autoScheduleShifts}.
     *
     * <p>Hand-written, and lost once already when the console model regenerated this interface. The
     * loss is quiet: the derived query needs {@code Team.geographicSpaceIds} to exist, so removing
     * the field breaks the context at startup rather than at compile time.
     */
    List<Team> findByGeographicSpaceIdsContaining(String geographicSpaceId);
}
