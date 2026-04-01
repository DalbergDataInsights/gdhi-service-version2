package it.gdhi.service;

import it.gdhi.dto.AIRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentResponseHandler;

import java.util.Optional;

@Slf4j
@Service
public class GdhmAiService {

    private final BedrockAgentRuntimeAsyncClient client;
    private final String agentId;
    private final String agentAliasId;

    public GdhmAiService(
            BedrockAgentRuntimeAsyncClient client,
            @Value("${aws.bedrock.agentId}") String agentId,
            @Value("${aws.bedrock.agentAliasId}") String agentAliasId) {
        this.client = client;
        this.agentId = agentId;
        this.agentAliasId = agentAliasId;
    }

    public Flux<String> streamChat(AIRequest userQuery) {
        String sessionId = Optional.ofNullable(userQuery.getResponseId())
                .map(String::trim)
                .orElseThrow(() -> new IllegalArgumentException("Session ID (ResponseId) cannot be null"));

        InvokeAgentRequest request = InvokeAgentRequest.builder()
                .agentId(agentId)
                .agentAliasId(agentAliasId)
                .sessionId(sessionId)
                .inputText(userQuery.getQuery())
                .streamingConfigurations(sc -> sc
                        .streamFinalResponse(true)
                        .applyGuardrailInterval(3))
                .build();

        return Flux.create(sink -> {
            InvokeAgentResponseHandler handler = InvokeAgentResponseHandler.builder()
                    .onError(sink::error)
                    .onComplete(sink::complete)
                    .subscriber(InvokeAgentResponseHandler.Visitor.builder()
                            .onChunk(chunk -> {
                                String text = chunk.bytes().asUtf8String();
                                if (StringUtils.hasText(text)) {
                                    sink.next(text);
                                }
                            })
                            .onDefault(event -> log.debug("Received Bedrock event: {}", event.sdkEventType()))
                            .build())
                    .build();

            client.invokeAgent(request, handler);
        });
    }
}