package com.jflop.server.admin;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 9/24/16
 */
public class ValidationException extends Exception {

    private String error;
    private String message;

    public ValidationException(String error, String message) {
        this.error = error;
        this.message = message;
    }

    public String toResponseBody() {
        return "{\"error\": \"" + error + "\", \"message\": \"" + message + "\"}";
    }
}
