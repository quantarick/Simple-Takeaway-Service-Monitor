package com.engineering.challenge.solution.services;

import com.engineering.challenge.solution.domain.ShelfType;
import com.engineering.challenge.solution.domain.dto.OrderDTO;
import com.engineering.challenge.solution.domain.entities.Order;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.modelmapper.ModelMapper;
import org.redisson.api.RLock;
import org.redisson.api.RMapCache;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.api.map.event.EntryEvent;
import org.redisson.api.map.event.EntryExpiredListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderEventService {

    private static Logger logger = LoggerFactory.getLogger(OrderEventService.class);

    private final OrderService orderService;

    private final DeliveryService deliveryService;

    private final RedissonClient redissonClient;

    private final RMapCacheManager rMapCacheManager;

    private final ShelfService shelfService;

    private final KafkaTemplate<Long, Object> kafkaTemplate;

    @Value("${order-app.order-event-topic-name}")
    private String topicName;

    private Map<RMapCache, List<Integer>> shelfEventListeners = new HashMap<>();

    private final ModelMapper mapper = new ModelMapper();

    public void placeNewOrder(OrderDTO newOrder) {
        Order order = mapper.map(newOrder, Order.class);
        RMapCache<Long, String> orderStatus = rMapCacheManager.getCache("order_status");
        orderStatus.put(order.getIdentifier(), ShelfType.WAITING.toString());
        kafkaTemplate.send(topicName, order.getIdentifier(), order);
    }

    @PostConstruct
    public void init() {
        Arrays.stream(ShelfType.values()).forEach(
            st -> {
                RMapCache shelf = rMapCacheManager.getCache(st.toString());
                registerExpiredHandler(shelf);
            }
        );
    }

    @PreDestroy
    public void cleanup() {
        shelfEventListeners.entrySet().stream().forEach(
            e -> e.getValue().stream().forEach(
                l -> e.getKey().removeListener(l)
            )
        );
    }

    /**
     * Accept a new order and notify the delivery service to pick up the order.
     */
    @KafkaListener(topics = "#{'${order-app.order-event-topic-name}'}", clientIdPrefix = "order-event", containerFactory = "kafkaListenerContainerFactory")
    public void accept(ConsumerRecord<Long, Order> cr, @Payload Order payload) {
        //logger.info("[OrderEventService] received key {} | Payload: {} | Record: {}", cr.key(), payload, cr.toString());
        orderService.accept(payload);
        deliveryService.accept(payload);
    }

    public void registerExpiredHandler(RMapCache shelf) {
        shelfEventListeners.putIfAbsent(shelf, new ArrayList<>());
        List listeners = shelfEventListeners.get(shelf);

        // listener for expire event, i.e. decaying of the order .
        listeners.add(shelf.addListener(new EntryExpiredListener<Long, Order>() {
            @Override
            public void onExpired(EntryEvent<Long, Order> entryEvent) {
                RMapCache<Long, String> orderStatus = rMapCacheManager.getCache("order_status");
                RScoredSortedSet<Long> overflowShelfTracker = redissonClient.getScoredSortedSet("overflow_shelf_tracker");
                Long orderIdentifier = entryEvent.getKey();
                String orderName = entryEvent.getValue().getName();
                RLock statusLock = orderStatus.getReadWriteLock(orderIdentifier).writeLock();
                statusLock.lock();
                try {
                    String s = orderStatus.get(orderIdentifier);
                    if (s != null) {
                        ShelfType shelfType = ShelfType.fromString(s);
                        switch (shelfType) {
                            case HOT:
                            case COLD:
                            case FROZEN:
                                logger.info("Order [{}]-[{}] decayed, will be wasted", orderIdentifier, orderName);
                                orderStatus.remove(orderIdentifier);
                                orderService.scanOverflowShelf();
                                break;
                            case OVERFLOW:
                                logger.info("Order [{}]-[{}] decayed, will be wasted", orderIdentifier, orderName);
                                orderStatus.remove(orderIdentifier);
                                // remove the tracker for the order on the overflow shelf.
                                overflowShelfTracker.remove(entryEvent.getValue());
                                break;
                        }
                        shelfService.onShelfChange(shelfType);
                    }
                } finally {
                    statusLock.unlock();
                }
            }
        }));
    }

}
