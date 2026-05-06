package com.example.spring_ai_test;

import java.util.Comparator;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class DirectProductTools {

    private final List<Product> products = List.of(
            new Product("P001", "軽量ノートPC 13インチ", "ノートPC", 98000, 5),
            new Product("P002", "高性能ノートPC 15インチ", "ノートPC", 148000, 2),
            new Product("P003", "ゲーミングノートPC", "ノートPC", 198000, 0),
            new Product("P004", "静音ワイヤレスマウス", "マウス", 2500, 20),
            new Product("P005", "メカニカルキーボード", "キーボード", 12000, 7)
    );

    @Tool(
            description = "Find the cheapest in-stock product by category and return the raw result directly.",
            returnDirect = true
    )
    public Product findCheapestProductDirect(
            @ToolParam(description = "Product category, such as ノートPC, マウス, キーボード") String category) {

        return products.stream()
                .filter(p -> p.category().contains(category))
                .filter(p -> p.stock() > 0)
                .min(Comparator.comparingInt(Product::price))
                .orElse(null);
    }

    public record Product(
            String code,
            String name,
            String category,
            int price,
            int stock) {
    }
}