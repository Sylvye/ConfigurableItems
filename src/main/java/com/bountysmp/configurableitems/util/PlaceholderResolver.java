package com.bountysmp.configurableitems.util;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlaceholderResolver {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)}");
    private static final Pattern VARIABLE = Pattern.compile("[A-Za-z0-9_]+");

    private PlaceholderResolver() {
    }

    public static Result render(String input, Map<String, String> variables, boolean allowUnresolved) {
        Matcher matcher = PLACEHOLDER.matcher(input == null ? "" : input);
        StringBuffer output = new StringBuffer();
        List<String> errors = new ArrayList<>();
        while (matcher.find()) {
            String expression = matcher.group(1).trim();
            Resolution resolution = resolve(expression, variables, allowUnresolved);
            if (resolution.unresolved()) {
                matcher.appendReplacement(output, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            if (resolution.error() != null) {
                errors.add(resolution.error());
                matcher.appendReplacement(output, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(resolution.value()));
        }
        matcher.appendTail(output);
        return new Result(output.toString(), List.copyOf(errors));
    }

    public static boolean hasPlaceholder(String input) {
        return PLACEHOLDER.matcher(input == null ? "" : input).find();
    }

    private static Resolution resolve(String expression, Map<String, String> variables, boolean allowUnresolved) {
        if (VARIABLE.matcher(expression).matches()) {
            String value = variables.get(expression.toUpperCase(Locale.ROOT));
            if (value != null) {
                return Resolution.value(value);
            }
            return allowUnresolved ? Resolution.unresolvedValue() : Resolution.error("Unknown variable {" + expression + "}");
        }
        try {
            double value = new Parser(expression, variables).parse();
            return Resolution.value(format(value));
        } catch (UnknownVariableException ex) {
            return allowUnresolved ? Resolution.unresolvedValue() : Resolution.error("Unknown variable {" + ex.variable() + "} in {" + expression + "}");
        } catch (ExpressionException ex) {
            return Resolution.error("Invalid expression {" + expression + "}: " + ex.getMessage());
        }
    }

    private static String format(double value) {
        if (!Double.isFinite(value)) {
            throw new ExpressionException("result is not finite");
        }
        if (Math.rint(value) == value) {
            return String.valueOf((long) value);
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    public record Result(String output, List<String> errors) {
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    private record Resolution(String value, String error, boolean unresolved) {
        static Resolution value(String value) {
            return new Resolution(value, null, false);
        }

        static Resolution error(String error) {
            return new Resolution(null, error, false);
        }

        static Resolution unresolvedValue() {
            return new Resolution(null, null, true);
        }
    }

    private static final class Parser {
        private final String input;
        private final Map<String, String> variables;
        private int index;

        private Parser(String input, Map<String, String> variables) {
            this.input = input;
            this.variables = variables;
        }

        double parse() {
            double value = expression();
            skipSpaces();
            if (index != input.length()) {
                throw new ExpressionException("unexpected '" + input.charAt(index) + "'");
            }
            return value;
        }

        private double expression() {
            double value = term();
            while (true) {
                skipSpaces();
                if (match('+')) {
                    value += term();
                } else if (match('-')) {
                    value -= term();
                } else {
                    return value;
                }
            }
        }

        private double term() {
            double value = factor();
            while (true) {
                skipSpaces();
                if (match('*')) {
                    value *= factor();
                } else if (match('/')) {
                    double divisor = factor();
                    if (divisor == 0.0) {
                        throw new ExpressionException("division by zero");
                    }
                    value /= divisor;
                } else {
                    return value;
                }
            }
        }

        private double factor() {
            skipSpaces();
            if (match('+')) {
                return factor();
            }
            if (match('-')) {
                return -factor();
            }
            if (match('(')) {
                double value = expression();
                skipSpaces();
                if (!match(')')) {
                    throw new ExpressionException("missing ')'");
                }
                return value;
            }
            if (index >= input.length()) {
                throw new ExpressionException("missing value");
            }
            char c = input.charAt(index);
            if (Character.isDigit(c) || c == '.') {
                return number();
            }
            if (Character.isLetter(c) || c == '_') {
                return variable();
            }
            throw new ExpressionException("unexpected '" + c + "'");
        }

        private double number() {
            int start = index;
            while (index < input.length() && (Character.isDigit(input.charAt(index)) || input.charAt(index) == '.')) {
                index++;
            }
            try {
                return Double.parseDouble(input.substring(start, index));
            } catch (NumberFormatException ex) {
                throw new ExpressionException("invalid number");
            }
        }

        private double variable() {
            int start = index;
            while (index < input.length()) {
                char c = input.charAt(index);
                if (!Character.isLetterOrDigit(c) && c != '_') {
                    break;
                }
                index++;
            }
            String variable = input.substring(start, index).toUpperCase(Locale.ROOT);
            String value = variables.get(variable);
            if (value == null) {
                throw new UnknownVariableException(variable);
            }
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException ex) {
                throw new ExpressionException("variable " + variable + " is not numeric");
            }
        }

        private boolean match(char expected) {
            if (index < input.length() && input.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void skipSpaces() {
            while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
                index++;
            }
        }
    }

    private static final class ExpressionException extends RuntimeException {
        private ExpressionException(String message) {
            super(message);
        }
    }

    private static final class UnknownVariableException extends RuntimeException {
        private final String variable;

        private UnknownVariableException(String variable) {
            this.variable = variable;
        }

        String variable() {
            return variable;
        }
    }
}
