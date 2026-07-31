package io.praporets.edge.evaluation.rest.exceptions;

public class EnvironmentNotLoadedException extends ProblemDetailsException {
    public EnvironmentNotLoadedException(String message) {
        super(503, "Service Unavailable", message);
    }
}
