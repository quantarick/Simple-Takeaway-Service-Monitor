package com.engineering.challenge.solution.services;


import com.engineering.challenge.solution.domain.ShelfType;
import com.engineering.challenge.solution.domain.entities.Order;

import org.redisson.api.RLock;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Random;

import lombok.RequiredArgsConstructor;

import static com.engineering.challenge.solution.domain.ShelfType.OVERFLOW;

@Service
@RequiredArgsConstructor
public class DeliveryService {

    private static Logger logger = LoggerFactory.getLogger(DeliveryService.class);

    private final ThreadPoolTaskScheduler scheduler;

    private final RedissonClient redissonClient;

    private final OrderService orderService;

    /**
     * Deliver the order, for now, it will only log the event, and in real production, an external system would be integrated.
     * @param order
     */
    public void accept(Order order) {
        scheduler.schedule(
            new Runnable() {
                @Override
                public void run() {
                    final Long candidateOrderIdentifier = order.getIdentifier();

                    // lock the order_status.
                    RMapCache<Long, String> orderStatus = redissonClient.getMapCache("order_status");
                    RLock statusLock = orderStatus.getReadWriteLock(candidateOrderIdentifier).writeLock();
                    statusLock.lock();
                    try {
                        // locate the shelf on which the order is.
                        String shelfName = orderStatus.get(candidateOrderIdentifier);
                        if (shelfName != null) {
                            RMapCache<Long, Order> shelf = redissonClient.getMapCache(shelfName);
                            RLock shelfLock = redissonClient.getReadWriteLock(shelfName + "_lock").writeLock();
                            shelfLock.lock();
                            try {
                                Order candidateOrder = orderService.removeFromShelf(
                                    candidateOrderIdentifier,
                                    ShelfType.fromString(shelfName).equals(OVERFLOW),
                                    shelf,
                                    orderStatus
                                );
                                if (candidateOrder != null) {
                                    logger.info(
                                        String.format("Deliver the order [%d]-[%s] successfully: %s", candidateOrderIdentifier, candidateOrder.getName(), candidateOrder)
                                    );
                                } else {
                                    logger.warn(
                                        String.format("Failed to deliver the order [%d]-[%s] cause it already decayed.", candidateOrderIdentifier, order.getName())
                                    );
                                }
                            } finally {
                                shelfLock.unlock();
                            }
                        } else {
                            logger.warn(
                                String.format("Failed to deliver the order [%d]-[%s] cause it already decayed.", candidateOrderIdentifier, order.getName())
                            );
                        }
                    } finally {
                        statusLock.unlock();
                    }
                }
            },
            new Date(System.currentTimeMillis() + getRandomNumberInRange(20000, 100000))
        );
    }

    private static int getRandomNumberInRange(int min, int max) {
        if (min >= max) {
            throw new IllegalArgumentException("max must be greater than min");
        }
        Random r = new Random();
        return r.nextInt((max - min) + 1) + min;
    }

}
