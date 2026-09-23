package ru.andrewb.charm.api.reaction;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/{receiverId}/reaction")
public class ReactionController {

    private final ReactionService reactionService;

    public ReactionController(ReactionService reactionService) {
        this.reactionService = reactionService;
    }

    @PutMapping
    public ReactionResult react(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long receiverId,
            @Valid @RequestBody ReactionRequest request
    ) {
        Long senderId = Long.valueOf(jwt.getSubject());

        return reactionService.react(
                senderId,
                receiverId,
                request.type()
        );
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeReaction(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long receiverId
    ) {
        Long senderId = Long.valueOf(jwt.getSubject());

        reactionService.removeReaction(
                senderId,
                receiverId
        );
    }
}