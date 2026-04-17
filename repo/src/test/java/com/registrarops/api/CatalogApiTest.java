package com.registrarops.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class CatalogApiTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void testSearchReturnsResults() throws Exception {
        mockMvc.perform(get("/catalog").param("q", "calculus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Calculus")))
                .andExpect(content().string(containsString("MATH201")));
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void testFilterByCategory() throws Exception {
        mockMvc.perform(get("/catalog").param("category", "Mathematics"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Calculus")))
                .andExpect(content().string(containsString("MATH201")));
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void testFilterByMinRatingHighExcludesAll() throws Exception {
        // Seed ratings top out at 4.80 — 5.00 must filter everything out.
        mockMvc.perform(get("/catalog").param("minRating", "5.00"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No matches")));
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void testFilterByTagKeepsCalculus() throws Exception {
        // Seed: MATH201 has tag "calculus,integrals,series" → substring match.
        mockMvc.perform(get("/catalog").param("tag", "calculus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Calculus")));
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void testFilterByAuthorKeepsVance() throws Exception {
        mockMvc.perform(get("/catalog").param("author", "Vance"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Calculus")));
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void testFilterByTagNoMatch() throws Exception {
        mockMvc.perform(get("/catalog").param("tag", "qzqzqz-no-such-tag"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No matches")));
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void testFilterByPriceRange() throws Exception {
        // The seed has CS301 priced 49.99 and several free courses; max=10 should hide CS301.
        mockMvc.perform(get("/catalog").param("maxPrice", "10"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Data Structures"))));
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void testNoResultsReturnsFallback() throws Exception {
        // Made-up term that matches nothing AND is too far for did-you-mean.
        mockMvc.perform(get("/catalog").param("q", "qzqzqzqz"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No matches")));
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void testHtmxSuggestionsEndpoint() throws Exception {
        mockMvc.perform(get("/api/search/suggestions").param("q", "cal"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Calculus")));
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void testRatingSubmit() throws Exception {
        mockMvc.perform(post("/catalog/rate")
                        .with(csrf())
                        .param("itemType", "course")
                        .param("itemId", "1")
                        .param("score", "5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/catalog/detail/course/1"));
    }

    @Test
    @WithMockUser(username = "faculty", roles = "FACULTY")
    void testFacultyCannotAccessCatalog() throws Exception {
        // /catalog/** is restricted to STUDENT and ADMIN per SecurityConfig.
        mockMvc.perform(get("/catalog"))
                .andExpect(status().isForbidden());
    }
}
