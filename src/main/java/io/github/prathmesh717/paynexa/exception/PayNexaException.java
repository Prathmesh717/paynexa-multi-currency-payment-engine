package io.github.prathmesh717.paynexa.exception;

public abstract class PayNexaException extends RuntimeException {
    protected PayNexaException(String message) {
        super(message);
    }
}
