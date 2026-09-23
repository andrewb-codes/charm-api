package ru.andrewb.charm.api.user.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrewb.charm.api.security.refresh.RefreshTokenService;
import ru.andrewb.charm.api.user.domain.AccountStatus;
import ru.andrewb.charm.api.user.domain.User;
import ru.andrewb.charm.api.user.domain.UserRole;
import ru.andrewb.charm.api.user.persistence.UserRepository;
import ru.andrewb.charm.api.user.profile.UserNotFoundException;

import java.util.List;
import java.util.Locale;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;

    public AdminUserService(
            UserRepository userRepository,
            RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(readOnly = true)
    public AdminUserPageResponse findUsers(
            AdminUserFilter filter,
            int page,
            int size
    ) {
        Page<User> result = userRepository.findAdminUsers(
                normalizeEmail(filter.email()),
                filter.role(),
                filter.profileStatus(),
                filter.accountStatus(),
                PageRequest.of(
                        page,
                        size,
                        Sort.by(Sort.Direction.ASC, "id")
                )
        );

        List<AdminUserResponse> users = result
                .getContent()
                .stream()
                .map(AdminUserResponse::from)
                .toList();

        return new AdminUserPageResponse(
                users,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional
    public AdminUserResponse updateAccountStatus(
            Long adminId,
            Long userId,
            AccountStatus status
    ) {
        if (adminId.equals(userId)) {
            throw new SelfAccountStatusChangeNotAllowedException();
        }

        User user = userRepository
                .findByIdForUpdate(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.getRole() == UserRole.ADMIN) {
            throw new AdminAccountModificationNotAllowedException();
        }

        if (status == AccountStatus.BLOCKED) {
            user.block();
            refreshTokenService.revokeAll(userId);
        }

        if (status == AccountStatus.ACTIVE) {
            user.unblock();
        }

        return AdminUserResponse.from(user);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }

        return email.trim().toLowerCase(Locale.ROOT);
    }
}
