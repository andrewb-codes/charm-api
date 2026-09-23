package ru.andrewb.charm.api.user.photo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserPhotoRepository extends JpaRepository<UserPhoto, Long> {

    List<UserPhoto> findAllByUser_IdOrderByPosition(Long userId);

    long countByUser_Id(Long userId);

    Optional<UserPhoto> findFirstByUser_IdOrderByPositionDesc(Long userId);

    Optional<UserPhoto> findByIdAndUser_Id(Long photoId, Long userId);

    @Query("""
            select photo.objectKey
            from UserPhoto photo
            where photo.user.id = :userId
            order by photo.position
            """)
    List<String>  findObjectKeysByUserId(@Param("userId") Long userId);
}
