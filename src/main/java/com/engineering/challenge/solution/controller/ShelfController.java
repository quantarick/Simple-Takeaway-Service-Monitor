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

    @GetMapping(value = "/{shelf-type}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ShelfDTO>> getShelf(@PathVariable("shelf-type") String st) {
        ShelfType shelfType = ShelfType.fromString(st);

        Flux<ServerSentEvent<ShelfDTO>> heartbeatStream = Flux.interval(Duration.ofSeconds(10)).map(
            tick -> toServerSentEvent(shelfService.getShelfSnapshot(shelfType))
        );

        return shelfService.getEventPublisher()
            .map(stringServerSentEvent -> toShelfDTO(stringServerSentEvent.data()))
            .filter(match -> match != null)
            .filter(match -> match.getType().equals(shelfType))
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
