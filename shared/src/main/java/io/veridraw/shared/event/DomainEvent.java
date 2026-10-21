package io.veridraw.shared.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Базовый интерфейс для всех доменных событий приложения.
 * <p>
 * Использует полиморфную сериализацию Jackson: при преобразовании в JSON
 * добавляется поле {@code type}, содержащее имя конкретного типа события,
 * что позволяет корректно десериализовать событие на стороне потребителя.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(@JsonSubTypes.Type(value = DrawCompletedEvent.class, name = "DrawCompleted"))
public interface DomainEvent {}
