package com.insuranceapp.backend.exception;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.insuranceapp.backend.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    public static final String INVALID_CREDENTIALS = "Invalid email or password.";
    public static final String ACCOUNT_INACTIVE = "Your account is not active. Please contact customer support.";
    public static final String DATA_CONFLICT =
            "The request conflicts with an existing record. Please refresh and try again.";
    public static final String DATABASE_UNAVAILABLE =
            "We could not process your request due to a database error. Please try again later.";

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS, request);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabled(DisabledException ex, HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, ACCOUNT_INACTIVE, request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(AiServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleAiUnavailable(AiServiceUnavailableException ex,
            HttpServletRequest request) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException ex, HttpServletRequest request) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    @ExceptionHandler({
            DuplicatePolicyException.class,
            DuplicatePaymentException.class,
            DuplicateCustomerException.class
    })
    public ResponseEntity<ErrorResponse> handleDuplicatePolicy(RuntimeException ex,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /** Unique-constraint violations, e.g. a concurrent duplicate registration. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
            HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return error(HttpStatus.CONFLICT, DATA_CONFLICT, request);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccess(DataAccessException ex, HttpServletRequest request) {
        log.error("Database error processing {}", request.getRequestURI(), ex);
        return error(HttpStatus.SERVICE_UNAVAILABLE, DATABASE_UNAVAILABLE, request);
    }

    @ExceptionHandler({ MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class })
    public ResponseEntity<ErrorResponse> handleBadParameter(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Invalid or missing request parameter.", request);
    }

    @ExceptionHandler(InvalidSearchCriteriaException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSearch(InvalidSearchCriteriaException ex,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        // Type conversion failures (e.g. a malformed date query parameter) carry framework messages, so replace them.
        ex.getBindingResult().getFieldErrors()
                .forEach(fieldError -> fieldErrors.putIfAbsent(fieldError.getField(),
                        fieldError.isBindingFailure() ? "Invalid value." : fieldError.getDefaultMessage()));
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new ErrorResponse(Instant.now(clock), status.value(),
                status.getReasonPhrase(), "Validation failed.", request.getRequestURI(), fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Malformed request body.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error processing {}", request.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again later.",
                request);
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(Instant.now(clock), status, message, request.getRequestURI()));
    }
}

