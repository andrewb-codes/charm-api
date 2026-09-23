package ru.andrewb.charm.api.user.photo;

public class UserPhotoNotFoundException extends RuntimeException {

    public UserPhotoNotFoundException(Long photoId) {
        super("Photo with id %d was not found".formatted(photoId));
    }
}
