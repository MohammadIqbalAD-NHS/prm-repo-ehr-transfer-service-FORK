package uk.nhs.prm.repo.ehrtransferservice.exceptions;

public class HttpException extends Exception {
    public HttpException(String message, Exception cause) {
        super(message, cause);
    }

    public HttpException() {
        super();
    }

    public HttpException(String message) {
        super(message);
    }
}