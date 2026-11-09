package com.minipay.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;

import com.minipay.common.exception.BalanceLimitExceededException;
import com.minipay.common.exception.EmailAlreadyExistsException;
import com.minipay.common.exception.InvalidCredentialsException;
import com.minipay.common.exception.IdempotencyConflictException;
import com.minipay.common.exception.InsufficientBalanceException;
import com.minipay.common.exception.InvalidAmountException;
import com.minipay.common.exception.InvalidDateRangeException;
import com.minipay.common.exception.InvalidPageRequestException;
import com.minipay.common.exception.InvalidRefreshTokenException;
import com.minipay.common.exception.SelfTransferException;
import com.minipay.common.exception.TransactionNotFoundException;
import com.minipay.common.exception.UserNotFoundException;
import com.minipay.common.exception.WalletAlreadyExistsException;
import com.minipay.common.exception.WalletNotFoundException;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleInvalidCredentials(
            InvalidCredentialsException exception, HttpServletRequest request) {
        return new ErrorResponse(HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(InvalidPageRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidPageRequest(InvalidPageRequestException exception, HttpServletRequest request) {
        return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),
                exception.getMessage(), request.getRequestURI());
    }

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // User not found
    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleUserNotFound(
            UserNotFoundException exception,
            HttpServletRequest request) {
        return new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                exception.getMessage(),
                request.getRequestURI());
    }

    // Email already exists
    @ExceptionHandler(EmailAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleEmailAlreadyExists(
            EmailAlreadyExistsException exception,
            HttpServletRequest request) {
        return new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                exception.getMessage(),
                request.getRequestURI());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && "23505".equals(violation.getSQLState())
                    && "users_email_key".equals(violation.getConstraintName())) {
                ErrorResponse body = new ErrorResponse(
                        HttpStatus.CONFLICT.value(),
                        HttpStatus.CONFLICT.getReasonPhrase(),
                        "Email already registered",
                        request.getRequestURI());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
            }
            cause = cause.getCause();
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(handleUnexpectedException(exception, request));
    }

    // Wallet not found
    @ExceptionHandler(WalletNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleWalletNotFound(
            WalletNotFoundException exception,
            HttpServletRequest request) {
        return new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                exception.getMessage(),
                request.getRequestURI());
    }

    // Wallet already exists
    @ExceptionHandler(WalletAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleWalletAlreadyExists(
            WalletAlreadyExistsException exception,
            HttpServletRequest request) {
        return new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                exception.getMessage(),
                request.getRequestURI());
    }

    // Self transfer exception
    @ExceptionHandler(SelfTransferException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleSelfTransferException(
            SelfTransferException exception,
            HttpServletRequest request) {
        return new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                exception.getMessage(),
                request.getRequestURI());
    }

    // Insufficient balance exception
    @ExceptionHandler(InsufficientBalanceException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleInsufficientBalanceException(
            InsufficientBalanceException exception,
            HttpServletRequest request) {
        return new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                exception.getMessage(),
                request.getRequestURI());
    }

    @ExceptionHandler(BalanceLimitExceededException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleBalanceLimitExceededException(
            BalanceLimitExceededException exception,
            HttpServletRequest request) {
        return new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                exception.getMessage(),
                request.getRequestURI());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Invalid request body",
                request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        return new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Invalid request parameter",
                request.getRequestURI());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                HttpStatus.METHOD_NOT_ALLOWED.getReasonPhrase(),
                "HTTP method is not supported",
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(exception.getHeaders())
                .body(body);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE.getReasonPhrase(),
                "Content type is not supported",
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .headers(exception.getHeaders())
                .body(body);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleResourceNotFound(
            NoResourceFoundException exception,
            HttpServletRequest request) {
        return new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                "Endpoint not found",
                request.getRequestURI());
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDenied(HttpServletRequest request) {
        return new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "Access denied",
                request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleUnexpectedException(
            Exception exception,
            HttpServletRequest request) {
        log.error("Unexpected server error", exception);

        return new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "Unexpected server error",
                request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Validation failed");

        return new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                message,
                request.getRequestURI());
    }

    @ExceptionHandler(InvalidAmountException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidAmountException(
            InvalidAmountException exception,
            HttpServletRequest request) {
        return new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                exception.getMessage(),
                request.getRequestURI());
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handlePessimisticLockingFailure(
            PessimisticLockingFailureException exception,
            HttpServletRequest request) {

        return new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                "Operation conflicted with another request. Please try again.",
                request.getRequestURI());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMissingRequestHeaderException(
            MissingRequestHeaderException exception,
            HttpServletRequest request) {
        return new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Missing required header: " + exception.getHeaderName(),
                request.getRequestURI());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleIdempotencyConflictException(
            IdempotencyConflictException exception,
            HttpServletRequest request) {

        return new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                exception.getMessage(),
                request.getRequestURI());
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleTransactionNotFoundException(
            TransactionNotFoundException exception,
            HttpServletRequest request) {

        return new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                exception.getMessage(),
                request.getRequestURI());
    }

    @ExceptionHandler(InvalidDateRangeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidDateRangeException(
            InvalidDateRangeException exception,
            HttpServletRequest request) {
        return new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                exception.getMessage(),
                request.getRequestURI());
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleInvalidRefreshToken(
        InvalidRefreshTokenException exception,
        HttpServletRequest request
    ) {
        return new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), 
        HttpStatus.UNAUTHORIZED.getReasonPhrase(), 
        exception.getMessage(), 
        request.getRequestURI());
    }
}
