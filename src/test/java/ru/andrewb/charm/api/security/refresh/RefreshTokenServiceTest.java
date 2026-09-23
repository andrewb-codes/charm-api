package ru.andrewb.charm.api.security.refresh;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import ru.andrewb.charm.api.JpaIntegrationTest;
import ru.andrewb.charm.api.user.domain.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


@Import(RefreshTokenService.class)
class RefreshTokenServiceTest extends JpaIntegrationTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenProperties properties;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void issuesRefreshTokenAndStoresOnlyItsHash() throws Exception {
        User user = createUser();

        Instant beforeCreation = Instant.now();
        String refreshToken = refreshTokenService.issue(user);
        Instant afterCreation = Instant.now();
        String expectedHash = hash(refreshToken);

        assertThat(refreshToken).isNotBlank();

        entityManager.flush();
        entityManager.clear();

        RefreshToken stored = refreshTokenRepository
                .findByTokenHash(expectedHash)
                .orElseThrow();

        assertThat(stored.getTokenHash()).isEqualTo(expectedHash);
        assertThat(stored.getExpiresAt())
                .isBetween(
                        beforeCreation.plus(properties.ttl()),
                        afterCreation.plus(properties.ttl())
                );
        assertThat(stored.getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    void issuesDifferentRefreshTokensForSameUser() {
        User user = createUser();

        String first = refreshTokenService.issue(user);
        String second = refreshTokenService.issue(user);

        assertThat(first).isNotEqualTo(second);
        assertThat(refreshTokenRepository.count()).isEqualTo(2);
    }

    @Test
    void rotatesActiveRefreshToken() throws Exception {
        User user = createUser();
        String first = refreshTokenService.issue(user);

        RefreshTokenRotation rotation = refreshTokenService.rotate(first);

        String second = rotation.refreshToken();

        assertThat(rotation.user().getId())
                .isEqualTo(user.getId());
        assertThat(second).isNotEqualTo(first);

        entityManager.flush();
        entityManager.clear();

        RefreshToken oldToken = refreshTokenRepository
                .findByTokenHash(hash(first))
                .orElseThrow();

        RefreshToken newToken = refreshTokenRepository
                .findByTokenHash(hash(second))
                .orElseThrow();

        assertThat(oldToken.getRevokedAt()).isNotNull();
        assertThat(oldToken.isActive(Instant.now())).isFalse();

        assertThat(newToken.getRevokedAt()).isNull();
        assertThat(newToken.isActive(Instant.now())).isTrue();

        assertThat(refreshTokenRepository.count()).isEqualTo(2);
    }

    @Test
    void rejectsAlreadyRotatedRefreshToken() {
        User user = createUser();
        String refreshToken = refreshTokenService.issue(user);

        refreshTokenService.rotate(refreshToken);

        assertThatThrownBy(() ->
                refreshTokenService.rotate(refreshToken)
        )
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Invalid or expired refresh token");

        assertThat(refreshTokenRepository.count()).isEqualTo(2);
    }

    @Test
    void rejectsUnknownRefreshToken() {
        assertThatThrownBy(() ->
                refreshTokenService.rotate("unknown-refresh-token")
        )
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Invalid or expired refresh token");
    }

    @Test
    void rejectsExpiredRefreshToken() throws Exception {
        User user = createUser();
        String tokenValue = "expired-refresh-token";

        refreshTokenRepository.saveAndFlush(
                new RefreshToken(
                        user,
                        hash(tokenValue),
                        Instant.now().minusSeconds(1)
                )
        );

        assertThatThrownBy(() ->
                refreshTokenService.rotate(tokenValue)
        )
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Invalid or expired refresh token");
    }

    @Test
    void revokesAllTokensForOnlySelectedUser() {
        User selectedUser = createUser("andrew@example.com");
        User anotherUser = createUser("maria@example.com");

        refreshTokenService.issue(selectedUser);
        refreshTokenService.issue(selectedUser);
        refreshTokenService.issue(anotherUser);

        refreshTokenService.revokeAll(selectedUser.getId());

        entityManager.flush();
        entityManager.clear();

        assertThat(refreshTokenRepository.findAllByUser_Id(selectedUser.getId()))
                .hasSize(2)
                .allSatisfy(token -> assertThat(token.getRevokedAt()).isNotNull());

        assertThat(refreshTokenRepository.findAllByUser_Id(anotherUser.getId()))
                .singleElement()
                .satisfies(token -> assertThat(token.getRevokedAt()).isNull());
    }

    private User createUser() {
        return createUser("andrew@example.com");
    }

    private User createUser(String email) {
        return userRepository.saveAndFlush(
                new User(
                        email,
                        "encoded-password"
                )
        );
    }

    private String hash(String value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(value.getBytes(StandardCharsets.UTF_8))
        );
    }
}
