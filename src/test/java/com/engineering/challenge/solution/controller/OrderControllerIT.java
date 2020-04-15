package com.engineering.challenge.solution.controller;


import com.engineering.challenge.solution.RedisKafkaSolutionApplication;

import org.json.JSONException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Random;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = RedisKafkaSolutionApplication.class)
@TestPropertySource(locations = "/application-test.properties")
public class OrderControllerIT {

    Logger logger = LoggerFactory.getLogger(OrderControllerIT.class);

    private static TestRestTemplate restTemplate;

    private static JSONArray orders = new JSONArray();

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
    public void testCreateOrder() throws JSONException {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        int start = 0;
        while (start < orders.size()) {
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
                    restTemplate.postForObject(String.format("http://localhost:%d/orders", serverPort), request, String.class);
                }
            );
            start = end;
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
            }
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