package ru.andrewb.charm.api.security.access;


import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import ru.andrewb.charm.api.user.domain.User;

import java.time.Instant;

@Service
public class JwtTokenService {

    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public JwtTokenService(
            JwtEncoder encoder,
            JwtProperties properties
    ) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public String issue(User user) {
            Instant now = Instant.now();

            JwsHeader header = JwsHeader
                    .with(MacAlgorithm.HS256)
                    .build();

            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer(properties.issuer())
                    .issuedAt(now)
                    .expiresAt(now.plus(properties.accessTokenTtl()))
                    .subject(user.getId().toString())
                    .claim("role", user.getRole().name())
                    .build();

        return encoder
                .encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }

    public long ttlSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }
}
