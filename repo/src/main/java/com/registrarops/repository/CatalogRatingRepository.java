package com.registrarops.repository;

import com.registrarops.entity.CatalogRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CatalogRatingRepository extends JpaRepository<CatalogRating, Long> {

    Optional<CatalogRating> findByUserIdAndItemTypeAndItemId(Long userId, String itemType, Long itemId);

    @Query("SELECT AVG(r.score) FROM CatalogRating r WHERE r.itemType = :itemType AND r.itemId = :itemId")
    Double averageScore(@Param("itemType") String itemType, @Param("itemId") Long itemId);
}
