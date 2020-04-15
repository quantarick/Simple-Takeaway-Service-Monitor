package com.engineering.challenge.solution.domain.dto;


import com.engineering.challenge.solution.domain.ShelfType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderDTO {

    private String name;

    private Double decayRate;

    private ShelfType temp;

    private Double shelfLife;
}
