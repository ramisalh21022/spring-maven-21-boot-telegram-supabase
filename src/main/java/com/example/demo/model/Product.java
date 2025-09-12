package com.example.demo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    private Integer id;
    private Integer companyId;
    private String productName;
    private String category;
    private BigDecimal price;
    private String imageUrl;
}
