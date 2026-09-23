package ru.andrewb.charm.api.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.andrewb.charm.api.reaction.SelfReactionNotAllowedException;
import ru.andrewb.charm.api.security.refresh.InvalidRefreshTokenException;
import ru.andrewb.charm.api.storage.FileStorageException;
import ru.andrewb.charm.api.user.admin.SelfAccountStatusChangeNotAllowedException;
import ru.andrewb.charm.api.user.admin.AdminAccountModificationNotAllowedException;
import ru.andrewb.charm.api.user.account.AccountBlockedException;
import ru.andrewb.charm.api.user.photo.InvalidPhotoException;
import ru.andrewb.charm.api.user.photo.PhotoLimitExceededException;
import ru.andrewb.charm.api.user.photo.UserPhotoNotFoundException;
import ru.andrewb.charm.api.user.registration.EmailAlreadyExistsException;
import ru.andrewb.charm.api.user.profile.UserNotFoundException;
import ru.andrewb.charm.api.user.profile.ProfileIncompleteException;
import ru.andrewb.charm.api.user.profile.UserVersionConflictException;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ProblemDetail> handleEmailAlreadyExists(
            EmailAlreadyExistsException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );
        problem.setTitle("Email already exists");

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        List<Map<String, String>> errors = exception
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", String.valueOf(error.getDefaultMessage())
                ))
                .toList();

        ProblemDetail problem = ProblemDetail.forStatus(
                HttpStatus.BAD_REQUEST
        );
        problem.setTitle("Request validation failed");
        problem.setDetail("One or more request fields are invalid");
        problem.setProperty("errors", errors);

        return ResponseEntity
                .badRequest()
                .body(problem);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleUserNotFound(
            UserNotFoundException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );
        problem.setTitle("User not found");

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(problem);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleBadCredentials(
            BadCredentialsException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                exception.getMessage()
        );
        problem.setTitle("Authentication failed");

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(problem);
    }

    @ExceptionHandler(UserVersionConflictException.class)
    public ResponseEntity<ProblemDetail> handleVersionConflict(
            UserVersionConflictException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );
        problem.setTitle("User version conflict");

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(problem);
    }

    @ExceptionHandler(SelfReactionNotAllowedException.class)
    public ResponseEntity<ProblemDetail> handleSelfReaction(
            SelfReactionNotAllowedException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                exception.getMessage()
        );

        problem.setTitle("Self-reaction is not allowed");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(problem);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ProblemDetail> handleInvalidRefreshToken(
            InvalidRefreshTokenException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                exception.getMessage()
        );
        problem.setTitle("Refresh token rejected");

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(problem);
    }

    @ExceptionHandler(InvalidPhotoException.class)
    public ResponseEntity<ProblemDetail> handleInvalidPhoto(
            InvalidPhotoException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                exception.getMessage()
        );
        problem.setTitle("Invalid photo");

        return ResponseEntity
                .badRequest()
                .body(problem);
    }

    @ExceptionHandler(PhotoLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handlePhotoLimitExceeded(
            PhotoLimitExceededException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );
        problem.setTitle("Photo limit exceeded");

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(problem);
    }

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ProblemDetail> handleFileStorage(
            FileStorageException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "File storage is temporarily unavailable"
        );
        problem.setTitle("File storage unavailable");

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(problem);
    }

    @ExceptionHandler(UserPhotoNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleUserPhotoNotFound(
            UserPhotoNotFoundException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );
        problem.setTitle("Photo not found");

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(problem);
    }

    @ExceptionHandler(SelfAccountStatusChangeNotAllowedException.class)
    public ResponseEntity<ProblemDetail> handleSelfAccountStatusChange(
            SelfAccountStatusChangeNotAllowedException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );
        problem.setTitle("Self account status change is not allowed");

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(problem);
    }

    @ExceptionHandler(AdminAccountModificationNotAllowedException.class)
    public ResponseEntity<ProblemDetail> handleAdminAccountModification(
            AdminAccountModificationNotAllowedException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                exception.getMessage()
        );
        problem.setTitle("Administrator account modification is not allowed");

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(problem);
    }

    @ExceptionHandler(AccountBlockedException.class)
    public ResponseEntity<ProblemDetail> handleAccountBlocked(
            AccountBlockedException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                exception.getMessage()
        );
        problem.setTitle("Account is blocked");

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(problem);
    }

    @ExceptionHandler(ProfileIncompleteException.class)
    public ResponseEntity<ProblemDetail> handleProfileIncomplete(
            ProfileIncompleteException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );
        problem.setTitle("Profile is incomplete");

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(problem);
    }

}
