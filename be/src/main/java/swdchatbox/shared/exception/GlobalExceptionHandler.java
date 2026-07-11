package swdchatbox.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import swdchatbox.shared.dto.ApiErrorResponse;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        log.error(
                "Validation error at path={} type={} message={}",
                request.getRequestURI(),
                ex.getClass().getName(),
                ex.getMessage(),
                ex
        );

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }

        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Validation failed")
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        log.error(
                "Unreadable request at path={} type={} message={}",
                request.getRequestURI(),
                ex.getClass().getName(),
                ex.getMessage(),
                ex
        );

        String message = "Invalid request body";
        Throwable cause = ex.getMostSpecificCause();
        if (cause != null && cause.getMessage() != null) {
            String detail = cause.getMessage();
            if (detail.contains("PointsDistributionMode")) {
                message = "pointsDistribution must be EVEN or BY_DIFFICULTY";
            } else if (detail.contains("MultipleChoiceMode")) {
                message = "multipleChoiceMode must be SINGLE or MULTIPLE";
            } else if (detail.contains("QuizStatus")) {
                message = "status must be a valid QuizStatus value";
            } else if (detail.contains("UUID")) {
                message = "Invalid UUID format in request body";
            }
        }

        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(
            BadRequestException ex,
            HttpServletRequest request
    ) {
        log.error(
                "Bad request at path={} type={} message={}",
                request.getRequestURI(),
                ex.getClass().getName(),
                ex.getMessage(),
                ex
        );

        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiErrorResponse> handleAuth(
            AuthException ex,
            HttpServletRequest request
    ) {
        log.error(
                "Auth error at path={} type={} message={}",
                request.getRequestURI(),
                ex.getClass().getName(),
                ex.getMessage(),
                ex
        );

        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error(HttpStatus.UNAUTHORIZED.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request
    ) {
        log.error(
                "Resource not found at path={} type={} message={}",
                request.getRequestURI(),
                ex.getClass().getName(),
                ex.getMessage(),
                ex
        );

        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request
    ) {
        log.error(
                "Upload size exceeded at path={} type={} message={}",
                request.getRequestURI(),
                ex.getClass().getName(),
                ex.getMessage(),
                ex
        );

        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.PAYLOAD_TOO_LARGE.value())
                .error(HttpStatus.PAYLOAD_TOO_LARGE.getReasonPhrase())
                .message(ex.getMessage() != null ? ex.getMessage() : "File is too large. Please upload a smaller file or increase server limits.")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleOther(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error(
                "Unhandled exception at path={} type={} message={}",
                request.getRequestURI(),
                ex.getClass().getName(),
                ex.getMessage(),
                ex
        );

        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .message("Internal server error")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}

