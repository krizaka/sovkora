package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository;

import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.CatalogModelEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** JPA Repository for accessing orazaka_models catalog table. */
@Repository
public interface CatalogModelRepository extends JpaRepository<CatalogModelEntity, Integer> {

  /**
   * Finds all models belonging to a specific category.
   *
   * @param category The category (e.g. speech, image, video, vision).
   * @return A list of CatalogModelEntity.
   */
  List<CatalogModelEntity> findByCategory(String category);

  /**
   * Finds a model by its unique name.
   *
   * @param modelName The model's unique identifier name.
   * @return Optional containing the CatalogModelEntity if found.
   */
  Optional<CatalogModelEntity> findByModelName(String modelName);

  /**
   * Finds the default model belonging to a specific category.
   *
   * @param category The category (e.g. speech, image, video, vision).
   * @return Optional containing the default CatalogModelEntity if found.
   */
  Optional<CatalogModelEntity> findByCategoryAndIsDefaultTrue(String category);

  /**
   * The default voice a category declares for one locale (ADR-076).
   *
   * @param category the category ({@code speech})
   * @param locale the declared locale, exactly
   * @return the locale's default voice
   */
  Optional<CatalogModelEntity> findByCategoryAndLocaleAndLocaleDefaultTrue(
      String category, String locale);

  /**
   * Every locale default a category declares, for the same-language fallback (ADR-076).
   *
   * @param category the category ({@code speech})
   * @return the defaults, in locale order so the fallback is deterministic
   */
  List<CatalogModelEntity> findByCategoryAndLocaleDefaultTrueOrderByLocaleAsc(String category);

  /**
   * One model of a category by name.
   *
   * @param category the category
   * @param modelName the model's name
   * @return the model
   */
  Optional<CatalogModelEntity> findByCategoryAndModelName(String category, String modelName);
}
