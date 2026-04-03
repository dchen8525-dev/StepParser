package com.example.stepparser.parse;

import com.example.stepparser.model.StepDataSection;
import com.example.stepparser.model.StepDerivedValue;
import com.example.stepparser.model.StepEntityInstance;
import com.example.stepparser.model.StepEnumValue;
import com.example.stepparser.model.StepFile;
import com.example.stepparser.model.StepHeaderEntity;
import com.example.stepparser.model.StepHeaderSection;
import com.example.stepparser.model.StepListValue;
import com.example.stepparser.model.StepNumberValue;
import com.example.stepparser.model.StepOmittedValue;
import com.example.stepparser.model.StepReferenceValue;
import com.example.stepparser.model.StepStringValue;
import com.example.stepparser.model.StepTypedValue;
import com.example.stepparser.model.StepValue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class StepParser {

    private record ParsedEntityPayload(String type, List<StepValue> parameters) {
    }

    private final StepLexer lexer;
    private Token currentToken;

    public StepParser(String input) {
        this.lexer = new StepLexer(input);
        this.currentToken = lexer.nextToken();
    }

    public StepFile parse() {
        if (isKeyword("ISO-10303-21")) {
            expectKeyword("ISO-10303-21");
            expect(TokenType.SEMICOLON);
            StepHeaderSection header = parseHeaderSection();
            StepDataSection data = parseDataSection();
            expectKeyword("END-ISO-10303-21");
            expect(TokenType.SEMICOLON);
            expect(TokenType.END_OF_INPUT);
            return new StepFile(header, data);
        }

        if (isKeyword("HEADER")) {
            StepHeaderSection header = parseHeaderSection();
            StepDataSection data = parseDataSection();
            expect(TokenType.END_OF_INPUT);
            return new StepFile(header, data);
        }

        if (isKeyword("DATA")) {
            StepDataSection data = parseDataSection();
            expect(TokenType.END_OF_INPUT);
            return new StepFile(new StepHeaderSection(List.of()), data);
        }

        throw error("Expected ISO-10303-21, HEADER, or DATA");
    }

    private StepHeaderSection parseHeaderSection() {
        expectKeyword("HEADER");
        expect(TokenType.SEMICOLON);
        List<StepHeaderEntity> entities = new ArrayList<>();
        while (!isKeyword("ENDSEC")) {
            String name = expect(TokenType.IDENTIFIER).text();
            List<StepValue> parameters = parseParameterList();
            expect(TokenType.SEMICOLON);
            entities.add(new StepHeaderEntity(name, parameters));
        }
        expectKeyword("ENDSEC");
        expect(TokenType.SEMICOLON);
        return new StepHeaderSection(entities);
    }

    private StepDataSection parseDataSection() {
        expectKeyword("DATA");
        expect(TokenType.SEMICOLON);
        List<StepEntityInstance> entities = new ArrayList<>();
        while (!isKeyword("ENDSEC")) {
            String instanceToken = expect(TokenType.INSTANCE_NAME).text();
            expect(TokenType.EQUALS);
            ParsedEntityPayload payload = parseEntityPayload();
            expect(TokenType.SEMICOLON);
            entities.add(new StepEntityInstance(parseInstanceId(instanceToken), payload.type(), payload.parameters()));
        }
        expectKeyword("ENDSEC");
        expect(TokenType.SEMICOLON);
        return new StepDataSection(entities);
    }

    private ParsedEntityPayload parseEntityPayload() {
        if (peek(TokenType.LEFT_PAREN)) {
            return parseComplexEntityPayload();
        }
        String type = expect(TokenType.IDENTIFIER).text();
        return new ParsedEntityPayload(type, parseParameterList());
    }

    private ParsedEntityPayload parseComplexEntityPayload() {
        expect(TokenType.LEFT_PAREN);
        List<StepValue> components = new ArrayList<>();
        while (!peek(TokenType.RIGHT_PAREN)) {
            String componentType = expect(TokenType.IDENTIFIER).text();
            List<StepValue> componentParameters = parseParameterList();
            List<StepValue> component = new ArrayList<>();
            component.add(new StepStringValue(componentType));
            component.addAll(componentParameters);
            components.add(new StepListValue(component));
        }
        expect(TokenType.RIGHT_PAREN);
        return new ParsedEntityPayload("COMPLEX_ENTITY", components);
    }

    private List<StepValue> parseParameterList() {
        expect(TokenType.LEFT_PAREN);
        List<StepValue> parameters = new ArrayList<>();
        if (!peek(TokenType.RIGHT_PAREN)) {
            do {
                parameters.add(parseValue());
            } while (tryConsume(TokenType.COMMA));
        }
        expect(TokenType.RIGHT_PAREN);
        return parameters;
    }

    private StepValue parseValue() {
        return switch (currentToken.type()) {
            case STRING -> {
                String text = currentToken.text();
                advance();
                yield new StepStringValue(text);
            }
            case INTEGER, REAL -> {
                String text = currentToken.text();
                advance();
                yield new StepNumberValue(new BigDecimal(text));
            }
            case ENUM -> {
                String text = currentToken.text();
                advance();
                yield new StepEnumValue(text.toUpperCase());
            }
            case INSTANCE_NAME -> {
                String text = currentToken.text();
                advance();
                yield new StepReferenceValue(parseInstanceId(text));
            }
            case LEFT_PAREN -> new StepListValue(parseParameterList());
            case IDENTIFIER -> {
                String type = currentToken.text();
                advance();
                yield new StepTypedValue(type, parseParameterList());
            }
            case DOLLAR -> {
                advance();
                yield StepOmittedValue.INSTANCE;
            }
            case STAR -> {
                advance();
                yield StepDerivedValue.INSTANCE;
            }
            default -> throw error("Unexpected token while parsing value: " + currentToken.type());
        };
    }

    private int parseInstanceId(String tokenText) {
        return Integer.parseInt(tokenText.substring(1));
    }

    private boolean tryConsume(TokenType type) {
        if (peek(type)) {
            advance();
            return true;
        }
        return false;
    }

    private boolean peek(TokenType type) {
        return currentToken.type() == type;
    }

    private boolean isKeyword(String keyword) {
        return currentToken.type() == TokenType.KEYWORD && currentToken.text().equalsIgnoreCase(keyword);
    }

    private Token expect(TokenType type) {
        if (currentToken.type() != type) {
            throw error("Expected token " + type + " but found " + currentToken.type());
        }
        Token token = currentToken;
        advance();
        return token;
    }

    private void expectKeyword(String keyword) {
        if (!isKeyword(keyword)) {
            throw error("Expected keyword " + keyword + " but found " + currentToken.text());
        }
        advance();
    }

    private void advance() {
        currentToken = lexer.nextToken();
    }

    private StepParseException error(String message) {
        return new StepParseException(message + " at position " + currentToken.position());
    }
}
