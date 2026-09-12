package com.receiptvision.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.receiptvision.core.domain.AppUser;
import com.receiptvision.core.repository.UserRepository;
import com.receiptvision.core.security.JwtService;
import com.receiptvision.core.web.dto.AuthRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository users;
    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder encoder;
    @Mock
    private AuthenticationManager authManager;
    @Mock
    private JwtService jwtService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, encoder, authManager, jwtService);
    }

    @Test
    void register_normalizesUsernameToLowercase() {
        when(users.existsByUsernameIgnoreCase("ali")).thenReturn(false);
        when(users.existsByUsername("ali")).thenReturn(false);
        when(encoder.encode("Pass1234")).thenReturn("hash");
        when(users.save(any(AppUser.class))).thenAnswer(i -> i.getArgument(0));
        when(jwtService.generateToken("ali")).thenReturn("tok");

        var res = service.register(new AuthRequest("ALI", "Pass1234"));
        assertThat(res.username()).isEqualTo("ali");
        assertThat(res.token()).isEqualTo("tok");
    }

    @Test
    void register_duplicateMapsTo409() {
        when(users.existsByUsernameIgnoreCase("ali")).thenReturn(true);
        assertThatThrownBy(() -> service.register(new AuthRequest("ali", "Pass1234")))
                .isInstanceOf(DuplicateUsernameException.class);
    }

    @Test
    void register_rejectsWeakPassword() {
        assertThatThrownBy(() -> service.register(new AuthRequest("ali", "password")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("letter and one digit");
        assertThatThrownBy(() -> service.register(new AuthRequest("ali", "12345678")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requireUser_isCaseInsensitive() {
        AppUser ali = new AppUser("ali", "hash");
        when(users.findByUsernameIgnoreCase("ali")).thenReturn(Optional.of(ali));
        assertThat(service.requireUser("ALI")).isSameAs(ali);
    }
}
