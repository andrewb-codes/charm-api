package ru.andrewb.charm.api.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.andrewb.charm.api.security.access.JwtTokenService;
import ru.andrewb.charm.api.security.refresh.InvalidRefreshTokenException;
import ru.andrewb.charm.api.security.refresh.RefreshTokenRotation;
import ru.andrewb.charm.api.security.refresh.RefreshTokenService;
import ru.andrewb.charm.api.user.domain.User;
import ru.andrewb.charm.api.user.persistence.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    @Test
    void logsInWithNormalizedEmailAndCorrectPassword() {
        User user = new User(
                "andrew@example.com",
                "encoded-password"
        );

        when(repository.findByEmail("andrew@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "secret-password",
                "encoded-password"
        )).thenReturn(true);

        when(jwtTokenService.issue(user))
                .thenReturn("test-access-token");

        when(refreshTokenService.issue(user))
                .thenReturn("test-refresh-token");

        when(jwtTokenService.ttlSeconds())
                .thenReturn(3600L);

        TokenResponse response = authService.login(
                "  ANDREW@Example.COM  ",
                "secret-password"
        );

        assertThat(response.accessToken())
                .isEqualTo("test-access-token");

        assertThat(response.refreshToken())
                .isEqualTo("test-refresh-token");

        assertThat(response.tokenType())
                .isEqualTo("Bearer");

        assertThat(response.expiresIn())
                .isEqualTo(3600L);

        verify(repository)
                .findByEmail("andrew@example.com");

        verify(passwordEncoder)
                .matches(
                        "secret-password",
                        "encoded-password"
                );

        verify(jwtTokenService)
                .issue(user);

        verify(refreshTokenService)
                .issue(user);
    }

    @Test
    void rejectsIncorrectPasswordWithoutCreatingToken() {
        User user = new User(
                "andrew@example.com",
                "encoded-password"
        );

        when(repository.findByEmail("andrew@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "wrong-password",
                "encoded-password"
        )).thenReturn(false);

        assertThatThrownBy(() ->
                authService.login(
                        "andrew@example.com",
                        "wrong-password"
                )
        )
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");

        verifyNoInteractions(
                jwtTokenService,
                refreshTokenService
        );
    }

    @Test
    void rejectsLoginForBlockedAccount() {
        User user = new User(
                "andrew@example.com",
                "encoded-password"
        );
        user.block();

        when(repository.findByEmail("andrew@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret-password", "encoded-password"))
                .thenReturn(true);

        assertThatThrownBy(() -> authService.login(
                "andrew@example.com",
                "secret-password"
        ))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");

        verifyNoInteractions(jwtTokenService, refreshTokenService);
    }

    @Test
    void refreshesAccessAndRefreshTokens() {
        User user = new User(
                "andrew@example.com",
                "encoded-password"
        );

        when(refreshTokenService.rotate("old-refresh-token"))
                .thenReturn(new RefreshTokenRotation(
                        user,
                        "new-refresh-token"
                ));

        when(jwtTokenService.issue(user))
                .thenReturn("new-access-token");

        when(jwtTokenService.ttlSeconds())
                .thenReturn(3600L);

        TokenResponse response =
                authService.refresh("old-refresh-token");

        assertThat(response.accessToken())
                .isEqualTo("new-access-token");

        assertThat(response.refreshToken())
                .isEqualTo("new-refresh-token");

        assertThat(response.tokenType())
                .isEqualTo("Bearer");

        assertThat(response.expiresIn())
                .isEqualTo(3600L);

        verify(refreshTokenService)
                .rotate("old-refresh-token");

        verify(jwtTokenService)
                .issue(user);
    }

    @Test
    void rejectsRefreshForBlockedAccount() {
        User user = new User(
                "andrew@example.com",
                "encoded-password"
        );
        user.block();

        when(refreshTokenService.rotate("old-refresh-token"))
                .thenReturn(new RefreshTokenRotation(
                        user,
                        "new-refresh-token"
                ));

        assertThatThrownBy(() -> authService.refresh("old-refresh-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verifyNoInteractions(jwtTokenService);
    }
}
