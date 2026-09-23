package ru.andrewb.charm.api.reaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.andrewb.charm.api.user.domain.User;
import ru.andrewb.charm.api.user.domain.AccountStatus;
import ru.andrewb.charm.api.user.domain.ProfileStatus;

import java.util.List;
import java.util.Optional;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    Optional<Reaction> findBySender_IdAndReceiver_Id(
            Long senderId,
            Long receiverId
    );

    boolean existsBySender_IdAndReceiver_IdAndType(
            Long senderId,
            Long receiverId,
            ReactionType type
    );

    void deleteBySender_IdAndReceiver_Id(
            Long senderId,
            Long receiverId
    );

    @Query("""
            select outgoing.receiver
            from Reaction outgoing
            where outgoing.sender.id = :userId
              and outgoing.type = :type
              and outgoing.receiver.profileStatus = :profileStatus
              and outgoing.receiver.accountStatus = :accountStatus
              and exists (
                select incoming.id
                from Reaction incoming
                where incoming.sender.id = outgoing.receiver.id
                  and incoming.receiver.id = :userId
                  and incoming.type = :type
              )
            order by outgoing.createdAt desc
            """)
    List<User> findMatchedUsers(
            @Param("userId") Long userId,
            @Param("type") ReactionType type,
            @Param("profileStatus") ProfileStatus profileStatus,
            @Param("accountStatus") AccountStatus accountStatus
    );
}
