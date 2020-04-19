package com.engineering.challenge.solution.controller;

import com.engineering.challenge.solution.domain.ShelfType;
import com.engineering.challenge.solution.domain.dto.ShelfDTO;
import com.engineering.challenge.solution.services.ShelfService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.Duration;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RequestMapping("/shelves")
@Controller
@RequiredArgsConstructor
public class ShelfController {

    private static Logger logger = LoggerFactory.getLogger(ShelfController.class);

    private final ShelfService shelfService;

    private final ObjectMapper objectMapper;

    /**
     * SSE API to get shelf status, new data will be streamed when there's add/remove on orders.
     * @param st, the shelf type, available values are 'hot', 'cold', 'frozen', 'overflow'
     * @return
     */
    @GetMapping(value = "/{shelf-type}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ShelfDTO>> getShelf(@PathVariable("shelf-type") String st) {
        ShelfType shelfType = ShelfType.fromString(st);

        // Note, if need to refresh periodically, then remove take(1) and increase the interval.
        Flux<ServerSentEvent<ShelfDTO>> heartbeatStream = Flux.interval(Duration.ofMillis(10)).map(
            tick -> toServerSentEvent(shelfService.getShelfSnapshot(shelfType))
        ).take(1);

        return shelfService.getEventPublisher()
            .map(stringServerSentEvent -> toShelfDTO(stringServerSentEvent.data()))
            .filter(shelf -> shelf != null)
            .filter(shelf -> shelf.getType().equals(shelfType))
            .map(this::toServerSentEvent)
            .mergeWith(heartbeatStream);
    }

    private ShelfDTO toShelfDTO(String json) {
        ShelfDTO shelf;
        try {
            shelf = objectMapper.readValue(json, ShelfDTO.class);
        } catch (Exception ex) {
            logger.error("parsing exception", ex);
            return null;
        }
        return shelf;
    }

    private ServerSentEvent<ShelfDTO> toServerSentEvent(ShelfDTO shelf) {
        return ServerSentEvent.<ShelfDTO>builder()
            .data(shelf)
            .build();
    }
}
