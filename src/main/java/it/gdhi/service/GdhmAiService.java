package it.gdhi.service;

import it.gdhi.dto.AIRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.ActionGroupInvocationInput;
import software.amazon.awssdk.services.bedrockagentruntime.model.DependencyFailedException;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentResponseHandler;
import software.amazon.awssdk.services.bedrockagentruntime.model.Observation;
import software.amazon.awssdk.services.bedrockagentruntime.model.ReturnControlPayload;
import software.amazon.awssdk.services.bedrockagentruntime.model.SessionState;
import software.amazon.awssdk.services.bedrockagentruntime.model.Trace;
import software.amazon.awssdk.services.bedrockagentruntime.model.TracePart;

import java.util.Optional;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static it.gdhi.utils.LanguageCode.USER_LANGUAGE;
import static java.util.UUID.randomUUID;

@Slf4j
@Service
public class GdhmAiService {

    private final BedrockAgentRuntimeAsyncClient client;
    private final BedrockReturnControlService returnControlService;
    private final String agentId;
    private final String agentAliasId;
    private final int maxModelRetryAttempts;
    private final boolean enableTrace;
    private final int applyGuardrailInterval;
    private final boolean logFullTrace;

    public GdhmAiService(
            BedrockAgentRuntimeAsyncClient client,
            BedrockReturnControlService returnControlService,
            @Value("${aws.bedrock.agentId}") String agentId,
            @Value("${aws.bedrock.agentAliasId}") String agentAliasId,
            @Value("${aws.bedrock.maxModelRetryAttempts:2}") int maxModelRetryAttempts,
            @Value("${aws.bedrock.enableTrace:true}") boolean enableTrace,
            @Value("${aws.bedrock.applyGuardrailInterval:20}") int applyGuardrailInterval,
            @Value("${aws.bedrock.logFullTrace:false}") boolean logFullTrace) {
        this.client = client;
        this.returnControlService = returnControlService;
        this.agentId = agentId;
        this.agentAliasId = agentAliasId;
        this.maxModelRetryAttempts = maxModelRetryAttempts;
        this.enableTrace = enableTrace;
        this.applyGuardrailInterval = applyGuardrailInterval;
        this.logFullTrace = logFullTrace;
    }

    public Flux<String> streamChat(AIRequest userQuery) {
        String sessionId = Optional.ofNullable(userQuery.getResponseId())
                .map(String::trim)
                .filter(StringUtils::hasText)
                .orElseGet(() -> randomUUID().toString());
        String query = userQuery.getQuery();
        String userLanguage = resolveUserLanguage(userQuery);

        log.info("Starting Bedrock agent sessionId={} queryLength={} userLanguage={}", sessionId,
                query == null ? 0 : query.length(), userLanguage);
        return Flux.create(sink -> invokeAgent(buildPromptRequest(sessionId, query, userLanguage), sink,
                userLanguage, new AtomicBoolean(false), 1));
    }

    private void invokeAgent(InvokeAgentRequest request, FluxSink<String> sink, String userLanguage,
                             AtomicBoolean hasEmittedText, int attemptNumber) {
        AtomicReference<ReturnControlPayload> pendingReturnControl = new AtomicReference<>();

        InvokeAgentResponseHandler handler = InvokeAgentResponseHandler.builder()
                .onError(error -> handleInvokeError(request, error, sink, userLanguage, hasEmittedText, attemptNumber))
                .onComplete(() -> continueOrComplete(request.sessionId(), pendingReturnControl.get(), sink,
                        userLanguage, hasEmittedText))
                .subscriber(InvokeAgentResponseHandler.Visitor.builder()
                        .onChunk(chunk -> {
                            String text = chunk.bytes().asUtf8String();
                            if (StringUtils.hasText(text)) {
                                hasEmittedText.set(true);
                                sink.next(text);
                            }
                        })
                        .onTrace(this::logTracePart)
                        .onReturnControl(pendingReturnControl::set)
                        .onDefault(event -> log.debug("Received Bedrock event: {}", event.sdkEventType()))
                        .build())
                .build();

        log.info("Invoking Bedrock agent sessionId={} returnControlResultsPresent={} attempt={}",
                request.sessionId(),
                request.sessionState() != null && request.sessionState().hasReturnControlInvocationResults(),
                attemptNumber);
        client.invokeAgent(request, handler);
    }

