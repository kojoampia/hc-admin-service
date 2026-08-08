package net.jojoaddison.service;

import java.util.Optional;
import net.jojoaddison.domain.Team;
import net.jojoaddison.repository.TeamRepository;
import net.jojoaddison.service.dto.TeamDTO;
import net.jojoaddison.service.mapper.TeamMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.Team}.
 */
@Service
public class TeamService {

    private static final Logger LOG = LoggerFactory.getLogger(TeamService.class);

    private final TeamRepository teamRepository;

    private final TeamMapper teamMapper;

    public TeamService(TeamRepository teamRepository, TeamMapper teamMapper) {
        this.teamRepository = teamRepository;
        this.teamMapper = teamMapper;
    }

    /**
     * Save a team.
     *
     * @param teamDTO the entity to save.
     * @return the persisted entity.
     */
    public TeamDTO save(TeamDTO teamDTO) {
        LOG.debug("Request to save Team : {}", teamDTO);
        Team team = teamMapper.toEntity(teamDTO);
        team = teamRepository.save(team);
        return teamMapper.toDto(team);
    }

    /**
     * Update a team.
     *
     * @param teamDTO the entity to save.
     * @return the persisted entity.
     */
    public TeamDTO update(TeamDTO teamDTO) {
        LOG.debug("Request to update Team : {}", teamDTO);
        Team team = teamMapper.toEntity(teamDTO);
        restoreGeographicSpaceIds(team);
        team = teamRepository.save(team);
        return teamMapper.toDto(team);
    }

    /**
     * Puts the stored {@code geographicSpaceIds} back when the incoming payload has none.
     *
     * <p>PUT sends a whole document, so a client that does not know about the field erases it — and
     * the console client, generated from a JDL that does not declare it, is exactly such a client.
     * The field is the auto-scheduler's only geography ({@code DutyRosterService}), and it fails
     * silently: shifts stop matching any covering team and are skipped rather than erroring.
     *
     * <p>Absent means absent. A caller that deliberately clears the coverage sends an empty list,
     * which is preserved.
     */
    private void restoreGeographicSpaceIds(Team team) {
        if (team.getGeographicSpaceIds() != null || team.getId() == null) {
            return;
        }
        teamRepository.findById(team.getId()).map(Team::getGeographicSpaceIds).ifPresent(team::setGeographicSpaceIds);
    }

    /**
     * Partially update a team.
     *
     * @param teamDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<TeamDTO> partialUpdate(TeamDTO teamDTO) {
        LOG.debug("Request to partially update Team : {}", teamDTO);

        return teamRepository
            .findById(teamDTO.getId())
            .map(existingTeam -> {
                teamMapper.partialUpdate(existingTeam, teamDTO);

                return existingTeam;
            })
            .map(teamRepository::save)
            .map(teamMapper::toDto);
    }

    /**
     * Get all the teams.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    public Page<TeamDTO> findAll(Pageable pageable) {
        LOG.debug("Request to get all Teams");
        return teamRepository.findAll(pageable).map(teamMapper::toDto);
    }

    /**
     * Get all the teams with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<TeamDTO> findAllWithEagerRelationships(Pageable pageable) {
        return teamRepository.findAllWithEagerRelationships(pageable).map(teamMapper::toDto);
    }

    /**
     * Get one team by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    public Optional<TeamDTO> findOne(String id) {
        LOG.debug("Request to get Team : {}", id);
        return teamRepository.findOneWithEagerRelationships(id).map(teamMapper::toDto);
    }

    /**
     * Delete the team by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        LOG.debug("Request to delete Team : {}", id);
        teamRepository.deleteById(id);
    }
}
