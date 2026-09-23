package ru.andrewb.charm.api.user.discovery;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/discovery")
@Validated
public class UserDiscoveryController {

    private final UserDiscoveryService discoveryService;

    public UserDiscoveryController(UserDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @GetMapping
    public UserDiscoveryResponse getUsers(
            @AuthenticationPrincipal Jwt jwt,

            @RequestParam(defaultValue = "0")
            @Min(0)
            int page,

            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(100)
            int size
    ) {
        Long currentUserId = Long.valueOf(jwt.getSubject());

        return discoveryService.findUsers(
                currentUserId,
                page,
                size
        );
    }
}
