package br.com.vendafacil.commerce;

import static org.assertj.core.api.Assertions.*;

import br.com.vendafacil.shared.config.AppProperties;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SaleMathTest {
  private SaleMath.Input item(String price, String quantity, String type, String discount) {
    return new SaleMath.Input(
        new BigDecimal(price), new BigDecimal(quantity), new SaleMath.Discount(type, discount));
  }

  @Test
  void kilogramsRoundHalfUpInBrl() {
    var result = SaleMath.calculate(List.of(item("19.90", "1.255", "PERCENT", "0")), null);
    assertThat(result.total()).isEqualByComparingTo("24.97");
  }

  @Test
  void cumulativeDiscountUsesTheEffectiveLinePercentage() {
    var result =
        SaleMath.calculate(
            List.of(item("100", "1", "PERCENT", "10")), new SaleMath.Discount("PERCENT", "10"));
    assertThat(result.total()).isEqualByComparingTo("81");
    assertThat(result.maximumPercent()).isEqualByComparingTo("19");
  }

  @Test
  void largestRemainderDistributesEveryCentDeterministically() {
    var result =
        SaleMath.calculate(
            List.of(
                item("1", "1", "AMOUNT", "0"),
                item("1", "1", "AMOUNT", "0"),
                item("1", "1", "AMOUNT", "0")),
            new SaleMath.Discount("AMOUNT", "0.01"));
    assertThat(result.lines().getFirst().discount()).isEqualByComparingTo("0.01");
    assertThat(
            result.lines().stream()
                .map(SaleMath.Line::discount)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo(result.discount());
    assertThat(
            result.lines().stream()
                .map(SaleMath.Line::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo("2.99");
  }

  @Test
  void percentageUsesRoundedAmountsAndCannotHideOneCentAboveTheLimit() {
    var result = SaleMath.calculate(List.of(item("0.05", "1", "PERCENT", "10")), null);
    assertThat(result.maximumPercent()).isEqualByComparingTo("20");
  }

  @Test
  void freeAndNegativeItemsAreRejected() {
    assertThatThrownBy(() -> SaleMath.calculate(List.of(item("10", "1", "PERCENT", "100")), null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SaleMath.calculate(List.of(item("10", "1", "AMOUNT", "11")), null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SaleMath.calculate(List.of(item("10", "1", "AMOUNT", "-1")), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void productionCannotEnableDemoSalesEvenWithConflictingProfiles() {
    var properties = new AppProperties("example.test", true, "", true);
    var env = new MockEnvironment();
    env.setActiveProfiles("production", "local");
    var service = new SaleService(null, null, null, null, null, null, null, null, properties, env);
    assertThat(service.demoSalesEnabled()).isFalse();
    env.setActiveProfiles("staging");
    assertThat(service.demoSalesEnabled()).isFalse();
    env.setActiveProfiles("local");
    assertThat(service.demoSalesEnabled()).isTrue();
  }
}