    private void continueOrComplete(String sessionId, ReturnControlPayload payload, FluxSink<String> sink,
                                    String userLanguage, AtomicBoolean hasEmittedText) {
        if (payload == null) {
            log.info("Bedrock agent sessionId={} completed without further return control", sessionId);
            sink.complete();
            return;
        }

        try {
            log.info("Bedrock agent sessionId={} returned control invocationId={} inputs={}",
                    sessionId, payload.invocationId(), payload.invocationInputs().size());
            SessionState sessionState = returnControlService.buildSessionState(payload, userLanguage);
            invokeAgent(buildReturnControlRequest(sessionId, sessionState), sink, userLanguage, hasEmittedText, 1);
        }
        catch (Exception ex) {
            log.error("Failed while handling Bedrock return control for sessionId={}", sessionId, ex);
            finishWithVisibleError(sink, hasEmittedText, ex, true);
        }
    }

    private InvokeAgentRequest buildPromptRequest(String sessionId, String inputText, String userLanguage) {
        return baseRequestBuilder(sessionId)
                .inputText(buildInputText(inputText, userLanguage))
                .sessionState(buildLanguageSessionState(userLanguage))
                .build();
    }

    private InvokeAgentRequest buildReturnControlRequest(String sessionId, SessionState sessionState) {
        return baseRequestBuilder(sessionId)
                .sessionState(sessionState)
                .build();
    }

    private InvokeAgentRequest.Builder baseRequestBuilder(String sessionId) {
        return InvokeAgentRequest.builder()
                .agentId(agentId)
                .agentAliasId(agentAliasId)
                .enableTrace(enableTrace)
                .sessionId(sessionId)
                .streamingConfigurations(sc -> sc
                        .streamFinalResponse(true)
                        .applyGuardrailInterval(applyGuardrailInterval));
    }

    private SessionState buildLanguageSessionState(String userLanguage) {
        return SessionState.builder()
                .sessionAttributes(Map.of(USER_LANGUAGE, userLanguage))
                .promptSessionAttributes(Map.of(USER_LANGUAGE, userLanguage))
                .build();
    }

    private String buildInputText(String inputText, String userLanguage) {
        String query = inputText == null ? "" : inputText.trim();
        return """
                Preferred response language: %s.
                Use this language for the full answer and for tool parameters when supported.
                For this turn, if the question involves live GDHM data, call the relevant tools again for this specific turn.
                Do not rely on data retrieved in an earlier turn, even if the conversation is continuing.

                User request:
                %s
                """.formatted(userLanguage, query);
    }

    private String resolveUserLanguage(AIRequest userQuery) {
        String userLanguage = Optional.ofNullable(userQuery.getUserLanguage()).map(String::trim).orElse("en");
        return StringUtils.hasText(userLanguage) ? userLanguage : "en";
    }

    private void handleInvokeError(InvokeAgentRequest request, Throwable error, FluxSink<String> sink,
                                   String userLanguage, AtomicBoolean hasEmittedText, int attemptNumber) {
        String sessionId = request.sessionId();
        Throwable rootCause = unwrap(error);
        boolean afterToolResults = hasReturnControlResults(request);
        if (shouldRetry(request, rootCause, hasEmittedText, attemptNumber)) {
            log.warn("Retrying Bedrock agent sessionId={} after dependency failure attempt={}/{} cause={}", sessionId,
                    attemptNumber, maxModelRetryAttempts, rootCause.getMessage());
            invokeAgent(request, sink, userLanguage, hasEmittedText, attemptNumber + 1);
            return;
        }

        log.error("Bedrock agent invocation failed for sessionId={} on attempt={} afterToolResults={} errorType={} message={}",
                sessionId, attemptNumber, afterToolResults, rootCause.getClass().getSimpleName(),
                rootCause.getMessage(), rootCause);
        finishWithVisibleError(sink, hasEmittedText, rootCause, afterToolResults);
    }

