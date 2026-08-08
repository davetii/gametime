package software.daveturner.gametime.api;

import org.junit.jupiter.api.*;
import org.springframework.http.*;
import software.daveturner.gametime.exception.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Direct unit coverage of the exception→status mappings. The bodies are trivial
 * one-liners, but invoking them here pins the contract (and the §3.6 addition:
 * ResourceUnprocessableException → 422 with an empty body, #024 F).
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void resourceNotFoundMapsTo404EmptyBody() {
        ResponseEntity<Object> r = handler.handleResourceNotFound(new ResourceNotFoundException());
        assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
        assertNull(r.getBody());
    }

    @Test
    void resourceConflictMapsTo409EmptyBody() {
        ResponseEntity<Object> r = handler.handleResourceConflict(
                new ResourceConflictException("dup"));
        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
        assertNull(r.getBody());
    }

    @Test
    void badRequestMapsTo400EmptyBody() {
        ResponseEntity<Object> r = handler.handleBadRequest(
                new ResourceBadRequestException("bad"));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertNull(r.getBody());
    }

    @Test
    void unprocessableMapsTo422EmptyBody() {
        ResponseEntity<Object> r = handler.handleUnprocessable(
                new ResourceUnprocessableException());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, r.getStatusCode());
        assertNull(r.getBody());
    }
}
