package com.registrarops.security;

import com.registrarops.entity.User;
import com.registrarops.repository.UserRepository;
import com.registrarops.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * DB-backed UserDetailsService.
 *
 * - rejects users where deletedAt IS NOT NULL (soft-deleted accounts cannot sign in)
 * - rejects users where isActive = false
 * - sets accountNonLocked from AuthService.isLockedOut(username) so Spring Security
 *   throws LockedException for locked accounts BEFORE comparing the password
 *
 * The Role enum value already includes the "ROLE_" prefix (e.g. ROLE_ADMIN), so we
 * pass it straight through as a SimpleGrantedAuthority.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final AuthService authService;

    @Autowired
    public CustomUserDetailsService(UserRepository userRepository,
                                    @Lazy AuthService authService) {
        this.userRepository = userRepository;
        this.authService = authService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("No such user: " + username));

        if (user.getDeletedAt() != null) {
            throw new UsernameNotFoundException("Account has been deleted: " + username);
        }

        boolean enabled = Boolean.TRUE.equals(user.getIsActive());
        boolean locked  = authService.isLockedOut(username);

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPasswordHash(),
                enabled,
                /*accountNonExpired*/ true,
                /*credentialsNonExpired*/ true,
                /*accountNonLocked*/ !locked,
                List.of(new SimpleGrantedAuthority(user.getRole().name()))
        );
    }
}
