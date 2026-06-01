package net.jojoaddison.service;

import java.util.Optional;
import net.jojoaddison.domain.Person;
import net.jojoaddison.repository.PersonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.Person}.
 */
@Service
public class PersonService {

    private static final Logger LOG = LoggerFactory.getLogger(PersonService.class);

    private final PersonRepository personRepository;

    public PersonService(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    /**
     * Save a person.
     *
     * @param person the entity to save.
     * @return the persisted entity.
     */
    public Person save(Person person) {
        LOG.debug("Request to save Person : {}", person);
        return personRepository.save(person);
    }

    /**
     * Update a person.
     *
     * @param person the entity to save.
     * @return the persisted entity.
     */
    public Person update(Person person) {
        LOG.debug("Request to update Person : {}", person);
        return personRepository.save(person);
    }

    /**
     * Partially update a person.
     *
     * @param person the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<Person> partialUpdate(Person person) {
        LOG.debug("Request to partially update Person : {}", person);

        return personRepository
            .findById(person.getId())
            .map(existingPerson -> {
                if (person.getFirstName() != null) {
                    existingPerson.setFirstName(person.getFirstName());
                }
                if (person.getMiddleName() != null) {
                    existingPerson.setMiddleName(person.getMiddleName());
                }
                if (person.getLastName() != null) {
                    existingPerson.setLastName(person.getLastName());
                }
                if (person.getBirthDate() != null) {
                    existingPerson.setBirthDate(person.getBirthDate());
                }
                if (person.getGender() != null) {
                    existingPerson.setGender(person.getGender());
                }
                if (person.getMaritalStatus() != null) {
                    existingPerson.setMaritalStatus(person.getMaritalStatus());
                }
                if (person.getNationality() != null) {
                    existingPerson.setNationality(person.getNationality());
                }
                if (person.getLanguage() != null) {
                    existingPerson.setLanguage(person.getLanguage());
                }
                if (person.getCreatedDate() != null) {
                    existingPerson.setCreatedDate(person.getCreatedDate());
                }
                if (person.getModifiedDate() != null) {
                    existingPerson.setModifiedDate(person.getModifiedDate());
                }
                if (person.getCreatedBy() != null) {
                    existingPerson.setCreatedBy(person.getCreatedBy());
                }
                if (person.getModifiedBy() != null) {
                    existingPerson.setModifiedBy(person.getModifiedBy());
                }

                return existingPerson;
            })
            .map(personRepository::save);
    }

    /**
     * Get all the people.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    public Page<Person> findAll(Pageable pageable) {
        LOG.debug("Request to get all People");
        return personRepository.findAll(pageable);
    }

    /**
     * Get one person by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    public Optional<Person> findOne(String id) {
        LOG.debug("Request to get Person : {}", id);
        return personRepository.findById(id);
    }

    /**
     * Delete the person by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        LOG.debug("Request to delete Person : {}", id);
        personRepository.deleteById(id);
    }
}
