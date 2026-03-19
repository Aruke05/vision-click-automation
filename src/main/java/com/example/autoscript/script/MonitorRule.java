package com.example.autoscript.script;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MonitorRule {
    private final String name;
    private final List<Condition> conditions = new ArrayList<>();
    private final List<ActionStep> actions = new ArrayList<>();
    private String conditionExpression = "";

    public MonitorRule(String name) {
        this.name = name;
    }

    public MonitorRule addCondition(Condition condition) {
        this.conditions.add(condition);
        return this;
    }

    public MonitorRule addAction(ActionStep action) {
        this.actions.add(action);
        return this;
    }

    public MonitorRule setConditionExpression(String conditionExpression) {
        this.conditionExpression = conditionExpression == null ? "" : conditionExpression.trim();
        return this;
    }

    public boolean matches(MonitorContext context) throws Exception {
        if (conditions.isEmpty()) {
            return true;
        }

        Map<String, Boolean> conditionValues = new HashMap<>();
        for (Condition condition : conditions) {
            if (condition == null) {
                continue;
            }
            String conditionName = normalizeName(condition.name());
            if (conditionName.isBlank()) {
                throw new IllegalArgumentException("条件名称不能为空");
            }
            ConditionResult result = condition.evaluate(context);
            context.putConditionResult(conditionName, result);
            if (conditionValues.containsKey(conditionName)) {
                throw new IllegalArgumentException("存在重复条件名称: " + conditionName);
            }
            conditionValues.put(conditionName, result.matched());
        }

        if (conditionValues.isEmpty()) {
            return true;
        }

        if (conditionExpression == null || conditionExpression.isBlank()) {
            for (boolean value : conditionValues.values()) {
                if (!value) {
                    return false;
                }
            }
            return true;
        }

        return new ExpressionParser(conditionExpression, conditionValues).parse();
    }

    public void runActions(MonitorContext context) throws Exception {
        for (ActionStep action : actions) {
            action.execute(context);
        }
    }

    public boolean evaluateAndRun(MonitorContext context) throws Exception {
        boolean matched = matches(context);
        if (matched) {
            runActions(context);
        }
        return matched;
    }

    public String getName() {
        return name;
    }

    public List<Condition> getConditions() {
        return conditions;
    }

    public List<ActionStep> getActions() {
        return actions;
    }

    public String getConditionExpression() {
        return conditionExpression;
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
    }

    private static final class ExpressionParser {
        private final String expression;
        private final Map<String, Boolean> values;
        private int index = 0;

        private ExpressionParser(String expression, Map<String, Boolean> values) {
            this.expression = expression == null ? "" : expression;
            this.values = values;
        }

        boolean parse() {
            boolean result = parseOr();
            skipWhitespace();
            if (index < expression.length()) {
                throw new IllegalArgumentException("条件表达式存在非法片段: " + expression.substring(index));
            }
            return result;
        }

        private boolean parseOr() {
            boolean left = parseAnd();
            while (true) {
                if (matchKeyword("OR") || matchSymbol("||")) {
                    boolean right = parseAnd();
                    left = left || right;
                    continue;
                }
                return left;
            }
        }

        private boolean parseAnd() {
            boolean left = parseUnary();
            while (true) {
                if (matchKeyword("AND") || matchSymbol("&&")) {
                    boolean right = parseUnary();
                    left = left && right;
                    continue;
                }
                return left;
            }
        }

        private boolean parseUnary() {
            if (matchKeyword("NOT") || matchSymbol("!")) {
                return !parseUnary();
            }
            if (matchSymbol("(")) {
                boolean value = parseOr();
                if (!matchSymbol(")")) {
                    throw new IllegalArgumentException("条件表达式括号不匹配: " + expression);
                }
                return value;
            }
            String identifier = readIdentifier();
            if (identifier == null || identifier.isBlank()) {
                throw new IllegalArgumentException("条件表达式缺少条件名: " + expression);
            }
            String normalized = identifier.toUpperCase(Locale.ROOT);
            if (!values.containsKey(normalized)) {
                throw new IllegalArgumentException("条件表达式引用了不存在的条件: " + identifier);
            }
            return values.get(normalized);
        }

        private boolean matchKeyword(String keyword) {
            skipWhitespace();
            if (startsWithIgnoreCase(keyword)) {
                int end = index + keyword.length();
                if (end >= expression.length() || !isIdentifierPart(expression.charAt(end))) {
                    index = end;
                    return true;
                }
            }
            return false;
        }

        private boolean matchSymbol(String symbol) {
            skipWhitespace();
            if (expression.startsWith(symbol, index)) {
                index += symbol.length();
                return true;
            }
            return false;
        }

        private String readIdentifier() {
            skipWhitespace();
            if (index >= expression.length()) {
                return null;
            }
            char first = expression.charAt(index);
            if (!isIdentifierStart(first)) {
                return null;
            }
            int start = index;
            index++;
            while (index < expression.length() && isIdentifierPart(expression.charAt(index))) {
                index++;
            }
            return expression.substring(start, index);
        }

        private boolean startsWithIgnoreCase(String value) {
            if (index + value.length() > expression.length()) {
                return false;
            }
            return expression.regionMatches(true, index, value, 0, value.length());
        }

        private void skipWhitespace() {
            while (index < expression.length() && Character.isWhitespace(expression.charAt(index))) {
                index++;
            }
        }

        private boolean isIdentifierStart(char ch) {
            return Character.isLetter(ch) || ch == '_';
        }

        private boolean isIdentifierPart(char ch) {
            return Character.isLetterOrDigit(ch) || ch == '_';
        }
    }
}
