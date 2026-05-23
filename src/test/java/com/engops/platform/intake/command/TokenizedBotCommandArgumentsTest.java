package com.engops.platform.intake.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenizedBotCommandArgumentsTest {

    @Test
    void parse_simpleArguments_splitsByWhitespace() {
        List<String> result = TokenizedBotCommandArguments.parse("/onboard acme 123 BUG_MINIMAL");
        assertThat(result).containsExactly("acme", "123", "BUG_MINIMAL");
    }

    @Test
    void parse_quotedArgument_preservesInternalSpaces() {
        List<String> result = TokenizedBotCommandArguments.parse(
                "/onboard acme \"Acme Corp\" 123 \"Demo Admin\" BUG_MINIMAL");
        assertThat(result).containsExactly("acme", "Acme Corp", "123", "Demo Admin", "BUG_MINIMAL");
    }

    @Test
    void parse_multipleQuotedArguments_handlesEach() {
        List<String> result = TokenizedBotCommandArguments.parse(
                "/cmd \"first one\" \"second two\" \"third three\"");
        assertThat(result).containsExactly("first one", "second two", "third three");
    }

    @Test
    void parse_escapedQuoteInsideQuotes_handledCorrectly() {
        List<String> result = TokenizedBotCommandArguments.parse(
                "/cmd \"He said \\\"hello\\\"\"");
        assertThat(result).containsExactly("He said \"hello\"");
    }

    @Test
    void parse_escapedBackslash_preservedAsLiteral() {
        List<String> result = TokenizedBotCommandArguments.parse(
                "/cmd \"C:\\\\path\\\\file\"");
        assertThat(result).containsExactly("C:\\path\\file");
    }

    @Test
    void parse_unmatchedQuote_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> TokenizedBotCommandArguments.parse(
                "/onboard acme \"Unclosed quote"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tirnoq");
    }

    @Test
    void parse_dropsCommandNameFirstToken() {
        List<String> result = TokenizedBotCommandArguments.parse("/help");
        assertThat(result).isEmpty();
    }

    @Test
    void parse_emptyAfterCommand_returnsEmptyList() {
        List<String> result = TokenizedBotCommandArguments.parse("/ping    ");
        assertThat(result).isEmpty();
    }

    @Test
    void parse_multipleConsecutiveSpaces_treatedAsOneSeparator() {
        List<String> result = TokenizedBotCommandArguments.parse("/cmd   a     b   c");
        assertThat(result).containsExactly("a", "b", "c");
    }

    @Test
    void parse_newlineAsWhitespace_acceptedSameAsSpace() {
        List<String> result = TokenizedBotCommandArguments.parse("/cmd a\nb\nc");
        assertThat(result).containsExactly("a", "b", "c");
    }

    @Test
    void parse_tabAsWhitespace_acceptedSameAsSpace() {
        List<String> result = TokenizedBotCommandArguments.parse("/cmd\ta\tb\tc");
        assertThat(result).containsExactly("a", "b", "c");
    }

    @Test
    void parse_singleQuoteIsLiteral_notTreatedAsQuote() {
        List<String> result = TokenizedBotCommandArguments.parse("/cmd it's fine");
        assertThat(result).containsExactly("it's", "fine");
    }

    @Test
    void parse_nullText_returnsEmptyList() {
        assertThat(TokenizedBotCommandArguments.parse(null)).isEmpty();
    }

    @Test
    void parse_emptyText_returnsEmptyList() {
        assertThat(TokenizedBotCommandArguments.parse("")).isEmpty();
        assertThat(TokenizedBotCommandArguments.parse("   ")).isEmpty();
    }

    @Test
    void parse_quotedTokenAdjacentToBareToken_treatedAsSeparate() {
        // "Quoted" and bare are still split if separated by whitespace.
        List<String> result = TokenizedBotCommandArguments.parse(
                "/cmd \"first\" second");
        assertThat(result).containsExactly("first", "second");
    }
}
