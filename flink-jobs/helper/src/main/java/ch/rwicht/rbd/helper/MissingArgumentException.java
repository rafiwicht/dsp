package ch.rwicht.rbd.helper;

public class MissingArgumentException extends Exception {
    public MissingArgumentException(String errorMessage) {
        super(errorMessage);
    }
}
