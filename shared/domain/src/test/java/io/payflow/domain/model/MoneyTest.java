package io.payflow.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    // -------------------------------------------------------------------------
    // Construction validation
    // -------------------------------------------------------------------------

    @Test
    void rejectNegativeAmountOnConstruction() {
        assertThatThrownBy(() -> new Money(new BigDecimal("-1.00"), Currency.getInstance("USD")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void rejectNullCurrencyObject() {
        assertThatThrownBy(() -> new Money(BigDecimal.TEN, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency");
    }

    @Test
    void rejectNullCurrencyCode() {
        assertThatThrownBy(() -> Money.of(BigDecimal.TEN, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }

    @Test
    void rejectInvalidIso4217CurrencyCode() {
        // "XYZ" is not a valid ISO 4217 code on most JVMs
        assertThatThrownBy(() -> Money.of(BigDecimal.TEN, "NOTACURRENCY"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptValidIso4217CurrencyCode() {
        Money m = Money.of(new BigDecimal("100.00"), "EUR");
        assertThat(m.currency().getCurrencyCode()).isEqualTo("EUR");
        assertThat(m.amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void rejectNullAmount() {
        assertThatThrownBy(() -> new Money(null, Currency.getInstance("USD")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount");
    }

    // -------------------------------------------------------------------------
    // Arithmetic
    // -------------------------------------------------------------------------

    @Test
    void addProducesCorrectSum() {
        Money a = Money.of("10.00", "USD");
        Money b = Money.of("5.50", "USD");
        Money result = a.add(b);
        assertThat(result.amount()).isEqualByComparingTo("15.50");
        assertThat(result.currency().getCurrencyCode()).isEqualTo("USD");
    }

    @Test
    void subtractProducesCorrectDifference() {
        Money a = Money.of("10.00", "USD");
        Money b = Money.of("3.00", "USD");
        Money result = a.subtract(b);
        assertThat(result.amount()).isEqualByComparingTo("7.00");
    }

    @Test
    void subtractThrowsWhenResultWouldBeNegative() {
        Money a = Money.of("5.00", "USD");
        Money b = Money.of("10.00", "USD");
        assertThatThrownBy(() -> a.subtract(b))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void addWithLargeBigDecimalAmountsDoesNotOverflow() {
        BigDecimal huge = new BigDecimal("999999999999999999999999999.99");
        Money a = new Money(huge, Currency.getInstance("USD"));
        Money b = new Money(huge, Currency.getInstance("USD"));
        // Java BigDecimal never overflows — just verifies no exception
        Money result = a.add(b);
        assertThat(result.amount()).isEqualByComparingTo(huge.add(huge));
    }

    @Test
    void subtractWithLargeBigDecimalAmountsDoesNotOverflow() {
        BigDecimal huge = new BigDecimal("999999999999999999999999999.99");
        BigDecimal small = new BigDecimal("1.00");
        Money a = new Money(huge, Currency.getInstance("USD"));
        Money b = new Money(small, Currency.getInstance("USD"));
        Money result = a.subtract(b);
        assertThat(result.amount()).isEqualByComparingTo(huge.subtract(small));
    }

    @Test
    void addRejectsDifferentCurrencies() {
        Money usd = Money.of("10.00", "USD");
        Money eur = Money.of("10.00", "EUR");
        assertThatThrownBy(() -> usd.add(eur))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currencies");
    }

    // -------------------------------------------------------------------------
    // Predicates
    // -------------------------------------------------------------------------

    @Test
    void isNegativeReturnsFalseForZero() {
        assertThat(Money.zero("USD").isNegative()).isFalse();
    }

    @Test
    void isNegativeReturnsFalseForPositive() {
        assertThat(Money.of("0.01", "USD").isNegative()).isFalse();
    }

    @Test
    void isZeroReturnsTrueForZero() {
        assertThat(Money.zero("USD").isZero()).isTrue();
    }

    @Test
    void isZeroReturnsFalseForPositive() {
        assertThat(Money.of("0.01", "USD").isZero()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Equality
    // -------------------------------------------------------------------------

    @Test
    void equalMoneyInstancesAreEqual() {
        Money a = Money.of("100.00", "USD");
        Money b = Money.of("100.00", "USD");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void moneyWithDifferentScaleButSameValueIsEqual() {
        // 100 vs 100.0 — BigDecimal.compareTo treats them equal
        Money a = Money.of("100", "USD");
        Money b = Money.of("100.0", "USD");
        assertThat(a).isEqualTo(b);
    }
}
