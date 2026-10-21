package io.veridraw.drawcore.dto;

import java.util.List;

/**
 * Ответ от API random.org (формат JSON-RPC).
 * Используется только внутренне в {@link io.veridraw.drawcore.service.RandomNumberService}.
 *
 * @param result результат выполнения запроса
 * @param error информация об ошибке, если запрос не удался
 */
public record RandomOrgResponse(Result result, ErrorInfo error) {

    /**
     * Обертка для данных результата.
     */
    public record Result(RandomData random) {}

    /**
     * Сгенерированные случайные данные.
     *
     * @param data массив случайных чисел
     */
    public record RandomData(List<Integer> data) {}

    /**
     * Информация об ошибке API.
     *
     * @param code код ошибки
     * @param message сообщение об ошибке
     */
    public record ErrorInfo(int code, String message) {}
}
