package com.registrarops.service;

import com.registrarops.entity.CatalogRating;
import com.registrarops.entity.Course;
import com.registrarops.entity.CourseMaterial;
import com.registrarops.entity.SearchTerm;
import com.registrarops.repository.CatalogRatingRepository;
import com.registrarops.repository.CourseMaterialRepository;
import com.registrarops.repository.CourseRepository;
import com.registrarops.repository.SearchTermRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Read-side facade for the catalog page (browse, filter, sort, rate).
 *
 * Search uses {@link CourseRepository#filter(String, BigDecimal, BigDecimal, java.time.LocalDateTime)}
 * which builds a single JPQL query with optional WHERE clauses (no string
 * concatenation, no native SQL — keeps the auditor happy and avoids injection).
 */
@Service
public class CatalogService {

    public enum Sort { RATING, PRICE_ASC, PRICE_DESC, NEW_ARRIVALS, BESTSELLERS }

    private static final int NEW_ARRIVAL_DAYS = 14;

    private final CourseRepository courseRepository;
    private final CourseMaterialRepository materialRepository;
    private final CatalogRatingRepository ratingRepository;
    private final SearchTermRepository searchTermRepository;
    private final SearchService searchService;
    private final AuditService auditService;

    public CatalogService(CourseRepository courseRepository,
                          CourseMaterialRepository materialRepository,
                          CatalogRatingRepository ratingRepository,
                          SearchTermRepository searchTermRepository,
                          SearchService searchService,
                          AuditService auditService) {
        this.courseRepository = courseRepository;
        this.materialRepository = materialRepository;
        this.ratingRepository = ratingRepository;
        this.searchTermRepository = searchTermRepository;
        this.searchService = searchService;
        this.auditService = auditService;
    }

    public List<Course> search(String query,
                               String category,
                               BigDecimal minPrice,
                               BigDecimal maxPrice,
                               boolean newArrivalsOnly,
                               Sort sort) {
        return search(query, category, minPrice, maxPrice, newArrivalsOnly, null, null, null, sort);
    }

    /**
     * Full filter form including the prompt-required tag/author/rating dimensions.
     * The repository query still handles the common fast-path filters; the
     * tag/author/rating dimensions are applied in-memory on the already-narrowed
     * result set (the dataset is small and rendered server-side, so this is safe
     * and avoids invasive changes to the JPQL filter query).
     */
    public List<Course> search(String query,
                               String category,
                               BigDecimal minPrice,
                               BigDecimal maxPrice,
                               boolean newArrivalsOnly,
                               String tag,
                               String author,
                               BigDecimal minRating,
                               Sort sort) {
        LocalDateTime newSince = newArrivalsOnly
                ? LocalDateTime.now().minusDays(NEW_ARRIVAL_DAYS)
                : null;

        List<Course> base;
        if (query != null && !query.isBlank()) {
            String norm = searchService.normalize(query);
            base = courseRepository.searchByTerm(norm);
            base = base.stream()
                    .filter(c -> category == null || category.isBlank() || category.equalsIgnoreCase(c.getCategory()))
                    .filter(c -> minPrice == null || c.getPrice().compareTo(minPrice) >= 0)
                    .filter(c -> maxPrice == null || c.getPrice().compareTo(maxPrice) <= 0)
                    .filter(c -> newSince == null || c.getCreatedAt().isAfter(newSince))
                    .toList();
        } else {
            base = courseRepository.filter(blankToNull(category), minPrice, maxPrice, newSince);
        }

        // Additional prompt-defined filter dimensions (tag/author/rating).
        String tagNorm = blankToNull(tag);
        String authorNorm = blankToNull(author);
        List<Course> filtered = base.stream()
                .filter(c -> tagNorm == null || (c.getTags() != null
                        && c.getTags().toLowerCase().contains(tagNorm.toLowerCase())))
                .filter(c -> authorNorm == null || (c.getAuthorName() != null
                        && c.getAuthorName().toLowerCase().contains(authorNorm.toLowerCase())))
                .filter(c -> minRating == null || (c.getRatingAvg() != null
                        && c.getRatingAvg().compareTo(minRating) >= 0))
                .toList();

        return applySort(filtered, sort);
    }

    public Optional<Course> getCourse(Long id) {
        return courseRepository.findById(id);
    }

    public List<CourseMaterial> getMaterialsForCourse(Long courseId) {
        return materialRepository.findByCourseId(courseId);
    }

    public List<SearchTerm> getTrending() {
        return searchTermRepository.findTop10ByOrderBySearchCountDesc();
    }

    @Transactional
    public CatalogRating rate(Long userId, String username, String itemType, Long itemId, int score) {
        if (score < 1 || score > 5) {
            throw new IllegalArgumentException("Rating must be 1..5");
        }
        CatalogRating rating = ratingRepository
                .findByUserIdAndItemTypeAndItemId(userId, itemType, itemId)
                .orElseGet(CatalogRating::new);
        rating.setUserId(userId);
        rating.setItemType(itemType);
        rating.setItemId(itemId);
        rating.setScore(score);
        if (rating.getCreatedAt() == null) rating.setCreatedAt(LocalDateTime.now());
        CatalogRating saved = ratingRepository.save(rating);

        // Recompute the item's average so the catalog cards stay accurate.
        if ("course".equalsIgnoreCase(itemType)) {
            Double avg = ratingRepository.averageScore(itemType, itemId);
            courseRepository.findById(itemId).ifPresent(c -> {
                c.setRatingAvg(avg == null ? BigDecimal.ZERO
                        : BigDecimal.valueOf(avg).setScale(2, java.math.RoundingMode.HALF_UP));
                courseRepository.save(c);
            });
        }
        auditService.log(userId, username, "RATE_ITEM", itemType, itemId, null,
                "{\"score\":" + score + "}", null);
        return saved;
    }

    private List<Course> applySort(List<Course> in, Sort sort) {
        if (sort == null) return in;
        Comparator<Course> cmp = switch (sort) {
            case RATING       -> Comparator.comparing(Course::getRatingAvg).reversed();
            case PRICE_ASC    -> Comparator.comparing(Course::getPrice);
            case PRICE_DESC   -> Comparator.comparing(Course::getPrice).reversed();
            case NEW_ARRIVALS -> Comparator.comparing(Course::getCreatedAt).reversed();
            case BESTSELLERS  -> Comparator.comparing(Course::getEnrollCount).reversed();
        };
        return in.stream().sorted(cmp).toList();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
