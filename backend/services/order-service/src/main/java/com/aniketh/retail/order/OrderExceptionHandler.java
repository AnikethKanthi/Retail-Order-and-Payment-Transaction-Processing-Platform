package com.aniketh.retail.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.sql.SQLException;

@RestControllerAdvice
class OrderExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderExceptionHandler.class);

    @ExceptionHandler(OrderException.class)
    ProblemDetail handleOrderError(OrderException exception) {
        return ProblemDetail.forStatusAndDetail(exception.status, exception.getMessage());
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail handleDatabaseError(DataAccessException exception) {
        String sqlState = exception.getMostSpecificCause() instanceof SQLException cause
                ? cause.getSQLState() : "unavailable";
        LOGGER.error("Order database operation failed: category={}, SQLState={}",
                exception.getClass().getSimpleName(), sqlState);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "The order operation could not be completed.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleBodyValidation(MethodArgumentNotValidException exception) {
        var problem = badRequest("The order request contains invalid fields.");
        problem.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList());
        return problem;
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail handleMethodValidation(HandlerMethodValidationException exception) {
        var problem = badRequest("One or more request values are invalid.");
        problem.setProperty("errors", exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream().map(error -> {
                    if (error instanceof FieldError fieldError) {
                        return new FieldViolation(fieldError.getField(), fieldError.getDefaultMessage());
                    }
                    var header = result.getMethodParameter()
                            .getParameterAnnotation(RequestHeader.class);
                    String field = header == null ? result.getMethodParameter().getParameterName() : header.value();
                    return new FieldViolation(field, error.getDefaultMessage());
                }))
                .toList());
        return problem;
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ProblemDetail handleMissingHeader(MissingRequestHeaderException exception) {
        return badRequest("Missing required header " + exception.getHeaderName() + ".");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableBody() {
        return badRequest("The request body must be valid JSON with correctly typed fields.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleInvalidParameter(MethodArgumentTypeMismatchException exception) {
        return badRequest("Invalid value for " + exception.getName() + ".");
    }

    private ProblemDetail badRequest(String detail) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    private record FieldViolation(String field, String message) {
    }
}
