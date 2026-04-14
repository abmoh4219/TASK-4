package com.registrarops.api;

import com.registrarops.repository.LoginAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class AuthApiTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private LoginAttemptRepository loginAttemptRepository;

    @BeforeEach
    void clearAttempts() {
        // Each test starts with a fresh failed-attempt counter so lockout state is deterministic.
        loginAttemptRepository.deleteAll();
    }

    @Test
    void loginPageIsPublic() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("RegistrarOps")));
    }

    @Test
    void testLoginSuccess() throws Exception {
        mockMvc.perform(formLogin("/login").user("admin").password("Admin@Registrar24!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated().withUsername("admin").withRoles("ADMIN"));
    }

    @Test
    void testLoginWrongPassword() throws Exception {
        mockMvc.perform(formLogin("/login").user("admin").password("WrongPassword!1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void testAccountLockedAfter5Attempts() throws Exception {
        // 5 wrong attempts → 6th should redirect to /login?locked
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(formLogin("/login").user("faculty").password("WrongPassword!" + i))
                    .andExpect(status().is3xxRedirection());
        }
        mockMvc.perform(formLogin("/login").user("faculty").password("WrongPasswordAgain!"))
                .andExpect(redirectedUrl("/login?locked"))
                .andExpect(unauthenticated());
        // even the correct password is rejected while locked
        mockMvc.perform(formLogin("/login").user("faculty").password("Faculty@Reg2024!"))
                .andExpect(redirectedUrl("/login?locked"))
                .andExpect(unauthenticated());
    }

    @Test
    void testCsrfRequiredOnPost() throws Exception {
        // A POST to /logout without a CSRF token must be rejected.
        mockMvc.perform(post("/logout"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testLogoutInvalidatesSession() throws Exception {
        mockMvc.perform(formLogin("/login").user("student").password("Student@Reg24!"))
                .andExpect(authenticated());
        mockMvc.perform(post("/logout").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(unauthenticated());
    }
}
