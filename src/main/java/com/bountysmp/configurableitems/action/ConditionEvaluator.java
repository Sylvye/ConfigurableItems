package com.bountysmp.configurableitems.action;

import java.util.List;

public final class ConditionEvaluator {
    private static final List<String> OPERATORS = List.of(">=", "<=", "!=", "=", ">", "<");

    private ConditionEvaluator() {
    }

    public static boolean evaluate(String condition) {
        for (String orPart : condition.split("\\|\\|")) {
            boolean andResult = true;
            for (String andPart : orPart.split("&&")) {
                andResult &= evaluateSingle(andPart.trim());
            }
            if (andResult) {
                return true;
            }
        }
        return false;
    }

    private static boolean evaluateSingle(String condition) {
        String clean = stripParens(condition);
        for (String operator : OPERATORS) {
            int index = clean.indexOf(operator);
            if (index > 0) {
                String left = clean.substring(0, index).trim();
                String right = clean.substring(index + operator.length()).trim();
                Double leftNumber = number(left);
                Double rightNumber = number(right);
                if (leftNumber != null && rightNumber != null) {
                    return compareNumbers(leftNumber, rightNumber, operator);
                }
                return compareStrings(left, right, operator);
            }
        }
        return Boolean.parseBoolean(clean);
    }

    private static String stripParens(String input) {
        String output = input;
        while (output.startsWith("(") && output.endsWith(")") && output.length() > 1) {
            output = output.substring(1, output.length() - 1).trim();
        }
        return output;
    }

    private static Double number(String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean compareNumbers(double left, double right, String operator) {
        return switch (operator) {
            case "=" -> left == right;
            case "!=" -> left != right;
            case ">" -> left > right;
            case "<" -> left < right;
            case ">=" -> left >= right;
            case "<=" -> left <= right;
            default -> false;
        };
    }

    private static boolean compareStrings(String left, String right, String operator) {
        return switch (operator) {
            case "=" -> left.equalsIgnoreCase(right);
            case "!=" -> !left.equalsIgnoreCase(right);
            default -> false;
        };
    }
}
