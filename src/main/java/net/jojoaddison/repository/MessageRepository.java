package net.jojoaddison.repository;

import net.jojoaddison.domain.Message;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Message entity.
 */
@Repository
public interface MessageRepository extends MongoRepository<Message, String> {}
