package com.inventorysystem.Model;

/**
 * Generic {success, message} response for actions that don't need to
 * return a full object, e.g. approving or rejecting a disposal request.
 */
public class GenericResponse {

    private boolean success;
    private String message;

    public GenericResponse() { }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}