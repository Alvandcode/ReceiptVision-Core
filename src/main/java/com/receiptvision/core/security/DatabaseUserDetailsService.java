package com.receiptvision.core.security;

import com.receiptvision.core.repository.UserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public DatabaseUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null) {
            throw new UsernameNotFoundException("User not found");
        }
        String normalized = username.trim().toLowerCase();
        var appUser = users.findByUsernameIgnoreCase(normalized)
                .or(() -> users.findByUsername(username))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        // Single role; authorization is purely ownership-based (see ReceiptService).
        return User.withUsername(appUser.getUsername())
                .password(appUser.getPasswordHash())
                .authorities("ROLE_USER")
                .build();
    }
}
