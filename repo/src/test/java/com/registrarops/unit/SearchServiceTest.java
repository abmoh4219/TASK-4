package com.registrarops.unit;

import com.registrarops.entity.Course;
import com.registrarops.entity.SearchTerm;
import com.registrarops.repository.CourseRepository;
import com.registrarops.repository.SearchTermRepository;
import com.registrarops.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SearchServiceTest {

    private CourseRepository courseRepository;
    private SearchTermRepository searchTermRepository;
    private SearchService searchService;

    @BeforeEach
    void setUp() {
        courseRepository = mock(CourseRepository.class);
        searchTermRepository = mock(SearchTermRepository.class);
        searchService = new SearchService(courseRepository, searchTermRepository);
    }

    private static Course course(long id, String title, String category) {
        Course c = new Course();
        c.setId(id);
        c.setCode("X" + id);
        c.setTitle(title);
        c.setCategory(category);
        c.setPrice(BigDecimal.ZERO);
        c.setRatingAvg(new BigDecimal("4.50"));
        c.setEnrollCount(10);
        c.setCreatedAt(LocalDateTime.now());
        return c;
    }

    private static SearchTerm term(String t, int count) {
        SearchTerm st = new SearchTerm();
        st.setTerm(t);
        st.setSearchCount(count);
        st.setLastSearchedAt(LocalDateTime.now());
        return st;
    }

    @Test
    void testSuggestionsReturnResults() {
        when(courseRepository.searchByTerm("calculus"))
                .thenReturn(List.of(course(1, "Calculus II", "Mathematics")));
        when(searchTermRepository.findTop10ByOrderBySearchCountDesc())
                .thenReturn(List.of(term("calculus", 50)));

        var result = searchService.getSuggestions("calculus");

        assertEquals(1, result.items().size());
        assertEquals("Calculus II", result.items().get(0).getTitle());
    }

    @Test
    void testLevenshteinCorrection() {
        when(courseRepository.searchByTerm("calculas")).thenReturn(List.of()); // 0 results, triggers did-you-mean
        when(courseRepository.searchByTerm("calculus")).thenReturn(List.of(course(1, "Calculus II", "Mathematics")));
        when(searchTermRepository.findTop10ByOrderBySearchCountDesc())
                .thenReturn(List.of(term("calculus", 50), term("history", 20)));

        var result = searchService.getSuggestions("calculas");

        assertEquals("calculus", result.didYouMean());
    }

    @Test
    void testFallbackWhenNoResults() {
        when(courseRepository.findTop10ByIsActiveTrueOrderByRatingAvgDesc())
                .thenReturn(List.of(course(1, "Top rated", "Mathematics")));
        when(courseRepository.findTop10ByIsActiveTrueOrderByCreatedAtDesc())
                .thenReturn(List.of(course(2, "Newest", "History")));

        var fallback = searchService.getFallbackRecommendations();
        assertEquals(2, fallback.size());
    }

    @Test
    void testTrendingTermsOrderedByCount() {
        when(searchTermRepository.findTop10ByOrderBySearchCountDesc())
                .thenReturn(List.of(term("calculus", 50), term("history", 20)));
        var trending = searchService.getTrendingTerms();
        assertEquals(2, trending.size());
        assertEquals("calculus", trending.get(0).getTerm());
    }

    @Test
    void testSearchRecordsTerm() {
        when(searchTermRepository.findByTerm("algorithms")).thenReturn(Optional.empty());
        searchService.recordSearch("algorithms");
        verify(searchTermRepository).save(any(SearchTerm.class));
    }

    @Test
    void testSynonymExpansion() {
        // "math" should normalize to "mathematics" — we just verify the normalize path,
        // which the suggestion query then uses.
        assertEquals("mathematics", searchService.normalize("math"));
        assertEquals("calculus", searchService.normalize("calc"));
    }

    @Test
    void testPinyinSubstitution() {
        // 数学 → shuxue
        assertEquals("shuxue", searchService.normalize("数学"));
        assertEquals("lishi", searchService.normalize("历史"));
    }
}
