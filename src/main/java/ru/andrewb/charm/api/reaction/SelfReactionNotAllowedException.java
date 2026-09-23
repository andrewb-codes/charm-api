package ru.andrewb.charm.api.reaction;

public class SelfReactionNotAllowedException extends RuntimeException {

    public SelfReactionNotAllowedException() {
        super("User cannot react to themselves");
    }
}
