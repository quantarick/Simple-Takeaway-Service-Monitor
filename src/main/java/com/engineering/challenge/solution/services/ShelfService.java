package com.engineering.challenge.solution.services;


import com.engineering.challenge.solution.domain.ShelfType;
import com.engineering.challenge.solution.domain.dto.OrderDTO;
import com.engineering.challenge.solution.domain.dto.ShelfDTO;
import com.engineering.challenge.solution.domain.entities.Order;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.modelmapper.ModelMapper;
import org.redisson.api.RLock;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

@Service
@RequiredArgsConstructor
public class ShelfService {

    private final KafkaReceiver<String, String> kafkaReceiver;

    private final RedissonClient redissonClient;

    @Value("${order-app.shelf-change-event-topic-name}")
    private String topicName;

    private ConnectableFlux<ServerSentEvent<String>> eventPublisher;

    private final KafkaSender<String, String> kafkaSender;

    private final ObjectMapper objectMapper;

    private final ModelMapper modelMapper = new ModelMapper();

    @PostConstruct
    public void init() {
        eventPublisher = kafkaReceiver.receive()
            .map(consumerRecord -> ServerSentEvent.builder(consumerRecord.value()).build())
            .publish();

        // subscribes to the KafkaReceiver -> starts consumption (without observers attached)
        eventPublisher.connect();
    }

    public ConnectableFlux<ServerSentEvent<String>> getEventPublisher() {
        return eventPublisher;
    }

    public void onShelfChange(ShelfType shelfType) {
        ShelfDTO shelfSnapshot = getShelfSnapshot(shelfType);
        kafkaSender.send(
            Mono.just(
                toSenderRecord(shelfSnapshot)
            ))
            .next()
            .log()
            .map(longSenderResult -> longSenderResult.exception() == null);
    }

    public ShelfDTO getShelfSnapshot(ShelfType shelfType) {
        return ShelfDTO
            .builder()
            .type(shelfType)
            .orders(peekOrdersOnShelf(shelfType))
            .build();
    }

    /**
     * Return the snapshot of the shelf
     *
     * @return list of orders on the shelf
     */
    private List<OrderDTO> peekOrdersOnShelf(ShelfType shelfType) {
        // lock the target shelf.
        RLock shelfLock = redissonClient.getReadWriteLock(shelfType.toString() + "_lock").readLock();
        RMapCache<Long, Order> shelf = redissonClient.getMapCache(shelfType.toString());
        shelfLock.lock();
        try {
            return shelf.values().stream().map(o -> modelMapper.map(o, OrderDTO.class)).collect(Collectors.toList());
        } finally {
            shelfLock.unlock();
        }
    }

    private SenderRecord<String, String, String> toSenderRecord(ShelfDTO shelf) {
        final String matchJsonStr;
        try {
            matchJsonStr = objectMapper.writeValueAsString(shelf);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return SenderRecord.create(new ProducerRecord<String, String>(topicName, matchJsonStr), shelf.getType().toString());
    }


}
