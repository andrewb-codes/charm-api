package ru.andrewb.charm.api.user.photo;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/api/v1/me/photos")
public class CurrentUserPhotoController {

    private final UserPhotoService photoService;

    public CurrentUserPhotoController(UserPhotoService photoService) {
        this.photoService = photoService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UserPhotoResponse upload(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("file") MultipartFile file
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        try (InputStream inputStream = file.getInputStream()) {
            return photoService.upload(
                    userId,
                    inputStream,
                    file.getSize(),
                    file.getContentType()
            );
        } catch (IOException exception) {
            throw new InvalidPhotoException(
                    "Failed to read photo file",
                    exception
            );
        }
    }

    @DeleteMapping("/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long photoId
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        photoService.delete(userId, photoId);
    }
}
