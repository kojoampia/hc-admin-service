package net.jojoaddison.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.service.dto.ProfileDTO;
import net.jojoaddison.service.mapper.ProfileMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.Profile}.
 */
@Service
public class ProfileService {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileService.class);

    private final ProfileRepository profileRepository;

    private final ProfileMapper profileMapper;

    public ProfileService(ProfileRepository profileRepository, ProfileMapper profileMapper) {
        this.profileRepository = profileRepository;
        this.profileMapper = profileMapper;
    }

    /**
     * Save a profile.
     *
     * @param profileDTO the entity to save.
     * @return the persisted entity.
     */
    public ProfileDTO save(ProfileDTO profileDTO) {
        LOG.debug("Request to save Profile : {}", profileDTO);
        Profile profile = profileMapper.toEntity(profileDTO);
        profile = profileRepository.save(profile);
        return profileMapper.toDto(profile);
    }

    /**
     * Update a profile.
     *
     * @param profileDTO the entity to save.
     * @return the persisted entity.
     */
    public ProfileDTO update(ProfileDTO profileDTO) {
        LOG.debug("Request to update Profile : {}", profileDTO);
        Profile profile = profileMapper.toEntity(profileDTO);
        profile = profileRepository.save(profile);
        return profileMapper.toDto(profile);
    }

    /**
     * Partially update a profile.
     *
     * @param profileDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<ProfileDTO> partialUpdate(ProfileDTO profileDTO) {
        LOG.debug("Request to partially update Profile : {}", profileDTO);

        return profileRepository
            .findById(profileDTO.getId())
            .map(existingProfile -> {
                profileMapper.partialUpdate(existingProfile, profileDTO);

                return existingProfile;
            })
            .map(profileRepository::save)
            .map(profileMapper::toDto);
    }

    /**
     * Get all the profiles.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    public Page<ProfileDTO> findAll(Pageable pageable) {
        LOG.debug("Request to get all Profiles");
        return profileRepository.findAll(pageable).map(profileMapper::toDto);
    }

    /**
     * Get all the profiles by organisation id and team id.
     *
     * @param organisationId the organisation id of the entity.
     * @param teamId the team id of the entity.
     * @return the list of entities.
     */
    public List<ProfileDTO> findByOrganisationIdAndTeamId(String organisationId, String teamId) {
        LOG.debug("Request to get Profiles by organisationId : {} and teamId : {}", organisationId, teamId);
        return profileRepository
            .findByOrganisationIdAndTeamId(organisationId, teamId)
            .stream()
            .map(profileMapper::toDto)
            .collect(Collectors.toList());
    }

    /**
     * Get all the profiles by organisation id and status.
     *
     * @param organisationId the organisation id of the entity.
     * @param status the status of the entity.
     * @return the list of entities.
     */
    public List<ProfileDTO> findByOrganisationIdAndStatus(String organisationId, Boolean status) {
        LOG.debug("Request to get Profiles by organisationId : {} and status : {}", organisationId, status);
        return profileRepository
            .findByOrganisationIdAndStatus(organisationId, status)
            .stream()
            .map(profileMapper::toDto)
            .collect(Collectors.toList());
    }

    /**
     * Get all the profiles by team id and status.
     *
     * @param teamId the team id of the entity.
     * @param status the status of the entity.
     * @return the list of entities.
     */
    public List<ProfileDTO> findByTeamIdAndStatus(String teamId, Boolean status) {
        LOG.debug("Request to get Profiles by teamId : {} and status : {}", teamId, status);
        return profileRepository.findByTeamIdAndStatus(teamId, status).stream().map(profileMapper::toDto).collect(Collectors.toList());
    }

    /**
     * Get all the profiles by roles and status.
     *
     * @param roles the roles of the entity.
     * @param status the status of the entity.
     * @return the list of entities.
     */
    public List<ProfileDTO> findByRolesAndStatus(String roles, Boolean status) {
        LOG.debug("Request to get Profiles by roles : {} and status : {}", roles, status);
        return profileRepository.findByRolesAndStatus(roles, status).stream().map(profileMapper::toDto).collect(Collectors.toList());
    }

    /**
     * Get all the profiles by roles.
     *
     * @param roles the roles of the entity.
     * @return the list of entities.
     */
    public List<ProfileDTO> findByRoles(String roles) {
        LOG.debug("Request to get Profiles by roles : {}", roles);
        return profileRepository.findByRoles(roles).stream().map(profileMapper::toDto).collect(Collectors.toList());
    }

    /**
     * Get all the profiles by status.
     *
     * @param status the status of the entity.
     * @return the list of entities.
     */
    public List<ProfileDTO> findByStatus(Boolean status) {
        LOG.debug("Request to get Profiles by status : {}", status);
        return profileRepository.findByStatus(status).stream().map(profileMapper::toDto).collect(Collectors.toList());
    }

    /**
     * Get all the profiles by createdBy.
     *
     * @param createdBy the createdBy of the entity.
     * @return the list of entities.
     */
    public List<ProfileDTO> findByCreatedBy(String createdBy) {
        LOG.debug("Request to get Profiles by createdBy : {}", createdBy);
        return profileRepository.findByCreatedBy(createdBy).stream().map(profileMapper::toDto).collect(Collectors.toList());
    }

    /**
     * Get all the profiles by modifiedBy.
     *
     * @param modifiedBy the modifiedBy of the entity.
     * @return the list of entities.
     */
    public List<ProfileDTO> findByModifiedBy(String modifiedBy) {
        LOG.debug("Request to get Profiles by modifiedBy : {}", modifiedBy);
        return profileRepository.findByModifiedBy(modifiedBy).stream().map(profileMapper::toDto).collect(Collectors.toList());
    }

    /**
     * Get all the profiles by createdDate.
     *
     * @param createdDate the createdDate of the entity.
     * @return the list of entities.
     */
    public List<ProfileDTO> findByCreatedDate(String createdDate) {
        LOG.debug("Request to get Profiles by createdDate : {}", createdDate);
        return profileRepository.findByCreatedDate(createdDate).stream().map(profileMapper::toDto).collect(Collectors.toList());
    }

    /**
     * Get all the profiles by modifiedDate.
     *
     * @param modifiedDate the modifiedDate of the entity.
     * @return the list of entities.
     */
    public List<ProfileDTO> findByModifiedDate(String modifiedDate) {
        LOG.debug("Request to get Profiles by modifiedDate : {}", modifiedDate);
        return profileRepository.findByModifiedDate(modifiedDate).stream().map(profileMapper::toDto).collect(Collectors.toList());
    }

    /**
     * Get one profile by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    public Optional<ProfileDTO> findOne(String id) {
        LOG.debug("Request to get Profile : {}", id);
        return profileRepository.findById(id).map(profileMapper::toDto);
    }

    /**
     * Get one profile by personId.
     *
     * @param personId the personId of the entity.
     * @return the entity.
     */
    public Optional<ProfileDTO> findByPersonId(String personId) {
        LOG.debug("Request to get Profile by personId : {}", personId);
        return profileRepository.findByPersonId(personId).map(profileMapper::toDto);
    }

    /**
     * Get one profile by contactId.
     *
     * @param contactId the contactId of the entity.
     * @return the entity.
     */
    public Optional<ProfileDTO> findByContactId(String contactId) {
        LOG.debug("Request to get Profile by contactId : {}", contactId);
        return profileRepository.findByContactId(contactId).map(profileMapper::toDto);
    }

    /**
     * Delete the profile by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        LOG.debug("Request to delete Profile : {}", id);
        profileRepository.deleteById(id);
    }
}
