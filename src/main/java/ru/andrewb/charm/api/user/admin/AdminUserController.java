package ru.andrewb.charm.api.user.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.andrewb.charm.api.user.domain.AccountStatus;
import ru.andrewb.charm.api.user.domain.ProfileStatus;
import ru.andrewb.charm.api.user.domain.UserRole;

@RestController
@RequestMapping("/api/v1/admin/users")
@Validated
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public AdminUserPageResponse getUsers(
            @RequestParam(required = false)
            String email,

            @RequestParam(required = false)
            UserRole role,

            @RequestParam(required = false)
            ProfileStatus profileStatus,

            @RequestParam(required = false)
            AccountStatus accountStatus,

            @RequestParam(defaultValue = "0")
            @Min(0)
            int page,

            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(100)
            int size
    ) {
        AdminUserFilter filter = new AdminUserFilter(
                email,
                role,
                profileStatus,
                accountStatus
        );

        return adminUserService.findUsers(filter, page, size);
    }

    @PutMapping("/{userId}/account-status")
    public AdminUserResponse updateAccountStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long userId,
            @Valid @RequestBody AdminUserAccountStatusUpdateRequest request
    ) {
        Long adminId = Long.valueOf(jwt.getSubject());

        return adminUserService.updateAccountStatus(
                adminId,
                userId,
                request.status()
        );
    }
}
