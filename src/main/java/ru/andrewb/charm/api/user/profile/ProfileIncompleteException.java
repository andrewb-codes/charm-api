package ru.andrewb.charm.api.user.profile;

public class ProfileIncompleteException extends RuntimeException {

    public ProfileIncompleteException() {
        super("Profile must be completed before performing this action");
    }
}
