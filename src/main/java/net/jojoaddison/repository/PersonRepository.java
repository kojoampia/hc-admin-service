package net.jojoaddison.repository;

import net.jojoaddison.domain.Person;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Person entity.
 */
@Repository
public interface PersonRepository extends MongoRepository<Person, String> {}
