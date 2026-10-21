package io.veridraw.drawcore.domain;

/**
 * Статус жизненного цикла розыгрыша.
 */
public enum DrawStatus {
    /** Создан, но ещё не активирован. */
    CREATED,
    /** Активирован, идёт приём билетов. */
    ACTIVATED,
    /** Завершён, определён победитель. */
    COMPLETED,
    /** Отменён. */
    CANCELLED,
}
