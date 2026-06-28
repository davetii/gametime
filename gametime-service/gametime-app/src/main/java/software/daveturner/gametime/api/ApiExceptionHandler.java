package software.daveturner.gametime.api;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.*;
import software.daveturner.gametime.exception.*;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    protected ResponseEntity<Object> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(ResourceConflictException.class)
    protected ResponseEntity<Object> handleResourceConflict(ResourceConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(ResourceBadRequestException.class)
    protected ResponseEntity<Object> handleBadRequest(ResourceBadRequestException ex) {
        return ResponseEntity.badRequest().build();
    }
}
