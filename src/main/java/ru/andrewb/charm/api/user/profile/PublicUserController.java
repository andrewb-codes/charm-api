package ru.andrewb.charm.api.user.profile;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class PublicUserController {

    private final UserProfileQueryService queryService;

    public PublicUserController(UserProfileQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{id}")
    public PublicUserResponse get(@PathVariable Long id) {
        return queryService.findPublicById(id);
    }
}
