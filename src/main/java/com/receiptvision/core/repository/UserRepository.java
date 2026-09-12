package com.receiptvision.core.repository;

import java.util.Optional;

import com.receiptvision.core.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    // Case-insensitive variants: usernames are stored lowercase (see AuthService.normalize),
    // but legacy rows with uppercase must still resolve.
    Optional<AppUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);
}
