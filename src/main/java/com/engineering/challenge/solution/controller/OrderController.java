package com.engineering.challenge.solution.controller;

import com.engineering.challenge.solution.domain.dto.OrderDTO;
import com.engineering.challenge.solution.services.OrderEventService;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RequestMapping("/orders")
@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderEventService orderEventService;

    /**
     * Place a new order, note the delivery service will be automatically called and adding 2~10s delay to pick up the order from the shelf.
     * @param newOrder
     */
    @PostMapping
    public void placeNewOrder(@RequestBody OrderDTO newOrder) {
        orderEventService.placeNewOrder(newOrder);
    }

}
