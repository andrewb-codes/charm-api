package ru.andrewb.charm.api.user.admin;

public class AdminAccountModificationNotAllowedException extends RuntimeException {

    public AdminAccountModificationNotAllowedException() {
        super("Administrator accounts cannot be modified");
    }
}
