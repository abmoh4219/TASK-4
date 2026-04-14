package com.registrarops.controller.api.v1;

import com.registrarops.entity.PolicySetting;
import com.registrarops.entity.User;
import com.registrarops.repository.UserRepository;
import com.registrarops.service.PolicySettingService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin-only REST surface for persisted policy/config settings.
 * Mutations go through {@link PolicySettingService#set} which validates and
 * audits every change.
 */
@RestController
@RequestMapping("/api/v1/policy")
@Validated
public class PolicyApiV1 {

    private final PolicySettingService policyService;
    private final UserRepository userRepository;

    public PolicyApiV1(PolicySettingService policyService, UserRepository userRepository) {
        this.policyService = policyService;
        this.userRepository = userRepository;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> list() {
        List<PolicySetting> all = policyService.findAll();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("allowedKeys", policyService.allowedKeys());
        body.put("items", all.stream().map(PolicyApiV1::json).toList());
        return body;
    }

    @GetMapping("/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> get(@PathVariable @NotBlank String key) {
        return policyService.get(key)
                .map(v -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("key", key);
                    m.put("value", v);
                    return m;
                })
                .orElseThrow(() -> new IllegalArgumentException("Unknown policy key: " + key));
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> update(@AuthenticationPrincipal UserDetails principal,
                                      @PathVariable @NotBlank String key,
                                      @RequestBody PolicyUpdateDto payload) {
        if (payload == null || payload.value == null || payload.value.isBlank()) {
            throw new IllegalArgumentException("value is required");
        }
        User actor = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        PolicySetting saved = policyService.set(key, payload.value, actor.getId(), actor.getUsername());
        return json(saved);
    }

    private static Map<String, Object> json(PolicySetting s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", s.getKey());
        m.put("value", s.getValue());
        m.put("updatedAt", s.getUpdatedAt() == null ? null : s.getUpdatedAt().toString());
        m.put("updatedBy", s.getUpdatedBy());
        return m;
    }

    /** Minimal request DTO for PUT /api/v1/policy/{key}. */
    public static class PolicyUpdateDto {
        public String value;
        public PolicyUpdateDto() {}
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}
