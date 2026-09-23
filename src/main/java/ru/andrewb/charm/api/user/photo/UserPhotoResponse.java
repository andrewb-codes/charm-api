package ru.andrewb.charm.api.user.photo;

public record UserPhotoResponse(
        Long id,
        int position,
        String url
) {

    public static UserPhotoResponse from(
            UserPhoto photo,
            String url
    ) {
        return new UserPhotoResponse(
                photo.getId(),
                photo.getPosition(),
                url
        );
    }
}
