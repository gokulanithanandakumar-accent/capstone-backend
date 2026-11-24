package com.insuranceapp.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuranceapp.backend.exception.AiServiceUnavailableException;

class InsuranceRecommendationAiClientTest {
    private ChatModel chatModel;
    private InsuranceRecommendationAiClient client;

    private final RecommendationPromptInput input = new RecommendationPromptInput(36, "Family income protection",
            "20 years", "TERM",
            List.of(new ProductCandidate("HD-TERM-001", "HealthDekho Secure Term", "TERM", 240,
                    new BigDecimal("5000000.00"), "MONTHLY", new BigDecimal("1250.00"))),
            3);

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        client = new InsuranceRecommendationAiClient(chatModel, new ObjectMapper());
    }

    @Test
    void sendsCustomerContextAndProductsAndParsesStructuredResponse() {
        when(chatModel.call(any(Prompt.class))).thenReturn(response("""
                ```json
                {"recommendations":[{"productCode":"HD-TERM-001",
                "recommendationReason":"Long-term protection aligned with income-replacement goal","suitability":"HIGH"}]}
                ```
                """, "claude-haiku-4-5"));

        AiRecommendationOutcome outcome = client.recommend(input);

        assertThat(outcome.suggestions()).containsExactly(new AiSuggestion("HD-TERM-001",
                "Long-term protection aligned with income-replacement goal", "HIGH"));
        assertThat(outcome.reportedModel()).isEqualTo("claude-haiku-4-5");

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        var messages = prompt.getValue().getInstructions();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(messages.get(0).getText())
                .contains("Recommend ONLY products listed in \"availableProducts\"")
                .contains("at most 3 products")
                .contains("HIGH, MEDIUM or LOW")
                .contains("Do not calculate or quote premiums");
        assertThat(messages.get(1).getMessageType()).isEqualTo(MessageType.USER);
        assertThat(messages.get(1).getText())
                .contains("\"customerAge\":36")
                .contains("\"insuranceGoals\":\"Family income protection\"")
                .contains("\"productCode\":\"HD-TERM-001\"");
    }

    @Test
    void returnsEmptySuggestionsWhenModelFindsNothingSuitable() {
        when(chatModel.call(any(Prompt.class))).thenReturn(response("{\"recommendations\":[]}", null));

        AiRecommendationOutcome outcome = client.recommend(input);

        assertThat(outcome.suggestions()).isEmpty();
        assertThat(outcome.reportedModel()).isNull();
    }

    @Test
    void wrapsModelFailureAsServiceUnavailable() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("AccessDeniedException"));

        assertThatThrownBy(() -> client.recommend(input))
                .isInstanceOf(AiServiceUnavailableException.class)
                .hasMessage(InsuranceRecommendationAiClient.UNAVAILABLE_MESSAGE);
    }

    @Test
    void rejectsUnparseableResponse() {
        when(chatModel.call(any(Prompt.class))).thenReturn(response("I recommend the term plan.", null));

        assertThatThrownBy(() -> client.recommend(input)).isInstanceOf(AiServiceUnavailableException.class);
    }

    @Test
    void rejectsEmptyResponseAndMissingRecommendationsField() {
        when(chatModel.call(any(Prompt.class))).thenReturn(response(" ", null));
        assertThatThrownBy(() -> client.recommend(input)).isInstanceOf(AiServiceUnavailableException.class);

        when(chatModel.call(any(Prompt.class))).thenReturn(response("{}", null));
        assertThatThrownBy(() -> client.recommend(input)).isInstanceOf(AiServiceUnavailableException.class);
    }

    private static ChatResponse response(String text, String model) {
        ChatResponseMetadata.Builder metadata = ChatResponseMetadata.builder();
        if (model != null) {
            metadata.model(model);
        }
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))), metadata.build());
    }
}
