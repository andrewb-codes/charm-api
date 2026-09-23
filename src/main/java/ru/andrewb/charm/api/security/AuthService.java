package ru.andrewb.charm.api.security;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrewb.charm.api.security.access.JwtTokenService;
import ru.andrewb.charm.api.security.refresh.InvalidRefreshTokenException;
import ru.andrewb.charm.api.security.refresh.RefreshTokenRotation;
import ru.andrewb.charm.api.security.refresh.RefreshTokenService;
import ru.andrewb.charm.api.user.domain.User;
import ru.andrewb.charm.api.user.persistence.UserRepository;
import ru.andrewb.charm.api.user.domain.AccountStatus;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            UserRepository repository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public TokenResponse login(String rawEmail, String rawPassword) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);

        User user = repository.findByEmail(email).orElseThrow(this::badCredentials);

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw badCredentials();
        }

        if (user.getAccountStatus() == AccountStatus.BLOCKED) {
            throw badCredentials();
        }

        String accessToken = jwtTokenService.issue(user);
        String refreshToken = refreshTokenService.issue(user);

        return new TokenResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtTokenService.ttlSeconds()
        );
    }

    @Transactional
    public TokenResponse refresh(String oldRefreshToken) {
        RefreshTokenRotation rotation = refreshTokenService.rotate(oldRefreshToken);

        User user = rotation.user();

        if (user.getAccountStatus() == AccountStatus.BLOCKED) {
            throw new InvalidRefreshTokenException();
        }

        String accessToken = jwtTokenService.issue(user);

        return new TokenResponse(
                accessToken,
                rotation.refreshToken(),
                "Bearer",
                jwtTokenService.ttlSeconds()
        );
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private BadCredentialsException badCredentials() {
        return new BadCredentialsException("Invalid email or password");
    }
}
