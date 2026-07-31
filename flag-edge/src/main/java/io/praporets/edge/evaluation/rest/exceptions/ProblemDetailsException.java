package io.praporets.edge.evaluation.rest.exceptions;


public abstract class ProblemDetailsException extends RuntimeException {
    public final int statusCode;
    public final String title;
    public final String detail;

    protected ProblemDetailsException(int statusCode, String message, String detail) {
        super(message);
        this.statusCode = statusCode;
        this.title = message;
        this.detail = detail;
    }
}
