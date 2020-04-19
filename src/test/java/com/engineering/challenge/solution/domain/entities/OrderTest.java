package com.engineering.challenge.solution.domain.entities;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Core-calculation logic test
 */
public class OrderTest {

    @Test
    void testGetLatestDeliveryTimeOnTargetShelf() throws InterruptedException {
        Order order = new Order();
        assertThat(order.getValue() == null);
        order.setShelfLife(100d);
        order.setDecayRate(0.5d);
        order.setOnShelfDate(LocalDateTime.now());
        // setup the test order which placed on the target shelf.
        order.setIsOnOverflowShelf(false);
        order.onMove();
        assertThat(Math.ceil(order.getLatestDeliveryTime())).isEqualTo(67);
    }

    @Test
    void testGetLatestDeliveryTimeOnOverflowShelf() throws InterruptedException {
        Order order = new Order();
        assertThat(order.getValue() == null);
        order.setShelfLife(100d);
        order.setDecayRate(0.5d);
        order.setOnShelfDate(LocalDateTime.now());
        // setup the test order which placed on the target shelf.
        order.setIsOnOverflowShelf(true);
        order.onMove();
        assertThat(Math.ceil(order.getLatestDeliveryTime())).isEqualTo(50);
    }

    @Test
    void testGetLatestDeliveryTimeOnOverflowShelfThenTargetShelf() throws InterruptedException {
        Order order = new Order();
        assertThat(order.getValue() == null);
        order.setShelfLife(100d);
        order.setDecayRate(0.5d);
        order.setOnShelfDate(LocalDateTime.now());
        // setup the test order which placed on the target shelf.
        order.setIsOnOverflowShelf(true);
        order.onMove();

        // order stays on overflow shelf for 1 seconds
        Thread.sleep(1000);
        order.onMove();

        // order moved to target shelf.
        order.setOnShelfDate(LocalDateTime.now());
        order.setIsOnOverflowShelf(false);
        order.onMove();
        assertThat(Math.ceil(order.getLatestDeliveryTime())).isEqualTo(66);
    }

    @Test
    void testGetNormalizedValueOnTargetShelfForFirstTime() throws InterruptedException{
        Order order = new Order();
        assertThat(order.getValue() == null);
        order.setShelfLife(100d);
        order.setDecayRate(0.5d);
        order.setOnShelfDate(LocalDateTime.now());
        // setup the test order which placed on the target shelf.
        order.setIsOnOverflowShelf(false);
        order.onMove();
        assertThat(Math.ceil(order.getNormalizedValue() * 1000)).isEqualTo(1000);
    }

    @Test
    void testGetNormalizedValueOnTargetShelfForSecondTime() throws InterruptedException {
        Order order = new Order();
        assertThat(order.getValue() == null);
        order.setShelfLife(100d);
        order.setDecayRate(0.5d);
        order.setOnShelfDate(LocalDateTime.now());
        // setup the test order which placed on the target shelf.
        order.setIsOnOverflowShelf(false);
        order.onMove();
        Thread.sleep(1000);
        assertThat(Math.ceil(order.getNormalizedValue() * 1000)).isEqualTo(985);
    }

    @Test
    void testGetNormalizedValueOnOverflowShelfForFirstTime() throws InterruptedException {
        Order order = new Order();
        assertThat(order.getValue() == null);
        order.setShelfLife(100d);
        order.setDecayRate(0.5d);
        order.setOnShelfDate(LocalDateTime.now());
        // setup the test order which placed on the overflow shelf.
        order.setIsOnOverflowShelf(true);
        order.onMove();
        assertThat(Math.ceil(order.getNormalizedValue() * 1000)).isEqualTo(1000);
    }

    @Test
    void testGetNormalizedValueOnOverflowShelfForSecondTime() throws InterruptedException {
        Order order = new Order();
        assertThat(order.getValue() == null);
        order.setShelfLife(100d);
        order.setDecayRate(0.5d);
        order.setOnShelfDate(LocalDateTime.now());
        // setup the test order which placed on the overflow shelf.
        order.setIsOnOverflowShelf(true);
        order.onMove();
        Thread.sleep(1000);
        assertThat(Math.ceil(order.getNormalizedValue() * 1000)).isEqualTo(980);
    }
}
