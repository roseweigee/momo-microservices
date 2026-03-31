package com.demo.order.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value Object：金額
 *
 * 為什麼用 Value Object 而不是直接用 BigDecimal？
 *
 * 1. 防止精度錯誤
 *    BigDecimal 到處散落，每個人用的 scale 不同
 *    Money 統一用 scale=2、HALF_UP，精度一致
 *
 * 2. 不可變（Immutable）
 *    計算結果是新物件，不修改原本的值
 *    multiply() 回傳新的 Money，不改 this
 *
 * 3. 業務語意清晰
 *    totalPrice = unitPrice.multiply(quantity)
 *    比 BigDecimal 相乘更能表達業務意圖
 *
 * Value Object 判斷標準：
 * - 沒有唯一 ID
 * - 用值本身描述（兩個 Money 金額相同就相等）
 * - 不可變
 */
public class Money {

    private final BigDecimal amount;
    private static final String CURRENCY = "TWD";

    private Money(BigDecimal amount) {
        if (amount == null) throw new IllegalArgumentException("金額不能為 null");
        if (amount.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("金額不能為負數");
        // 統一精度：小數點後兩位，四捨五入
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public static Money of(long amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }

    /** 乘以數量（計算訂單總金額） */
    public Money multiply(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("數量必須大於 0");
        return new Money(this.amount.multiply(BigDecimal.valueOf(quantity)));
    }

    /** 加法 */
    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    /** 是否大於另一個金額 */
    public boolean isGreaterThan(Money other) {
        return this.amount.compareTo(other.amount) > 0;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return CURRENCY;
    }

    /** Value Object：以值判斷相等，不是以 ID */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money)) return false;
        Money money = (Money) o;
        return Objects.equals(amount, money.amount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount);
    }

    @Override
    public String toString() {
        return CURRENCY + " " + amount.toPlainString();
    }
}
