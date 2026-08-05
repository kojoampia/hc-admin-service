package net.jojoaddison.repository;

import net.jojoaddison.domain.Photo;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Photo entity.
 */
@Repository
public interface PhotoRepository extends MongoRepository<Photo, String> {}
