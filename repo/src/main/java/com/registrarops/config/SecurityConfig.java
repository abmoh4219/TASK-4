package com.registrarops.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Spring Security configuration.
 *
 * Phase 0: in-memory users so the app boots and Phase 0 verification passes.
 * Phase 2 replaces the InMemoryUserDetailsManager with the DB-backed
 * CustomUserDetailsService and adds lockout / device binding integration.
 *
 * CSRF is enabled (Cookie repo) so every Thymeleaf form must include the token.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsService(PasswordEncoder encoder) {
        UserDetails admin = User.withUsername("admin")
                .password(encoder.encode("Admin@Registrar24!"))
                .roles("ADMIN")
                .build();
        UserDetails faculty = User.withUsername("faculty")
                .password(encoder.encode("Faculty@Reg2024!"))
                .roles("FACULTY")
                .build();
        UserDetails reviewer = User.withUsername("reviewer")
                .password(encoder.encode("Review@Reg2024!"))
                .roles("REVIEWER")
                .build();
        UserDetails student = User.withUsername("student")
                .password(encoder.encode("Student@Reg24!"))
                .roles("STUDENT")
                .build();
        return new InMemoryUserDetailsManager(admin, faculty, reviewer, student);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CSRF: enabled for all form POSTs. Token persisted in cookie so HTMX
            // can read it from <meta> tag and resend on every request.
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
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/", true)
                .failureUrl("/login?error")
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll())
            .sessionManagement(sm -> sm.maximumSessions(1));
        return http.build();
    }
}
