package br.com.vendafacil.commerce;

import br.com.vendafacil.shared.validation.DecimalValues;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** All calculations in BRL. Global discounts use largest-remainder cent allocation. */
public final class SaleMath {
  private SaleMath() {}

  public record Discount(String type, String value) {}

  public record Input(BigDecimal price, BigDecimal quantity, Discount discount) {}

  public record Line(BigDecimal gross, BigDecimal discount, BigDecimal total) {}

  public record Totals(
      List<Line> lines,
      BigDecimal subtotal,
      BigDecimal discount,
      BigDecimal total,
      BigDecimal maximumPercent) {}

  public static BigDecimal money(String value) {
    return DecimalValues.parse("amount", value, 2);
  }

  public static BigDecimal round(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  static BigDecimal reduction(BigDecimal base, Discount discount) {
    if (discount == null) return new BigDecimal("0.00");
    var value = money(discount.value());
    if (value.signum() < 0
        || !("PERCENT".equals(discount.type()) || "AMOUNT".equals(discount.type())))
      throw new IllegalArgumentException("Desconto inválido.");
    if ("PERCENT".equals(discount.type()) && value.compareTo(new BigDecimal("100")) >= 0)
      throw new IllegalArgumentException("Desconto de 100% não é permitido.");
    var amount =
        "PERCENT".equals(discount.type()) ? round(base.multiply(value).movePointLeft(2)) : value;
    if (amount.compareTo(base) >= 0)
      throw new IllegalArgumentException(
          "O desconto deve ser menor que o valor do item ou da venda.");
    return amount;
  }

  public static Totals calculate(List<Input> inputs, Discount global) {
    var gross = new ArrayList<BigDecimal>();
    var own = new ArrayList<BigDecimal>();
    var net = new ArrayList<BigDecimal>();
    for (var input : inputs) {
      var amount = round(input.price().multiply(input.quantity()));
      if (amount.signum() <= 0)
        throw new IllegalArgumentException("O valor de cada item deve ser de pelo menos R$ 0,01.");
      gross.add(amount);
      own.add(reduction(amount, input.discount()));
      net.add(amount.subtract(own.getLast()));
    }
    var subtotal = gross.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    var base = net.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    var globalAmount = reduction(base, global);
    var shares = new ArrayList<BigDecimal>();
    var remainders = new ArrayList<BigDecimal>();
    for (var value : net) {
      var exact = globalAmount.multiply(value).divide(base, 20, RoundingMode.DOWN);
      var share = exact.setScale(2, RoundingMode.DOWN);
      shares.add(share);
      remainders.add(exact.subtract(share));
    }
    int cents =
        globalAmount
            .subtract(shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add))
            .movePointRight(2)
            .intValueExact();
    var order = new ArrayList<Integer>();
    for (int i = 0; i < net.size(); i++) order.add(i);
    order.sort(
        Comparator.<Integer, BigDecimal>comparing(remainders::get)
            .reversed()
            .thenComparingInt(i -> i));
    for (int i = 0; i < cents; i++) {
      int index = order.get(i);
      shares.set(index, shares.get(index).add(new BigDecimal("0.01")));
    }
    var lines = new ArrayList<Line>();
    var totalDiscount = new BigDecimal("0.00");
    var maximum = BigDecimal.ZERO;
    for (int i = 0; i < gross.size(); i++) {
      var discount = own.get(i).add(shares.get(i));
      var total = gross.get(i).subtract(discount);
      if (total.signum() <= 0)
        throw new IllegalArgumentException("O desconto zerou um item. Reduza o desconto.");
      maximum =
          maximum.max(
              discount
                  .multiply(new BigDecimal("100"))
                  .divide(gross.get(i), 10, RoundingMode.CEILING));
      lines.add(new Line(gross.get(i), discount, total));
      totalDiscount = totalDiscount.add(discount);
    }
    return new Totals(
        List.copyOf(lines), subtotal, totalDiscount, subtotal.subtract(totalDiscount), maximum);
  }
}
