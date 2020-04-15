package com.engineering.challenge.solution.services;

import com.engineering.challenge.solution.domain.ShelfType;
import com.engineering.challenge.solution.domain.entities.Order;

import org.redisson.api.RLock;
import org.redisson.api.RMapCache;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;

import static com.engineering.challenge.solution.domain.ShelfType.OVERFLOW;

@Service
@RequiredArgsConstructor
public class OrderService {

    Logger logger = LoggerFactory.getLogger(OrderService.class);

    private final RedissonClient redissonClient;

    private final KafkaTemplate<Long, Object> kafkaTemplate;

    private final ShelfService shelfService;

    @Value("#{${order-app.shelf-capacity}}")
    private Map<ShelfType, Integer> shelfCapacity;

    @Value("${order-app.order-event-topic-name}")
    private String topicName;

    private Map<RMapCache, List<Integer>> shelfEventListeners = new HashMap<>();

    /**
     * Scan the overflow shelf and move order back to the target shelf if possible.
     */
    public void scanOverflowShelf() {
        // lock the overflow shelf.
        RMapCache<Long, String> orderStatus = redissonClient.getMapCache("order_status");
        RLock overflowShelfLock = redissonClient.getReadWriteLock(OVERFLOW.toString() + "_lock").writeLock();
        RScoredSortedSet<Long> overflowShelfTracker = redissonClient.getScoredSortedSet("overflow_shelf_tracker");
        overflowShelfLock.lock();
        try {
            RMapCache<Long, Order> overflowShelf = redissonClient.getMapCache(OVERFLOW.toString());
            while (overflowShelf.size() > 0) {
                Long candidateOrderIdentifier = overflowShelfTracker.pollLast();
                if (candidateOrderIdentifier != null) {
                    Order candidateOrder = overflowShelf.get(candidateOrderIdentifier);
                    if (candidateOrder != null) {
                        // lock the target shelf.
                        ShelfType targetShelfType = candidateOrder.getTemp();
                        RLock targetShelfLock = redissonClient.getReadWriteLock(targetShelfType + "_lock").writeLock();
                        targetShelfLock.lock();
                        try {
                            RMapCache<Long, Order> targetShelf = redissonClient.getMapCache(targetShelfType.toString());
                            if (targetShelf.size() < shelfCapacity.get(targetShelfType)) {
                                // remove order from the overflow shelf.
                                candidateOrder = removeFromShelf(candidateOrderIdentifier, true, overflowShelf, orderStatus);

                                // check again considering no way to lock the expired data.
                                if (candidateOrder != null) {
                                    // put the order on the target shelf.
                                    putOrderOnShelf(candidateOrder, false, overflowShelf, orderStatus, overflowShelfTracker);
                                }
                            } else {
                                // recover overflowShelfTracker if failed to move
                                overflowShelfTracker.add(candidateOrder.getLatestDeliveryTime(), candidateOrder.getIdentifier());
                            }
                        } finally {
                            targetShelfLock.unlock();
                        }
                        // break if successfully moved an order from overflow to target shelf.
                        break;
                    }
                }
            }
        } finally {
            overflowShelfLock.unlock();
        }
    }

    /**
     * Put the order on shelf
     */
    @Async
    public void accept(Order order) {
        RMapCache<Long, String> orderStatus = redissonClient.getMapCache("order_status");
        RScoredSortedSet<Long> overflowShelfTracker = redissonClient.getScoredSortedSet("overflow_shelf_tracker");
        ShelfType shelfType = order.getTemp();
        // lock the target shelf.
        RLock shelfLock = redissonClient.getReadWriteLock(shelfType.toString() + "_lock").writeLock();
        if (shelfLock.tryLock()) {
            try {
                RMapCache<Long, Order> shelf = redissonClient.getMapCache(shelfType.toString());
                if (shelf.size() < shelfCapacity.get(shelfType)) {
                    // put order on the target shelf
                    putOrderOnShelf(order, false, shelf, orderStatus, overflowShelfTracker);
                }
                // if the target shelf is full, try to put it on the overflow shelf.
                else {
                    // lock the overflow shelf.
                    RLock overflowShelfLock = redissonClient.getReadWriteLock(OVERFLOW.toString() + "_lock").writeLock();
                    if (overflowShelfLock.tryLock()) {
                        try {
                            RMapCache<Long, Order> overflowShelf = redissonClient.getMapCache(OVERFLOW.toString());
                            if (overflowShelf.size() < shelfCapacity.get(OVERFLOW)) {
                                // put order on the overflow shelf.
                                putOrderOnShelf(order, true, overflowShelf, orderStatus, overflowShelfTracker);
                            } else {
                                // if both the target shelf and the overflow shelf are full, mark the order as 'wasted'.
                                logger.info(String.format("No space for order[%s]: %s, waste directly.", order.getIdentifier(), order.toString()));
                                orderStatus.remove(order.getIdentifier());
                            }
                        } finally {
                            overflowShelfLock.unlock();
                        }
                    } else {
                        // system busy, requeue the order.
                        kafkaTemplate.send(topicName, order);
                    }
                }
            } finally {
                shelfLock.unlock();
            }
        } else {
            // system busy, requeue the order.
            kafkaTemplate.send(topicName, order);
        }
    }

    void putOrderOnShelf(Order order, Boolean toOverflowShelf, RMapCache<Long, Order> shelf, RMapCache<Long, String> orderStatus, RScoredSortedSet<Long> overflowShelfTracker) {
        // set/reset on-shelf date for the order and ready to put on shelf.
        order.setOnShelfDate(LocalDateTime.now());
        order.setIsOnOverflowShelf(toOverflowShelf);
        order.resetValue();

        ShelfType toShelfType = toOverflowShelf ? OVERFLOW : order.getTemp();
        logger.info(String.format("put order on shelf[%s]: %s", toShelfType, order.toString()));
        shelf.put(order.getIdentifier(), order, order.getLatestDeliveryTime(), TimeUnit.SECONDS);

        // update the order status.
        orderStatus.put(order.getIdentifier(), toShelfType.toString());

        // setup the tracker if it's for overflow shelf.
        if (toOverflowShelf) {
            overflowShelfTracker.add(order.getLatestDeliveryTime(), order.getIdentifier());
        }

        // send shelf change event
        shelfService.onShelfChange(toShelfType);
    }

    Order removeFromShelf(Long orderIdentifier, Boolean fromOverflowShelf, RMapCache<Long, Order> shelf, RMapCache<Long, String> orderStatus) {
        Order order = shelf.remove(orderIdentifier);

        if (order == null) return null;

        ShelfType fromShelfType = fromOverflowShelf ? OVERFLOW : order.getTemp();

        // !Important, need to reset value when situation changes.
        order.resetValue();
        logger.info(String.format("remove order from shelf[%s]: %s", fromShelfType, order.toString()));

        // remove from the order status.
        RLock statusLock = orderStatus.getReadWriteLock(orderIdentifier).writeLock();
        statusLock.lock();
        try {
            orderStatus.remove(orderIdentifier);
        } finally {
            statusLock.unlock();
        }

        // send shelf change event
        shelfService.onShelfChange(fromShelfType);

        return order;
    }

}
