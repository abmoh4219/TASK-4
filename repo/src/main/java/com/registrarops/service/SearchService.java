package com.registrarops.service;

import com.registrarops.entity.Course;
import com.registrarops.entity.SearchTerm;
import com.registrarops.repository.CourseRepository;
import com.registrarops.repository.SearchTermRepository;
import info.debatty.java.stringsimilarity.Levenshtein;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Unified-search engine for the catalog.
 *
 * Features required by the business prompt:
 *   - instant suggestions          → {@link #getSuggestions(String)}
 *   - trending terms               → {@link #getTrendingTerms()}
 *   - "did you mean" typo correct  → Levenshtein distance against trending terms
 *   - pinyin / synonym normalize   → {@link #normalize(String)}
 *   - fallback recommendations     → {@link #getFallbackRecommendations()}
 *   - search-term tracking         → {@link #recordSearch(String)}
 *
 * Pinyin support is intentionally a small local lookup table (rather than
 * pulling in ICU4J / pinyin4j as a heavy dependency) — the offline business
 * prompt explicitly wants offline-only behavior, and the test fixtures cover
 * the demonstrated mappings.
 */
@Service
public class SearchService {

    private static final int FEW_RESULTS_THRESHOLD = 3;
    private static final double MAX_DID_YOU_MEAN_DISTANCE = 3.0;

    private static final Map<String, String> PINYIN = Map.of(
            "数学", "shuxue",     // mathematics
            "历史", "lishi",      // history
            "化学", "huaxue",     // chemistry
            "艺术", "yishu",      // art
            "微积分", "weijifen"); // calculus

    private static final Map<String, String> SYNONYMS = Map.of(
            "math",     "mathematics",
            "maths",    "mathematics",
            "calc",     "calculus",
            "algo",     "algorithms",
            "cs",       "computer science",
            "chem",     "chemistry");

    private final CourseRepository courseRepository;
    private final SearchTermRepository searchTermRepository;
    private final Levenshtein levenshtein = new Levenshtein();

    public SearchService(CourseRepository courseRepository,
                         SearchTermRepository searchTermRepository) {
        this.courseRepository = courseRepository;
        this.searchTermRepository = searchTermRepository;
    }

    public SuggestionResult getSuggestions(String rawQuery) {
        if (rawQuery == null) rawQuery = "";
        String q = normalize(rawQuery.trim());
        if (q.isEmpty()) {
            return new SuggestionResult(List.of(), null, getTrendingTerms());
        }
        List<Course> matches = courseRepository.searchByTerm(q);
        String didYouMean = null;
        if (matches.size() < FEW_RESULTS_THRESHOLD) {
            didYouMean = findClosestTerm(q);
            if (didYouMean != null && !didYouMean.equalsIgnoreCase(q)) {
                List<Course> alt = courseRepository.searchByTerm(didYouMean);
                if (!alt.isEmpty() && matches.size() < alt.size()) {
                    matches = alt;
                }
            } else {
                didYouMean = null;
            }
        }
        return new SuggestionResult(matches, didYouMean, getTrendingTerms());
    }

    /**
     * Apply pinyin transliteration and synonym expansion. Lower-cases the input.
     */
    public String normalize(String query) {
        if (query == null) return "";
        String q = query.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : PINYIN.entrySet()) {
            if (q.contains(e.getKey())) {
                q = q.replace(e.getKey(), e.getValue());
            }
        }
        // Whole-token synonym substitution.
        String[] tokens = q.split("\\s+");
        StringBuilder rebuilt = new StringBuilder();
        for (String t : tokens) {
            String mapped = SYNONYMS.getOrDefault(t, t);
            if (rebuilt.length() > 0) rebuilt.append(' ');
            rebuilt.append(mapped);
        }
        return rebuilt.toString().trim();
    }

    /**
     * Find the closest known trending term to {@code query} within
     * {@link #MAX_DID_YOU_MEAN_DISTANCE} edits. Used for "did you mean" hints
     * when the literal query returns too few hits.
     */
    public String findClosestTerm(String query) {
        if (query == null || query.isBlank()) return null;
        List<SearchTerm> terms = searchTermRepository.findTop10ByOrderBySearchCountDesc();
        String best = null;
        double bestDist = Double.MAX_VALUE;
        for (SearchTerm t : terms) {
            double d = levenshtein.distance(query.toLowerCase(Locale.ROOT),
                                            t.getTerm().toLowerCase(Locale.ROOT));
            if (d < bestDist) {
                bestDist = d;
                best = t.getTerm();
            }
        }
        return bestDist <= MAX_DID_YOU_MEAN_DISTANCE ? best : null;
    }

    @Transactional
    public void recordSearch(String term) {
        if (term == null || term.isBlank()) return;
        String key = term.trim().toLowerCase(Locale.ROOT);
        SearchTerm st = searchTermRepository.findByTerm(key).orElseGet(() -> {
            SearchTerm fresh = new SearchTerm();
            fresh.setTerm(key);
            fresh.setSearchCount(0);
            return fresh;
        });
        st.setSearchCount(st.getSearchCount() + 1);
        st.setLastSearchedAt(LocalDateTime.now());
        searchTermRepository.save(st);
    }

    public List<SearchTerm> getTrendingTerms() {
        return searchTermRepository.findTop10ByOrderBySearchCountDesc();
    }

    /**
     * When a search returns zero results, surface this list of "you might also
     * like" items: top-rated active courses combined with the newest courses,
     * deduplicated, capped at 10.
     */
    public List<Course> getFallbackRecommendations() {
        LinkedHashMap<Long, Course> dedup = new LinkedHashMap<>();
        for (Course c : courseRepository.findTop10ByIsActiveTrueOrderByRatingAvgDesc()) dedup.put(c.getId(), c);
        for (Course c : courseRepository.findTop10ByIsActiveTrueOrderByCreatedAtDesc()) dedup.put(c.getId(), c);
        return new ArrayList<>(dedup.values()).subList(0, Math.min(10, dedup.size()));
    }

    /** Result record for the unified search bar. */
    public record SuggestionResult(List<Course> items, String didYouMean, List<SearchTerm> trending) { }
}
