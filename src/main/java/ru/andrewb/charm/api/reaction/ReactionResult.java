package ru.andrewb.charm.api.reaction;

public record ReactionResult(
        Long reactionId,
        ReactionType type,
        boolean matched
) {
}
