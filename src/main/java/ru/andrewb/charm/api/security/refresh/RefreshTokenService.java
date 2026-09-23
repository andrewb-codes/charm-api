package ru.andrewb.charm.api.security.refresh;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrewb.charm.api.user.domain.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {

    private static final int TOKEN_SIZE_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final RefreshTokenProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository repository,
            RefreshTokenProperties properties
    ) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional
    public String issue(User user) {
        String tokenValue = generateToken();
        String tokenHash = hash(tokenValue);
        Instant expiresAt = Instant.now().plus(properties.ttl());

        RefreshToken refreshToken = new RefreshToken(user, tokenHash, expiresAt);

        repository.save(refreshToken);

        return tokenValue;
    }

    @Transactional
    public RefreshTokenRotation rotate(String tokenValue) {
        Instant now = Instant.now();
        String tokenHash = hash(tokenValue);

        RefreshToken currentRefreshToken = repository
                .findForUpdateByTokenHash(tokenHash)
                .filter(token -> token.isActive(now))
                .orElseThrow(InvalidRefreshTokenException::new);

        currentRefreshToken.revoke(now);

        String newRefreshToken = issue(currentRefreshToken.getUser());

        return new RefreshTokenRotation(
                currentRefreshToken.getUser(),
                newRefreshToken
        );
    }

    @Transactional
    public void revoke(String tokenValue) {
        Instant now = Instant.now();
        String tokenHash = hash(tokenValue);

        repository.findForUpdateByTokenHash(tokenHash)
                .ifPresent(token -> token.revoke(now));
    }

    @Transactional
    public void revokeAll(Long userId) {
        Instant now = Instant.now();

        repository.findAllByUser_Id(userId)
                .forEach(token -> token.revoke(now));
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_SIZE_BYTES];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }
}
