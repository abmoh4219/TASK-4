package com.registrarops.controller.api.v1.dto;

import jakarta.validation.constraints.Size;

import java.util.HashMap;
import java.util.Map;

/**
 * Request DTO for {@code POST /api/v1/import/courses}. Each {@code map_*}
 * field, if non-blank, is treated as the source header for its canonical
 * field. Validated for reasonable length so an attacker cannot push giant
 * strings through the mapping channel.
 */
public class CsvImportMappingDto {

    @Size(max = 100) private String mapCode;
    @Size(max = 100) private String mapTitle;
    @Size(max = 100) private String mapCredits;
    @Size(max = 100) private String mapPrice;
    @Size(max = 100) private String mapCategory;

    public Map<String, String> toMapping() {
        Map<String, String> m = new HashMap<>();
        putIfValue(m, "code", mapCode);
        putIfValue(m, "title", mapTitle);
        putIfValue(m, "credits", mapCredits);
        putIfValue(m, "price", mapPrice);
        putIfValue(m, "category", mapCategory);
        return m;
    }

    private static void putIfValue(Map<String, String> m, String key, String value) {
        if (value != null && !value.isBlank()) m.put(key, value);
    }

    public String getMapCode()     { return mapCode; }
    public void setMapCode(String mapCode)         { this.mapCode = mapCode; }
    public String getMapTitle()    { return mapTitle; }
    public void setMapTitle(String mapTitle)       { this.mapTitle = mapTitle; }
    public String getMapCredits()  { return mapCredits; }
    public void setMapCredits(String mapCredits)   { this.mapCredits = mapCredits; }
    public String getMapPrice()    { return mapPrice; }
    public void setMapPrice(String mapPrice)       { this.mapPrice = mapPrice; }
    public String getMapCategory() { return mapCategory; }
    public void setMapCategory(String mapCategory) { this.mapCategory = mapCategory; }
}
