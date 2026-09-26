package ru.dta.check.api;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.ErrorResponse;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidCheckRequestException.class)
    public ResponseEntity<ApiErrorResponse> invalidRequest(InvalidCheckRequestException exception) {
        return ResponseEntity.status(422).body(new ApiErrorResponse("validation_error",
                exception.getMessage(), exception.getFieldErrors()));
    }

    @ExceptionHandler(CheckNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> notFound(CheckNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "check_not_found", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> invalidQuery(IllegalArgumentException exception) {
        return error(HttpStatus.UNPROCESSABLE_CONTENT, "validation_error", exception.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> tooLarge() {
        return error(HttpStatus.CONTENT_TOO_LARGE, "payload_too_large", "Превышен технический лимит загрузки.");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiErrorResponse> malformedMultipart() {
        return error(HttpStatus.BAD_REQUEST, "invalid_multipart", "Не удалось прочитать multipart-запрос.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> unsupportedMediaType() {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type", "Ожидается multipart/form-data.");
    }

    @ExceptionHandler({DataAccessResourceFailureException.class, CannotCreateTransactionException.class})
    public ResponseEntity<ApiErrorResponse> databaseUnavailable(Exception exception) {
        LOG.warn("Database unavailable: {}", exception.getClass().getName());
        return error(HttpStatus.SERVICE_UNAVAILABLE, "database_unavailable", "База данных временно недоступна.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> unexpectedFailure(Exception exception) {
        if (exception instanceof ErrorResponse response) {
            return ResponseEntity.status(response.getStatusCode()).body(new ApiErrorResponse(
                    "http_error", "Запрос не может быть обработан.", List.of()));
        }
        LOG.error("Unexpected check failure: {}", exception.getClass().getName());
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Внутренняя ошибка сервиса.");
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message, List.of()));
    }
}
