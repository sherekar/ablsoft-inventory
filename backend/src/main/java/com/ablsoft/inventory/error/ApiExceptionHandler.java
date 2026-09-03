package com.ablsoft.inventory.error;

import com.ablsoft.inventory.spreadsheet.ImportValidationException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ImportValidationException.class)
    ProblemDetail validation(ImportValidationException error) {
        var problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Import validation failed", error.getMessage());
        problem.setProperty("errors", error.getErrors());
        problem.setProperty("totalErrors", error.getTotalErrors());
        return problem;
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail invalid(InvalidRequestException error) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", error.getMessage());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestPartException.class})
    ProblemDetail malformed(Exception error) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "Check the request parameters and include a file for imports.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail tooLarge(MaxUploadSizeExceededException error) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "File too large", "The maximum workbook size is 5 MB.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(DataIntegrityViolationException error) {
        return problem(HttpStatus.CONFLICT, "Import conflict",
            "An inventory entry conflicts with existing data. Another import may have added the same SKU and purchase date. No rows were saved.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception error, HttpServletRequest request) {
        if (error instanceof ErrorResponse response) {
            return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders()).body(response.getBody());
        }
        log.error("Request failed: {} {}", request.getMethod(), request.getRequestURI(), error);
        return ResponseEntity.internalServerError().body(problem(HttpStatus.INTERNAL_SERVER_ERROR,
            "Unexpected error", "The request could not be completed. Please try again."));
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("about:blank"));
        return problem;
    }
}
