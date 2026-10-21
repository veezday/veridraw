package io.veridraw.drawcore.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veridraw.drawcore.domain.Draw;
import io.veridraw.drawcore.dto.CreateDrawRequest;
import io.veridraw.drawcore.dto.DrawResponse;
import io.veridraw.drawcore.service.DrawService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class DrawControllerTest {

    @Mock
    private DrawService drawService;

    @InjectMocks
    private DrawController drawController;

    private final UUID drawId = UUID.randomUUID();
    private final CreateDrawRequest createRequest = mock(CreateDrawRequest.class);
    private final DrawResponse drawResponse = mock(DrawResponse.class);

    @Test
    void createDraw_WhenValidRequest_ShouldReturnCreated() {
        when(drawService.createDraw(createRequest)).thenReturn(Mono.just(mock(Draw.class))); // Заглушка для сущности

        ResponseEntity<DrawResponse> response =
                drawController.createDraw(createRequest).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        verify(drawService, times(1)).createDraw(createRequest);
    }

    @Test
    void getAllDraws_WhenDrawsExist_ShouldReturnFluxWithItems() {
        when(drawService.getAllDraws()).thenReturn(Flux.just(mock(Draw.class)));

        List<DrawResponse> result = drawController.getAllDraws().collectList().block();

        assertThat(result).hasSize(1);
        verify(drawService, times(1)).getAllDraws();
    }

    @Test
    void getDrawById_WhenFound_ShouldReturnOk() {
        when(drawService.getDrawById(drawId)).thenReturn(Mono.just(mock(Draw.class)));

        ResponseEntity<DrawResponse> response =
                drawController.getDrawById(drawId).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(drawService, times(1)).getDrawById(drawId);
    }

    @Test
    void getDrawById_WhenNotFound_ShouldReturnNotFound() {
        when(drawService.getDrawById(drawId)).thenReturn(Mono.empty());

        ResponseEntity<DrawResponse> response =
                drawController.getDrawById(drawId).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(drawService, times(1)).getDrawById(drawId);
    }

    @Test
    void activateDraw_WhenSuccess_ShouldReturnOk() {
        when(drawService.activateDraw(drawId)).thenReturn(Mono.just(mock(Draw.class)));

        ResponseEntity<DrawResponse> response =
                drawController.activateDraw(drawId).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(drawService, times(1)).activateDraw(drawId);
    }

    @Test
    void activateDraw_WhenError_ShouldReturnBadRequest() {
        when(drawService.activateDraw(drawId)).thenReturn(Mono.error(new RuntimeException("Error")));

        ResponseEntity<DrawResponse> response =
                drawController.activateDraw(drawId).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(drawService, times(1)).activateDraw(drawId);
    }

    @Test
    void completeDraw_WhenSuccess_ShouldReturnOk() {
        when(drawService.completeDraw(drawId)).thenReturn(Mono.just(mock(Draw.class)));

        ResponseEntity<DrawResponse> response =
                drawController.completeDraw(drawId).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(drawService, times(1)).completeDraw(drawId);
    }

    @Test
    void completeDraw_WhenError_ShouldReturnBadRequest() {
        when(drawService.completeDraw(drawId)).thenReturn(Mono.error(new RuntimeException("Ошибка завершения")));

        ResponseEntity<DrawResponse> response =
                drawController.completeDraw(drawId).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(drawService, times(1)).completeDraw(drawId);
    }

    @Test
    void cancelDraw_WhenSuccess_ShouldReturnOk() {
        when(drawService.cancelDraw(drawId)).thenReturn(Mono.just(mock(Draw.class)));

        ResponseEntity<DrawResponse> response =
                drawController.cancelDraw(drawId).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(drawService, times(1)).cancelDraw(drawId);
    }

    @Test
    void cancelDraw_WhenError_ShouldReturnBadRequest() {
        when(drawService.cancelDraw(drawId)).thenReturn(Mono.error(new RuntimeException("Ошибка отмены")));

        ResponseEntity<DrawResponse> response =
                drawController.cancelDraw(drawId).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(drawService, times(1)).cancelDraw(drawId);
    }
}
