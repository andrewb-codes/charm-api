package ru.andrewb.charm.api.user.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.andrewb.charm.api.user.domain.User;
import ru.andrewb.charm.api.user.domain.AccountStatus;
import ru.andrewb.charm.api.user.domain.ProfileStatus;
import ru.andrewb.charm.api.user.domain.UserRole;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByIdAndProfileStatusAndAccountStatus(
            Long id,
            ProfileStatus profileStatus,
            AccountStatus accountStatus
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select user
        from User user
        where user.id = :id
        """)
    Optional<User> findByIdForUpdate(
            @Param("id") Long id
    );

    @Query("""
            select user
            from User user
            where lower(user.email) like concat('%', :email, '%')
              and (:role is null or user.role = :role)
              and (:profileStatus is null or user.profileStatus = :profileStatus)
              and (:accountStatus is null or user.accountStatus = :accountStatus)
            """)
    Page<User> findAdminUsers(
            @Param("email") String email,
            @Param("role") UserRole role,
            @Param("profileStatus") ProfileStatus profileStatus,
            @Param("accountStatus") AccountStatus accountStatus,
            Pageable pageable
    );

    @Query("""
            select user
            from User user
            where user.profileStatus = :profileStatus
              and user.accountStatus = :accountStatus
              and user.id <> :currentUserId
              and not exists (
                  select reaction.id
                  from Reaction reaction
                  where reaction.sender.id = :currentUserId
                    and reaction.receiver.id = user.id
              )
            order by user.id
            """)
    Slice<User> findDiscoveryUsers(
            @Param("currentUserId") Long currentUserId,
            @Param("profileStatus") ProfileStatus profileStatus,
            @Param("accountStatus") AccountStatus accountStatus,
            Pageable pageable
    );
}
