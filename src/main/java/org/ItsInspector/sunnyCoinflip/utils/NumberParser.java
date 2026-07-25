package org.ItsInspector.sunnyCoinflip.utils;

public class NumberParser {
    public static double parseNumber(String input) throws IllegalArgumentException {
        if (input != null && !input.isEmpty()) {
            String normalized = input.trim().toLowerCase();
            double multiplier = (double)1.0F;
            if (normalized.endsWith("k")) {
                multiplier = (double)1000.0F;
                normalized = normalized.substring(0, normalized.length() - 1);
            } else if (normalized.endsWith("m")) {
                multiplier = (double)1000000.0F;
                normalized = normalized.substring(0, normalized.length() - 1);
            } else if (normalized.endsWith("b")) {
                multiplier = (double)1.0E9F;
                normalized = normalized.substring(0, normalized.length() - 1);
            }

            try {
                double value = Double.parseDouble(normalized);
                return value * multiplier;
            } catch (NumberFormatException var6) {
                throw new IllegalArgumentException("Inserisci un numero valido.");
            }
        } else {
            throw new IllegalArgumentException("Inserisci un numero valido.");
        }
    }
}
