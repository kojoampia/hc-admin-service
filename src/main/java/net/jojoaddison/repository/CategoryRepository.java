package net.jojoaddison.repository;

import net.jojoaddison.domain.Category;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Category entity.
 */
@Repository
public interface CategoryRepository extends MongoRepository<Category, String> {}
