package com.engineering.challenge.solution.controller;


import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.engineering.challenge.solution.RedisKafkaSolutionApplication;
import com.engineering.challenge.solution.domain.dto.OrderDTO;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.assertj.core.util.Lists;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = RedisKafkaSolutionApplication.class,
    properties = {"management.port=0"}
)
@DirtiesContext
@TestPropertySource(locations = "/application-test.properties")
public class OrderControllerIT {

    Logger logger = LoggerFactory.getLogger(OrderControllerIT.class);

    private static TestRestTemplate restTemplate;

    private static JSONArray orders = new JSONArray();

    private final ObjectMapper mapper = new ObjectMapper();

    @LocalServerPort
    private Integer serverPort;

    @BeforeAll
    public static void runBeforeAllTestMethods() {
        //JSON parser object to parse read file
        JSONParser jsonParser = new JSONParser();

        // tips, use System.out.println(new File(".").getAbsoluteFile()); to locate the path.
        try (FileReader reader = new FileReader("src/test/resources/Engineering_Challenge_Orders.json")) {
            //Read JSON file
            Object obj = jsonParser.parse(reader);
            orders = (JSONArray) obj;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }

        restTemplate = new TestRestTemplate();
    }

    @Test
    public void testCreateOrder() {
        // wait for redis/kafka to warm up.
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
        }

        // place new orders.
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        int start = 0;
        while (start < orders.size()) {
            // randomly place 1 ~ 9 orders.
            int end = start + getRandomNumberInRange(1, 9);
            end = end >= orders.size() ? orders.size() - 1 : end;
            if (start == end) {
                break;
            }
            List<JSONObject> ordersInBatch = orders.subList(start, end);
            logger.info(String.format("The order batch size: %d", ordersInBatch.size()));
            ordersInBatch.stream().forEach(
                order -> {
                    HttpEntity<String> request =
                        new HttpEntity<String>(order.toString(), headers);
                    OrderDTO resp = restTemplate.postForObject(String.format("http://localhost:%d/orders", serverPort), request, OrderDTO.class);
                    assertThat(resp).isNotNull();
                    assertThat(resp.getNormalizedValue()).isBetween(0.9d, 1d);
                }
            );
            start = end;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
        }

    }

    @Test
    public void testOrderServerSendStream() {
        // place some orders.
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        List<JSONObject> ordersInBatch = orders.subList(0, 1);
        logger.info(String.format("The order batch size: %d", ordersInBatch.size()));
        ordersInBatch.stream().forEach(
            order -> {
                HttpEntity<String> request =
                    new HttpEntity<String>(order.toString(), headers);
                OrderDTO resp = restTemplate.postForObject(String.format("http://localhost:%d/orders", serverPort), request, OrderDTO.class);
                assertThat(resp).isNotNull();
                assertThat(resp.getNormalizedValue()).isBetween(0.9d, 1d);
            }
        );

        // start stream get orders.
        HttpHeaders headers2 = new HttpHeaders();
        headers2.setAccept(Lists.newArrayList(MediaType.APPLICATION_JSON));

        try {
            restTemplate.execute(String.format("http://localhost:%d/shelves/frozen/stream", serverPort), HttpMethod.GET, request -> {
            }, response -> {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(response.getBody()));
                String line;
                Integer orderCount = Integer.MAX_VALUE;
                try {
                    while ((line = bufferedReader.readLine()) != null) {
                        if (line.isEmpty()) continue;
                        JsonObject shelf = new JsonParser().parse(line.replace("data:", "")).getAsJsonObject();
                        String shelfType = shelf.get("type").getAsString();
                        assertThat(shelfType).isEqualTo("frozen");
                        JsonArray orders = shelf.get("orders").getAsJsonArray();
                        assertThat(orders.size()).isLessThanOrEqualTo(orderCount);
                        orderCount = orders.size();
                        logger.info("Orders on shelf[{}]: {}", shelfType, orderCount);
                        if (orderCount == 0) {
                            response.close();
                            return response;
                        }
                    }
                } catch (IOException e) {
                    //Something clever
                }
                return response;
            });
        } catch (Exception e) {
            logger.info("should exit!");
        }
    }

    private static int getRandomNumberInRange(int min, int max) {
        if (min >= max) {
            throw new IllegalArgumentException("max must be greater than min");
        }
        Random r = new Random();
        return r.nextInt((max - min) + 1) + min;
    }

}