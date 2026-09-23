package ru.andrewb.charm.api.user.photo;

import jakarta.persistence.*;
import ru.andrewb.charm.api.user.domain.User;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "user_photos",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "user_photos_object_key_unique",
                        columnNames = "object_key"
                ),
                @UniqueConstraint(
                        name = "user_photos_user_position_unique",
                        columnNames = {"user_id", "position"}
                )
        }
)
public class UserPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "user_photos_user_fk")
    )
    private User user;

    @Column(
            name = "object_key",
            nullable = false,
            length = 500,
            updatable = false
    )
    private String objectKey;

    @Column(nullable = false)
    private int position;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    protected UserPhoto() {
    }

    public UserPhoto(
            User user,
            String objectKey,
            int position
    ) {
        if (position < 0) {
            throw new IllegalArgumentException("Photo position cannot be negative");
        }

        this.user = Objects.requireNonNull(user);
        this.objectKey = Objects.requireNonNull(objectKey);
        this.position = position;
    }

    @PrePersist
    private void beforeInsert() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public int getPosition() {
        return position;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
