package io.veridraw.drawcore.dto;

/**
 * Запрос к API random.org для генерации случайных чисел (формат JSON-RPC).
 * Используется только внутренне в {@link io.veridraw.drawcore.service.RandomNumberService}.
 */
public record RandomOrgRequest(String jsonrpc, String method, Params params, int id) {

    /**
     * Параметры запроса к random.org.
     *
     * @param apiKey ключ API
     * @param n количество запрашиваемых чисел
     * @param min минимальное значение
     * @param max максимальное значение
     * @param replacement флаг, допускающий повторения чисел
     */
    public record Params(String apiKey, int n, int min, int max, boolean replacement) {}
}
