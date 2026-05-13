package net.jojoaddison.domain;

import java.math.BigDecimal;
import java.util.Comparator;

public final class AssertUtils {

    public static final Comparator<BigDecimal> bigDecimalCompareTo = (left, right) -> {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.compareTo(right);
    };

    private AssertUtils() {}
}
