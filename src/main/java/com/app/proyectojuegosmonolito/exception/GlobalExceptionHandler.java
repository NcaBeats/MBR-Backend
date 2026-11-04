package com.app.proyectojuegosmonolito.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleNotFound(EntityNotFoundException ex) {
        log.warn("Entity not found: {}", sanitize(ex.getMessage()));
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Resource not found.");
        problem.setTitle("Not Found");
        return problem;
    }

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex) {
        log.warn("Business rule violation: {}", sanitize(ex.getMessage()));
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad Request");
        problem.setProperty("code", ex.getCode());
        return problem;
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingHeader(MissingRequestHeaderException ex) {
        log.warn("Missing request header: {}", ex.getHeaderName());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Bad request.");
        problem.setTitle("Bad Request");
        return problem;
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        log.warn("Unsupported media type: {}", ex.getContentType());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type.");
        problem.setTitle("Unsupported Media Type");
        return problem;
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ProblemDetail handleMissingPart(Exception ex) {
        log.warn("Invalid multipart request: {}", sanitize(ex.getMessage()));
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid multipart request.");
        problem.setTitle("Bad Request");
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        log.warn("Validation error: {}", errors);
        var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation Error");
        problem.setDetail("Invalid request content.");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        log.warn("Authentication failed");
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        problem.setTitle("Unauthorized");
        return problem;
    }

    @ExceptionHandler(InvalidGoogleTokenException.class)
    public ProblemDetail handleInvalidGoogleToken(InvalidGoogleTokenException ex) {
        log.warn("Invalid Google ID token: {}", sanitize(ex.getMessage()));
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid Google credentials.");
        problem.setTitle("Unauthorized");
        return problem;
    }

    @ExceptionHandler(InvalidOnboardingTokenException.class)
    public ProblemDetail handleInvalidOnboardingToken(InvalidOnboardingTokenException ex) {
        log.warn("Invalid onboarding token: {}", sanitize(ex.getMessage()));
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "Invalid or expired onboarding token.");
        problem.setTitle("Unauthorized");
        return problem;
    }

    @ExceptionHandler(EmailDomainNotAllowedException.class)
    public ProblemDetail handleEmailDomainNotAllowed(EmailDomainNotAllowedException ex) {
        log.warn("Rejected OAuth signup from a non-allowed email domain");
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "Email domain not allowed.");
        problem.setTitle("Forbidden");
        problem.setProperty("code", BusinessException.EMAIL_DOMAIN_NOT_ALLOWED);
        return problem;
    }

    @ExceptionHandler(GoogleSignInNotConfiguredException.class)
    public ProblemDetail handleGoogleSignInNotConfigured(GoogleSignInNotConfiguredException ex) {
        log.warn("Google sign-in endpoint called without app.oauth.google.client-id");
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Google sign-in is not configured on this server.");
        problem.setTitle("Service Unavailable");
        problem.setProperty("code", BusinessException.GOOGLE_SIGN_IN_NOT_CONFIGURED);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed request body.");
        problem.setTitle("Bad Request");
        return problem;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch for parameter {}: {}", ex.getName(), sanitize(String.valueOf(ex.getValue())));
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid argument type.");
        problem.setTitle("Bad Request");
        return problem;
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not supported: {}", ex.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed.");
        problem.setTitle("Method Not Allowed");
        return problem;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(NoResourceFoundException ex) {
        log.warn("Resource not found: {}", ex.getResourcePath());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Resource not found.");
        problem.setTitle("Not Found");
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        log.warn("Bad request: {}", sanitize(ex.getMessage()));
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Bad request.");
        problem.setTitle("Bad Request");
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Database constraint violation.");
        problem.setTitle("Conflict");
        return problem;
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ProblemDetail handleOptimisticLock(Exception ex) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Concurrent modification detected. Please retry.");
        problem.setTitle("Conflict");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
        problem.setTitle("Internal Server Error");
        return problem;
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "null";
        }
        return value.replaceAll("[\\r\\n]", " ");
    }
}
