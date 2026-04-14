package com.registrarops.controller.api;

import com.registrarops.service.SearchService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTMX endpoint backing the unified search bar dropdown.
 *
 * Returns a Thymeleaf fragment ({@code fragments/search-suggestions :: dropdown})
 * — not JSON — so the front end can hx-swap straight into the DOM with no
 * client-side templating.
 */
@Controller
@RequestMapping("/api/search")
public class SearchApiController {

    private final SearchService searchService;

    public SearchApiController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/suggestions")
    public String suggestions(@RequestParam(value = "q", required = false) String q,
                              Model model) {
        SearchService.SuggestionResult result = searchService.getSuggestions(q == null ? "" : q);
        model.addAttribute("query", q == null ? "" : q);
        model.addAttribute("items", result.items());
        model.addAttribute("didYouMean", result.didYouMean());
        model.addAttribute("trending", result.trending());
        return "fragments/search-suggestions :: dropdown";
    }
}