    private boolean shouldRetry(InvokeAgentRequest request, Throwable error, AtomicBoolean hasEmittedText,
                                int attemptNumber) {
        return error instanceof DependencyFailedException
                && !hasReturnControlResults(request)
                && !hasEmittedText.get()
                && attemptNumber < maxModelRetryAttempts;
    }

    private boolean hasReturnControlResults(InvokeAgentRequest request) {
        return request.sessionState() != null && request.sessionState().hasReturnControlInvocationResults();
    }

    private void finishWithVisibleError(FluxSink<String> sink, AtomicBoolean hasEmittedText, Throwable error,
                                        boolean afterToolResults) {
        String visibleMessage = userVisibleErrorMessage(error, afterToolResults);
        if (!sink.isCancelled()) {
            if (hasEmittedText.get()) {
                sink.next("\n\n");
            }
            sink.next(visibleMessage);
        }
        sink.complete();
    }

    private String userVisibleErrorMessage(Throwable error, boolean afterToolResults) {
        if (error instanceof DependencyFailedException) {
            if (afterToolResults) {
                return "That request was too broad for the AI to finish reliably after loading the GDHM data. Please narrow it by country, year, category, phase, or region and try again.";
            }
            return "I'm having trouble getting a response from Bedrock right now. Please try the same question again in a moment.";
        }
        return "I ran into a temporary problem while generating the answer. Please try again.";
    }

    private Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private void logTracePart(TracePart tracePart) {
        Trace trace = tracePart.trace();
        if (trace == null) {
            log.info("Bedrock trace sessionId={} received empty trace event", tracePart.sessionId());
            return;
        }

        if (trace.failureTrace() != null) {
            log.warn("Bedrock trace sessionId={} phase=failure traceId={} reason={}",
                    tracePart.sessionId(),
                    trace.failureTrace().traceId(),
                    trace.failureTrace().failureReason());
        }
        else if (trace.preProcessingTrace() != null) {
            log.info("Bedrock trace sessionId={} phase=pre-processing type={} traceId={}",
                    tracePart.sessionId(),
                    trace.preProcessingTrace().type(),
                    trace.preProcessingTrace().modelInvocationOutput() == null
                            ? null
                            : trace.preProcessingTrace().modelInvocationOutput().traceId());
        }
        else if (trace.orchestrationTrace() != null) {
            logOrchestrationTrace(tracePart.sessionId(), trace.orchestrationTrace().observation(),
                    trace.orchestrationTrace().invocationInput(), trace.orchestrationTrace().type().toString());
        }
        else if (trace.postProcessingTrace() != null) {
            log.info("Bedrock trace sessionId={} phase=post-processing type={}",
                    tracePart.sessionId(),
                    trace.postProcessingTrace().type());
        }
        else {
            log.info("Bedrock trace sessionId={} phase={} details=unclassified",
                    tracePart.sessionId(), trace.type());
        }

        if (logFullTrace) {
            log.info("Bedrock full trace sessionId={} payload={}", tracePart.sessionId(), trace);
        }
    }

    private void logOrchestrationTrace(String sessionId, Observation observation,
                                       software.amazon.awssdk.services.bedrockagentruntime.model.InvocationInput invocationInput,
                                       String orchestrationType) {
        ActionGroupInvocationInput actionGroupInvocationInput =
                invocationInput == null ? null : invocationInput.actionGroupInvocationInput();
        String actionGroupName = actionGroupInvocationInput == null ? null : actionGroupInvocationInput.actionGroupName();
        String apiPath = actionGroupInvocationInput == null ? null : actionGroupInvocationInput.apiPath();
        String verb = actionGroupInvocationInput == null ? null : actionGroupInvocationInput.verb();

        log.info("Bedrock trace sessionId={} phase=orchestration orchestrationType={} observationType={} invocationType={} actionGroup={} verb={} apiPath={} traceId={}",
                sessionId,
                orchestrationType,
                observation == null ? null : observation.typeAsString(),
                invocationInput == null ? null : invocationInput.invocationTypeAsString(),
                actionGroupName,
                verb,
                apiPath,
                observation == null ? null : observation.traceId());
    }
}
