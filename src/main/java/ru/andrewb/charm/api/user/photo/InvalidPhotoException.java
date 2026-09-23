package ru.andrewb.charm.api.user.photo;

public class InvalidPhotoException extends RuntimeException {

    public InvalidPhotoException(String message) {
        super(message);
    }

    public InvalidPhotoException(String message, Throwable cause) {
        super(message, cause);
    }
}
