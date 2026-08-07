package net.jojoaddison.repository;

import net.jojoaddison.domain.UserOption;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the UserOption entity.
 */
@Repository
public interface UserOptionRepository extends MongoRepository<UserOption, String> {}
