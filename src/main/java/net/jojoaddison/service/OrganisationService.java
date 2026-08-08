package net.jojoaddison.service;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.Organisation;
import net.jojoaddison.repository.OrganisationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.Organisation}.
 */
@Service
public class OrganisationService {

    private final Logger log = LoggerFactory.getLogger(OrganisationService.class);

    private final OrganisationRepository organisationRepository;

    public OrganisationService(OrganisationRepository organisationRepository) {
        this.organisationRepository = organisationRepository;
    }

    /**
     * Save a organisation.
     *
     * @param organisation the entity to save.
     * @return the persisted entity.
     */
    public Organisation save(Organisation organisation) {
        log.debug("Request to save Organisation : {}", organisation);
        return organisationRepository.save(organisation);
    }

    /**
     * Update a organisation.
     *
     * @param organisation the entity to save.
     * @return the persisted entity.
     */
    public Organisation update(Organisation organisation) {
        log.debug("Request to update Organisation : {}", organisation);
        return organisationRepository.save(organisation);
    }

    /**
     * Partially update a organisation.
     *
     * @param organisation the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<Organisation> partialUpdate(Organisation organisation) {
        log.debug("Request to partially update Organisation : {}", organisation);

        return organisationRepository
            .findById(organisation.getId())
            .map(existingOrganisation -> {
                if (organisation.getName() != null) {
                    existingOrganisation.setName(organisation.getName());
                }
                if (organisation.getLegalName() != null) {
                    existingOrganisation.setLegalName(organisation.getLegalName());
                }
                if (organisation.getDescription() != null) {
                    existingOrganisation.setDescription(organisation.getDescription());
                }
                if (organisation.getRegistrationNumber() != null) {
                    existingOrganisation.setRegistrationNumber(organisation.getRegistrationNumber());
                }
                if (organisation.getTin() != null) {
                    existingOrganisation.setTin(organisation.getTin());
                }
                if (organisation.getFoundedOn() != null) {
                    existingOrganisation.setFoundedOn(organisation.getFoundedOn());
                }
                if (organisation.getSwitchboard() != null) {
                    existingOrganisation.setSwitchboard(organisation.getSwitchboard());
                }
                if (organisation.getEmail() != null) {
                    existingOrganisation.setEmail(organisation.getEmail());
                }
                if (organisation.getDeskHours() != null) {
                    existingOrganisation.setDeskHours(organisation.getDeskHours());
                }

                return existingOrganisation;
            })
            .map(organisationRepository::save);
    }

    /**
     * Get all the organisations.
     *
     * @return the list of entities.
     */
    public Page<Organisation> findAll(Pageable pageable) {
        log.debug("Request to get a page of Organisations");
        return organisationRepository.findAll(pageable);
    }

    /**
     * Get one organisation by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    public Optional<Organisation> findOne(String id) {
        log.debug("Request to get Organisation : {}", id);
        return organisationRepository.findById(id);
    }

    /**
     * Delete the organisation by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        log.debug("Request to delete Organisation : {}", id);
        organisationRepository.deleteById(id);
    }
}
