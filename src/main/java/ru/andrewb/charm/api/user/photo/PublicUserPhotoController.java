package ru.andrewb.charm.api.user.photo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users/{userId}/photos")
public class PublicUserPhotoController {

    private final UserPhotoService photoService;

    public PublicUserPhotoController(UserPhotoService photoService) {
        this.photoService = photoService;
    }

    @GetMapping
    public List<UserPhotoResponse> getPhotos(@PathVariable Long userId) {
        return photoService.findAllByUserId(userId);
    }
}
