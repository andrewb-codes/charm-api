package ru.andrewb.charm.api.user.admin;

public class SelfAccountStatusChangeNotAllowedException extends RuntimeException {

    public SelfAccountStatusChangeNotAllowedException() {
        super("Administrator cannot change their own account status");
    }
}
