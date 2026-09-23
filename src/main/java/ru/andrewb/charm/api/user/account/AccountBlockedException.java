package ru.andrewb.charm.api.user.account;

public class AccountBlockedException extends RuntimeException {

    public AccountBlockedException() {
        super("Account is blocked");
    }
}
