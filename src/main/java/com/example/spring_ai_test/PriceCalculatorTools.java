package com.example.spring_ai_test;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class PriceCalculatorTools {

    @Tool(description = "Calculate the total price including tax from unit price, quantity, and tax rate.")
    public PriceResult calculateTotalPrice(
            @ToolParam(description = "Unit price in Japanese yen") int unitPrice,
            @ToolParam(description = "Quantity of items") int quantity,
            @ToolParam(description = "Tax rate as decimal. For example, 0.1 means 10 percent tax.") double taxRate) {

        int subtotal = unitPrice * quantity;
        int tax = (int) Math.floor(subtotal * taxRate);
        int total = subtotal + tax;

        return new PriceResult(unitPrice, quantity, taxRate, subtotal, tax, total);
    }

    public record PriceResult(
            int unitPrice,
            int quantity,
            double taxRate,
            int subtotal,
            int tax,
            int total) {
    }
}