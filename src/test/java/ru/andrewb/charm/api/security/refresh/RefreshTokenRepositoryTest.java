package ru.andrewb.charm.api.security.refresh;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import ru.andrewb.charm.api.JpaIntegrationTest;
import ru.andrewb.charm.api.user.domain.User;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshTokenRepositoryTest
        extends JpaIntegrationTest {

    private static final String TOKEN_HASH = "a".repeat(64);

    @Autowired
    private RefreshTokenRepository refreshRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savesAndFindsRefreshTokenByHash() {
        User user = createUser();

        RefreshToken saved = refreshRepository.saveAndFlush(
                new RefreshToken(
                        user,
                        TOKEN_HASH,
                        Instant.now().plusSeconds(3600)
                )
        );

        entityManager.clear();

        RefreshToken found = refreshRepository
                .findByTokenHash(TOKEN_HASH)
                .orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getUser().getId())
                .isEqualTo(user.getId());
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getRevokedAt()).isNull();
        assertThat(found.isActive(Instant.now())).isTrue();
    }

    @Test
    void marksTokenAsRevoked() {
        User user = createUser();

        RefreshToken token = refreshRepository.saveAndFlush(
                new RefreshToken(
                        user,
                        TOKEN_HASH,
                        Instant.now().plusSeconds(3600)
                )
        );

        Instant revokedAt = Instant.parse("2025-01-01T12:00:00.123456Z");

        token.revoke(revokedAt);
        refreshRepository.saveAndFlush(token);

        entityManager.clear();

        RefreshToken found = refreshRepository
                .findByTokenHash(TOKEN_HASH)
                .orElseThrow();

        assertThat(found.getRevokedAt())
                .isEqualTo(revokedAt);

        assertThat(found.isActive(Instant.now()))
                .isFalse();
    }

    @Test
    void rejectsDuplicateTokenHash() {
        User user = createUser();

        refreshRepository.saveAndFlush(
                new RefreshToken(
                        user,
                        TOKEN_HASH,
                        Instant.now().plusSeconds(3600)
                )
        );

        assertThatThrownBy(() ->
                refreshRepository.saveAndFlush(
                        new RefreshToken(
                                user,
                                TOKEN_HASH,
                                Instant.now().plusSeconds(7200)
                        )
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    private User createUser() {
        return userRepository.saveAndFlush(
                new User(
                        "andrew@example.com",
                        "encoded-password"
                )
        );
    }
}
