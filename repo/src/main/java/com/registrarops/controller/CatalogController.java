package com.registrarops.controller;

import com.registrarops.entity.User;
import com.registrarops.repository.UserRepository;
import com.registrarops.service.CatalogService;
import com.registrarops.service.SearchService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequestMapping("/catalog")
public class CatalogController {

    private final CatalogService catalogService;
    private final SearchService searchService;
    private final UserRepository userRepository;

    public CatalogController(CatalogService catalogService,
                             SearchService searchService,
                             UserRepository userRepository) {
        this.catalogService = catalogService;
        this.searchService = searchService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String index(@RequestParam(value = "q", required = false) String query,
                        @RequestParam(value = "category", required = false) String category,
                        @RequestParam(value = "minPrice", required = false) BigDecimal minPrice,
                        @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
                        @RequestParam(value = "newArrivals", required = false, defaultValue = "false") boolean newArrivals,
                        @RequestParam(value = "sort", required = false) CatalogService.Sort sort,
                        Model model) {
        if (query != null && !query.isBlank()) {
            searchService.recordSearch(query);
        }
        var results = catalogService.search(query, category, minPrice, maxPrice, newArrivals, sort);
        model.addAttribute("results", results);
        model.addAttribute("query", query == null ? "" : query);
        model.addAttribute("category", category);
        model.addAttribute("minPrice", minPrice);
        model.addAttribute("maxPrice", maxPrice);
        model.addAttribute("newArrivals", newArrivals);
        model.addAttribute("sort", sort);
        model.addAttribute("trending", catalogService.getTrending());
        if (results.isEmpty()) {
            model.addAttribute("fallback", searchService.getFallbackRecommendations());
        }
        return "catalog/index";
    }

    @GetMapping("/detail/{type}/{id}")
    public String detail(@PathVariable String type,
                         @PathVariable Long id,
                         Model model) {
        if (!"course".equalsIgnoreCase(type)) {
            return "redirect:/catalog";
        }
        var course = catalogService.getCourse(id).orElse(null);
        if (course == null) {
            return "redirect:/catalog";
        }
        model.addAttribute("course", course);
        model.addAttribute("materials", catalogService.getMaterialsForCourse(id));
        return "catalog/detail";
    }

    @PostMapping("/rate")
    public String rate(@AuthenticationPrincipal UserDetails principal,
                       @RequestParam("itemType") String itemType,
                       @RequestParam("itemId") Long itemId,
                       @RequestParam("score") int score,
                       RedirectAttributes redirect) {
        User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        catalogService.rate(user.getId(), user.getUsername(), itemType, itemId, score);
        redirect.addFlashAttribute("flashSuccess", "Rating saved.");
        return "redirect:/catalog/detail/" + itemType + "/" + itemId;
    }
}
