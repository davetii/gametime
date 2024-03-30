package software.daveturner.gametime.api;

import jakarta.persistence.*;
import org.springdoc.api.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.*;
import org.springframework.web.server.*;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.*;

@EnableWebMvc
@ControllerAdvice
public class ExceptionHandlerController  extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus(value = HttpStatus.NOT_FOUND)
    public ErrorMessage resourceNotFoundException(ResponseStatusException ex, WebRequest request) {
        System.out.println("************** I am here2 ***********");
        ErrorMessage message = new ErrorMessage("");
        return message;
    }

    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(NoHandlerFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        System.out.println("************** I am here ***********");
        return super.handleNoHandlerFoundException(ex, headers, status, request);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    protected ResponseEntity<Object> handleEntityNotFound(EntityNotFoundException ex) {
        System.out.println("sdid we ever gfet here");
        return ResponseEntity.notFound().build();
    }
}
