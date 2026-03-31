package com.demo.order.domain;

import lombok.Getter;

/**
 * Entity：訂單明細
 *
 * Entity vs Value Object 判斷：
 * - OrderItem 有自己的 ID（itemId）
 * - 需要追蹤它的狀態（是否已出貨、是否退貨）
 * - 所以是 Entity，不是 Value Object
 *
 * 對比 Money（Value Object）：
 * - Money 沒有 ID，用值描述，NT$100 就是 NT$100
 * - OrderItem 有 ID，同樣商品的兩筆明細是不同的
 *
 * 注意：OrderItem 不是 Aggregate Root
 * 外部不能直接操作 OrderItem，必須透過 Order
 */
@Getter
public class OrderItem {

    private final String itemId;
    private final String productId;
    private final String productName;
    private final int quantity;
    private final Money unitPrice;
    private final Money subtotal;  // = unitPrice × quantity

    public OrderItem(String productId, String productName,
                     int quantity, Money unitPrice) {
        if (quantity <= 0) throw new IllegalArgumentException("數量必須大於 0");
        this.itemId = java.util.UUID.randomUUID().toString();
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = unitPrice.multiply(quantity);
    }

    /**
     * Entity：以 ID 判斷相等
     * 兩個 OrderItem 就算商品、數量、金額都一樣，
     * 只要 itemId 不同就是不同的 Entity
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderItem)) return false;
        OrderItem that = (OrderItem) o;
        return itemId.equals(that.itemId);
    }

    @Override
    public int hashCode() {
        return itemId.hashCode();
    }
}
