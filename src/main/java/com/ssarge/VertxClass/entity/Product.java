package com.ssarge.VertxClass.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@AllArgsConstructor
public class Product {
    private String id;
    private String number;
    private String description;
}
