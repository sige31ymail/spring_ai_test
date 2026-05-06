package com.example.spring_ai_test;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TenantProductSearchTools {
    private static final Logger logger = LoggerFactory.getLogger(TenantProductSearchTools.class);
    private final List<Product> products = List.of(
            new Product("tokyo", "P001", "軽量ノートPC 13インチ", "ノートPC", 98000, 5),
            new Product("tokyo", "P002", "高性能ノートPC 15インチ", "ノートPC", 148000, 2),
            new Product("tokyo", "P004", "静音ワイヤレスマウス", "マウス", 2500, 20),

            new Product("osaka", "P001", "軽量ノートPC 13インチ", "ノートPC", 102000, 0),
            new Product("osaka", "P002", "高性能ノートPC 15インチ", "ノートPC", 145000, 4),
            new Product("osaka", "P004", "静音ワイヤレスマウス", "マウス", 2300, 8));

    @Tool(description = "Find the cheapest in-stock product by category for the current tenant.")
    public Product findCheapestInStockProduct(
            @ToolParam(description = "Product category, such as ノートPC or マウス") String category,
            ToolContext toolContext) {

        String tenantId = Objects.toString(toolContext.getContext().get("tenantId"), "tokyo");
        logger.info("ToolContext tenantId = {}", tenantId);

        return products.stream()
                .filter(p -> p.tenantId().equals(tenantId))
                .filter(p -> p.category().contains(category))
                .filter(p -> p.stock() > 0)
                .min(Comparator.comparingInt(Product::price))
                .orElse(null);
    }

    public record Product(
            String tenantId,
            String code,
            String name,
            String category,
            int price,
            int stock) {
    }
}