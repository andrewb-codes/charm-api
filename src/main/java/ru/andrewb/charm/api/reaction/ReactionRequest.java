package ru.andrewb.charm.api.reaction;

import jakarta.validation.constraints.NotNull;

public record ReactionRequest(

        @NotNull(message = "Reaction type is required")
        ReactionType type
) {
}
