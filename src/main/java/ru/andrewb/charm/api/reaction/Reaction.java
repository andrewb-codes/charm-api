package ru.andrewb.charm.api.reaction;

import jakarta.persistence.*;
import ru.andrewb.charm.api.user.domain.User;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "reactions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "reactions_sender_receiver_unique",
                        columnNames = {
                                "sender_id",
                                "receiver_id"
                        }
                )
        }
)
public class Reaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "sender_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "reactions_sender_fk")
    )
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "receiver_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "reactions_receiver_fk")
    )
    private User receiver;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "reaction_type",
            nullable = false,
            length = 20
    )
    private ReactionType type;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    protected Reaction() {
    }

    public Reaction(
            User sender,
            User receiver,
            ReactionType type
    ) {
        this.sender = Objects.requireNonNull(sender);
        this.receiver = Objects.requireNonNull(receiver);
        this.type = Objects.requireNonNull(type);
    }

    @PrePersist
    private void beforeInsert() {
        createdAt = Instant.now();
    }

    public void changeType(ReactionType type) {
        this.type = Objects.requireNonNull(type);
    }

    public Long getId() {
        return id;
    }

    public User getSender() {
        return sender;
    }

    public User getReceiver() {
        return receiver;
    }

    public ReactionType getType() {
        return type;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}