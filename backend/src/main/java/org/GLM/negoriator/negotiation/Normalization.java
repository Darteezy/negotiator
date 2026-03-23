package org.GLM.negoriator.negotiation;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class Normalization {

	static final int SCALE = 4;

	private Normalization() {
	}

	static BigDecimal normalizePositiveDecimal(BigDecimal value, BigDecimal min, BigDecimal max) {
		if (max.compareTo(min) == 0) {
			return BigDecimal.ZERO;
		}

		BigDecimal normalized = value.subtract(min)
			.divide(max.subtract(min), SCALE, RoundingMode.HALF_UP);

		return clamp(normalized);
	}

	static BigDecimal normalizeInvertedDecimal(BigDecimal value, BigDecimal min, BigDecimal max) {
		if (max.compareTo(min) == 0) {
			return BigDecimal.ZERO;
		}

		BigDecimal normalized = max.subtract(value)
			.divide(max.subtract(min), SCALE, RoundingMode.HALF_UP);

		return clamp(normalized);
	}

	static BigDecimal normalizePositiveInt(int value, int min, int max) {
		if (max == min) {
			return BigDecimal.ZERO;
		}

		BigDecimal normalized = BigDecimal.valueOf(value - min)
			.divide(BigDecimal.valueOf(max - min), SCALE, RoundingMode.HALF_UP);

		return clamp(normalized);
	}

	static BigDecimal normalizeNegativeInt(int value, int min, int max) {
		if (max == min) {
			return BigDecimal.ZERO;
		}

		BigDecimal normalized = BigDecimal.valueOf(max - value)
			.divide(BigDecimal.valueOf(max - min), SCALE, RoundingMode.HALF_UP);

		return clamp(normalized);
	}

	static BigDecimal clamp(BigDecimal value) {
		if (value.compareTo(BigDecimal.ZERO) < 0) {
			return BigDecimal.ZERO;
		}
		if (value.compareTo(BigDecimal.ONE) > 0) {
			return BigDecimal.ONE;
		}
		return value.setScale(SCALE, RoundingMode.HALF_UP);
	}
}
