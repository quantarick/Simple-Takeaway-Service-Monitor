package com.engineering.challenge.solution.domain.dto;

import com.engineering.challenge.solution.domain.ShelfType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShelfDTO {

    ShelfType type;

    List<OrderDTO> orders;
}
