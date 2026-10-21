package io.veridraw.drawcore.exception;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.Map;
import javax.management.InstanceAlreadyExistsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Глобальный обработчик исключений для всех контроллеров приложения.
 * Преобразует исключения в структурированные HTTP-ответы с понятными сообщениями.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Обрабатывает исключение {@link InstanceAlreadyExistsException}, возникающее при попытке
     * создать сущность, которая уже существует в системе.
     *
     * @param e перехваченное исключение
     * @return HTTP-ответ со статусом 409 CONFLICT и сообщением об ошибке
     */
    @ExceptionHandler(InstanceAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @ApiResponse(
            responseCode = "409",
            description = "Конфликт: сущность уже существует",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples =
                                    @ExampleObject(
                                            value = "{\"error\": \"CONFLICT\", \"message\": \"Draw already exists\"}")))
    public ResponseEntity<Map<String, String>> handleAlreadyExists(InstanceAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "CONFLICT", "message", e.getMessage()));
    }

    /**
     * Внутренняя модель для документирования структуры ответа об ошибке в Swagger.
     */
    private record ErrorResponse(String error, String message) {}
}
