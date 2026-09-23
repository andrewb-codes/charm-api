package ru.andrewb.charm.api.user.profile;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.andrewb.charm.api.user.account.EmailChangeRequest;
import ru.andrewb.charm.api.user.account.PasswordChangeRequest;
import ru.andrewb.charm.api.user.account.UserAccountService;

@RestController
@RequestMapping("/api/v1/me")
public class CurrentUserController {

    private final UserProfileQueryService queryService;
    private final UserProfileService profileService;
    private final UserAccountService accountService;

    public CurrentUserController(
            UserProfileQueryService queryService,
            UserProfileService profileService,
            UserAccountService accountService
    ) {
        this.queryService = queryService;
        this.profileService = profileService;
        this.accountService = accountService;
    }

    @GetMapping
    public CurrentUserResponse get(
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.parseLong(jwt.getSubject());
        return queryService.findCurrentById(userId);
    }

    @PutMapping
    public CurrentUserResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UserProfileUpdateRequest request
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return profileService.update(userId, request);
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PasswordChangeRequest request
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        accountService.changePassword(
                userId,
                request.currentPassword(),
                request.newPassword()
        );
    }

    @PutMapping("/email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeEmail(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EmailChangeRequest request
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        accountService.changeEmail(
                userId,
                request.currentPassword(),
                request.newEmail()
        );
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        accountService.delete(userId);
    }
}
