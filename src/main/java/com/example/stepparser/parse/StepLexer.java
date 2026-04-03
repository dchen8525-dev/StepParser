package com.example.stepparser.parse;

import java.util.Set;

final class StepLexer {

    private static final Set<String> KEYWORDS = Set.of(
            "ISO-10303-21",
            "HEADER",
            "ENDSEC",
            "DATA",
            "END-ISO-10303-21"
    );

    private final String input;
    private int index;

    StepLexer(String input) {
        this.input = input;
    }

    Token nextToken() {
        skipIgnored();
        if (index >= input.length()) {
            return new Token(TokenType.END_OF_INPUT, "", index);
        }

        char current = input.charAt(index);
        int position = index;

        return switch (current) {
            case '=' -> singleCharacter(TokenType.EQUALS, position);
            case ',' -> singleCharacter(TokenType.COMMA, position);
            case ';' -> singleCharacter(TokenType.SEMICOLON, position);
            case '(' -> singleCharacter(TokenType.LEFT_PAREN, position);
            case ')' -> singleCharacter(TokenType.RIGHT_PAREN, position);
            case '$' -> singleCharacter(TokenType.DOLLAR, position);
            case '*' -> singleCharacter(TokenType.STAR, position);
            case '#' -> readInstanceName(position);
            case '\'' -> readString(position);
            case '.' -> readEnum(position);
            default -> {
                if (isNumberStart(current)) {
                    yield readNumber(position);
                }
                if (isIdentifierStart(current)) {
                    yield readIdentifierOrKeyword(position);
                }
                throw error("Unexpected character '" + current + "'", position);
            }
        };
    }

    private Token singleCharacter(TokenType type, int position) {
        char value = input.charAt(index++);
        return new Token(type, String.valueOf(value), position);
    }

    private Token readInstanceName(int position) {
        index++;
        int startDigits = index;
        while (index < input.length() && Character.isDigit(input.charAt(index))) {
            index++;
        }
        if (startDigits == index) {
            throw error("Expected digits after '#'", position);
        }
        return new Token(TokenType.INSTANCE_NAME, input.substring(position, index), position);
    }

    private Token readString(int position) {
        index++;
        StringBuilder builder = new StringBuilder();
        while (index < input.length()) {
            char current = input.charAt(index++);
            if (current == '\'') {
                if (index < input.length() && input.charAt(index) == '\'') {
                    builder.append('\'');
                    index++;
                    continue;
                }
                return new Token(TokenType.STRING, builder.toString(), position);
            }
            builder.append(current);
        }
        throw error("Unterminated string literal", position);
    }

    private Token readEnum(int position) {
        index++;
        int start = index;
        while (index < input.length() && input.charAt(index) != '.') {
            index++;
        }
        if (index >= input.length()) {
            throw error("Unterminated enumeration literal", position);
        }
        String text = input.substring(start, index);
        index++;
        return new Token(TokenType.ENUM, text, position);
    }

    private Token readNumber(int position) {
        int start = index;
        if (input.charAt(index) == '+' || input.charAt(index) == '-') {
            index++;
        }

        boolean hasDigits = false;
        while (index < input.length() && Character.isDigit(input.charAt(index))) {
            index++;
            hasDigits = true;
        }

        boolean isReal = false;
        if (index < input.length() && input.charAt(index) == '.') {
            isReal = true;
            index++;
            while (index < input.length() && Character.isDigit(input.charAt(index))) {
                index++;
                hasDigits = true;
            }
        }

        if (!hasDigits) {
            throw error("Invalid numeric literal", position);
        }

        if (index < input.length() && (input.charAt(index) == 'E' || input.charAt(index) == 'e')) {
            isReal = true;
            index++;
            if (index < input.length() && (input.charAt(index) == '+' || input.charAt(index) == '-')) {
                index++;
            }
            int exponentStart = index;
            while (index < input.length() && Character.isDigit(input.charAt(index))) {
                index++;
            }
            if (exponentStart == index) {
                throw error("Invalid exponent in numeric literal", position);
            }
        }

        return new Token(isReal ? TokenType.REAL : TokenType.INTEGER, input.substring(start, index), position);
    }

    private Token readIdentifierOrKeyword(int position) {
        int start = index;
        while (index < input.length() && isIdentifierPart(input.charAt(index))) {
            index++;
        }
        String text = input.substring(start, index);
        if (KEYWORDS.contains(text.toUpperCase())) {
            return new Token(TokenType.KEYWORD, text.toUpperCase(), position);
        }
        return new Token(TokenType.IDENTIFIER, text.toUpperCase(), position);
    }

    private void skipIgnored() {
        while (index < input.length()) {
            char current = input.charAt(index);
            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }
            if (current == '/' && index + 1 < input.length() && input.charAt(index + 1) == '*') {
                skipComment();
                continue;
            }
            break;
        }
    }

    private void skipComment() {
        index += 2;
        while (index + 1 < input.length()) {
            if (input.charAt(index) == '*' && input.charAt(index + 1) == '/') {
                index += 2;
                return;
            }
            index++;
        }
        throw error("Unterminated block comment", index);
    }

    private boolean isNumberStart(char current) {
        if (Character.isDigit(current)) {
            return true;
        }
        if ((current == '+' || current == '-') && index + 1 < input.length()) {
            char next = input.charAt(index + 1);
            return Character.isDigit(next) || next == '.';
        }
        return false;
    }

    private boolean isIdentifierStart(char current) {
        return Character.isLetter(current) || current == '_';
    }

    private boolean isIdentifierPart(char current) {
        return Character.isLetterOrDigit(current) || current == '_' || current == '-';
    }

    private StepParseException error(String message, int position) {
        return new StepParseException(message + " at position " + position);
    }
}
