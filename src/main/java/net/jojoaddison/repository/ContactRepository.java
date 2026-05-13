package net.jojoaddison.repository;

import net.jojoaddison.domain.Contact;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Contact entity.
 */
@Repository
public interface ContactRepository extends MongoRepository<Contact, String> {}
