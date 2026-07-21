package org.ItsInspector.sunnyCoinflip.utils;

public class NumberParser {
    
    public static double parseNumber(String input) throws IllegalArgumentException {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("Inserisci un numero valido.");
        }
        
        String normalized = input.trim().toLowerCase();
        
        double multiplier = 1.0;
        if (normalized.endsWith("k")) {
            multiplier = 1_000.0;
            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("m")) {
            multiplier = 1_000_000.0;
            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("b")) {
            multiplier = 1_000_000_000.0;
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        
        try {
            double value = Double.parseDouble(normalized);
            return value * multiplier;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Inserisci un numero valido.");
        }
    }
}
