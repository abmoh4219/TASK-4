package com.registrarops.config;

import com.registrarops.security.AuthEventHandlers;
import com.registrarops.security.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Phase 2 SecurityConfig.
 *
 * - DB-backed authentication via {@link CustomUserDetailsService} (replaces the
 *   Phase 0 in-memory placeholder).
 * - BCryptPasswordEncoder(strength=12).
 * - CSRF enabled with CookieCsrfTokenRepository so HTMX can read the token from
 *   a cookie / meta tag and resend it on every mutating request.
 * - Form login points at custom /login; success and failure are handled by
 *   {@link AuthEventHandlers} (lockout integration).
 * - Role-based URL rules per the business prompt's role matrix.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(CustomUserDetailsService userDetailsService,
                                                               PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AuthEventHandlers handlers) throws Exception {
        http
            // CSRF: enabled on every form post. CookieCsrfTokenRepository.withHttpOnlyFalse
            // lets the HTMX layer read the token from the meta tag and forward it.
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers("/api/v1/**"))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico").permitAll()
                .requestMatchers("/login", "/error").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/grades/**").hasAnyRole("FACULTY", "ADMIN", "STUDENT")
                .requestMatchers("/evaluations/**").hasAnyRole("FACULTY", "REVIEWER", "ADMIN")
                .requestMatchers("/catalog/**").hasAnyRole("STUDENT", "ADMIN")
                .requestMatchers("/orders/**").hasAnyRole("STUDENT", "ADMIN")
                .requestMatchers("/api/notifications/**").authenticated()
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler(handlers.successHandler())
                .failureHandler(handlers.failureHandler())
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                .permitAll())
            .sessionManagement(sm -> sm.maximumSessions(1));
        return http.build();
    }
}
