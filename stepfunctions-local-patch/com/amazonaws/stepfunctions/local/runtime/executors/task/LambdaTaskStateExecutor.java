package com.amazonaws.stepfunctions.local.runtime.executors.task;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.AmazonClientException;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClient;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.InvocationType;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.stepfunctions.model.ExecutionStatus;
import com.amazonaws.services.stepfunctions.model.HistoryEvent;
import com.amazonaws.services.stepfunctions.model.HistoryEventType;
import com.amazonaws.services.stepfunctions.model.LambdaFunctionFailedEventDetails;
import com.amazonaws.services.stepfunctions.model.LambdaFunctionScheduledEventDetails;
import com.amazonaws.services.stepfunctions.model.LambdaFunctionSucceededEventDetails;
import com.amazonaws.services.stepfunctions.model.LambdaFunctionTimedOutEventDetails;
import com.amazonaws.services.stepfunctions.model.LoggingConfiguration;
import com.amazonaws.stepfunctions.local.model.ExecutionModel;
import com.amazonaws.stepfunctions.local.model.definition.states.StateDefinition;
import com.amazonaws.stepfunctions.local.model.definition.states.TaskStateDefinition;
import com.amazonaws.stepfunctions.local.repo.ExecutionHistoryRepo;
import com.amazonaws.stepfunctions.local.runtime.Config;
import com.amazonaws.stepfunctions.local.runtime.Limits;
import com.amazonaws.stepfunctions.local.runtime.Log;
import com.amazonaws.stepfunctions.local.runtime.CredentialProvider;
import com.amazonaws.stepfunctions.local.runtime.exceptions.AwlParserException;
import com.amazonaws.stepfunctions.local.runtime.exceptions.ExecutionTerminatedException;
import com.amazonaws.stepfunctions.local.runtime.executors.StateExecutor;
import com.amazonaws.stepfunctions.local.runtime.executors.StateMachineExecutor.StateError;
import com.amazonaws.stepfunctions.local.runtime.executors.StateMachineExecutor.StateResult;
import com.amazonaws.stepfunctions.local.util.ResponseUtils;
import com.amazonaws.stepfunctions.local.util.Arn.ArnUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import javax.inject.Inject;

public class LambdaTaskStateExecutor extends StateExecutor {
    private static final Pattern LAMBDA_ARN_PATTERN = Pattern.compile("arn:aws:lambda:[a-z0-9-]+:[0-9]{12}:function:.*");
    private final AWSLambda lambdaClient;
    private final ExecutionHistoryRepo executionHistoryRepo;
    private final ObjectMapper mapper;
    private final ExecutorService executorService;
    private final Config config;
    private TaskStateDefinition taskState;
    private String lambdaArn;

    @Inject
    public LambdaTaskStateExecutor(AWSLambda lambdaClient, ExecutionHistoryRepo executionHistoryRepo, ObjectMapper mapper, ExecutorService executorService, Config config) {
        this.lambdaClient = lambdaClient;
        this.executionHistoryRepo = executionHistoryRepo;
        this.mapper = mapper;
        this.executorService = executorService;
        this.config = config;
    }

    public void init(ExecutionModel executionModel, StateDefinition stateDefinition, String stateName, String input, long enteredEventId) {
        super.init(executionModel, stateDefinition, stateName, input, enteredEventId);
        this.taskState = (TaskStateDefinition)stateDefinition;
        this.lambdaArn = ArnUtils.injectRegionAndAccountId(this.taskState.getResource(), this.config.getAccount(), this.config.getRegion());
    }

    private AWSLambda getClient(String region) {
        // TODO: potentially cache clients per region, for better performance
        AWSLambdaClientBuilder builder = AWSLambdaClient.builder()
            .withEndpointConfiguration(
                new EndpointConfiguration(config.getLambdaEndpoint(), region))
            .withCredentials(new CredentialProvider().dummyProvider())
            .withClientConfiguration(
                new ClientConfiguration()
                    .withMaxErrorRetry(0)
                    .withConnectionTimeout(5 * 1000)
                    .withRequestTimeout(16 * 60 * 1000)
                    .withSocketTimeout(16 * 60 * 1000)
                    .withMaxConnections(1000)
                    .withConnectionMaxIdleMillis(5000L)
                    .withTcpKeepAlive(true));
        return builder.build();
    }

    public StateResult execute() {
        this.validateLambdaARN();
        String filteredInput = this.getFilteredInput();
        String effectiveInput = this.getEffectiveInput(this.taskState.getParameters());

        // whummer: Small patch to enable invocation of Lambdas across different regions
        String region = this.lambdaArn.split(":")[3];
        AWSLambda lambdaClient = getClient(region);

        InvokeResult invokeResult;
        try {
            this.recordLambdaScheduledEvent(effectiveInput);
            this.recordLambdaStartedEvent();
            String functionName = ArnUtils.parseFunctionName(this.lambdaArn);
            InvokeRequest invokeRequest = (new InvokeRequest()).withFunctionName(functionName).withInvocationType(InvocationType.RequestResponse).withPayload(effectiveInput);
            Future<InvokeResult> invokeResultFuture = this.executorService.submit(() -> {
                return lambdaClient.invoke(invokeRequest);
            });
            invokeResult = (InvokeResult)invokeResultFuture.get(this.taskState.getTimeoutSecondsValue(filteredInput) == null ? Limits.MAX_TASK_EXECUTION_TIME : this.taskState.getTimeoutSecondsValue(filteredInput), TimeUnit.SECONDS);
        } catch (TimeoutException var8) {
            return this.exitOnTimeout();
        } catch (ExecutionException var9) {
            Throwable cause = var9.getCause();
            if (cause instanceof AmazonClientException) {
                AmazonClientException amazonClientException = (AmazonClientException)cause;
                String error = "Lambda." + amazonClientException.getClass().getSimpleName();
                return this.exitOnFailure(error, amazonClientException.getMessage());
            }

            throw com.amazonaws.stepfunctions.local.runtime.exceptions.ExecutionException.runtimeError(var9.getMessage());
        } catch (InterruptedException var10) {
            throw new ExecutionTerminatedException(String.format("The execution of %s has been interrupted.", this.lambdaArn));
        } catch (AwlParserException var11) {
            throw com.amazonaws.stepfunctions.local.runtime.exceptions.ExecutionException.stateExecutionRuntimeException(this.stateName, this.enteredEventId, var11.getMessage());
        }

        return this.handleLambdaResponse(invokeResult, this.lambdaArn);
    }

    private StateResult handleLambdaResponse(InvokeResult invokeResult, String resource) {
        String payload = null;
        if (invokeResult.getPayload() != null) {
            payload = ResponseUtils.getLambdaPayload(invokeResult);
        }

        boolean success = invokeResult.getFunctionError() == null;
        if (success) {
            if (ResponseUtils.invalidPayload(payload)) {
                return this.exitOnFailure("States.DataLimitExceeded", String.format("The state/task '%s' returned a result with a size exceeding the maximum number of bytes service limit.", resource));
            } else {
                this.recordLambdaSucceededEvent(payload);
                String effectiveResult = this.getEffectiveResult(payload, this.taskState.getResultSelector());
                String effectiveOutput = this.getEffectiveOutput(effectiveResult);
                return this.exitOnSuccess(effectiveOutput);
            }
        } else {
            try {
                JsonNode root = this.mapper.readTree(payload);
                return this.exitOnFailure(root.get("errorType").asText(), payload);
            } catch (Exception var7) {
                Log.error("exception when deserializing lambda response:", var7);
                return this.exitOnFailure("Lambda.Unknown", "The cause could not be determined because Lambda did not return an error type.");
            }
        }
    }

    private void validateLambdaARN() {
        if (!LAMBDA_ARN_PATTERN.matcher(this.lambdaArn).matches()) {
            throw com.amazonaws.stepfunctions.local.runtime.exceptions.ExecutionException.runtimeError(String.format("An error occurred while scheduling the state '%s'. The provided ARN '%s' is invalid.", this.stateName, this.lambdaArn));
        }
    }

    private void recordLambdaScheduledEvent(String input) throws AwlParserException {
        this.lastEventId = this.executionHistoryRepo.addEvent(this.getExecutionArn(), (new HistoryEvent()).withType(HistoryEventType.LambdaFunctionScheduled).withPreviousEventId(this.lastEventId).withLambdaFunctionScheduledEventDetails((new LambdaFunctionScheduledEventDetails()).withTimeoutInSeconds(this.taskState.getTimeoutSecondsValue(this.getFilteredInput())).withInput(input).withResource(this.lambdaArn)));
    }

    private void recordLambdaStartedEvent() {
        this.lastEventId = this.executionHistoryRepo.addEvent(this.getExecutionArn(), (new HistoryEvent()).withType(HistoryEventType.LambdaFunctionStarted).withPreviousEventId(this.lastEventId));
    }

    private void recordLambdaSucceededEvent(String output) {
        this.lastEventId = this.executionHistoryRepo.addEvent(this.getExecutionArn(), (new HistoryEvent()).withType(HistoryEventType.LambdaFunctionSucceeded).withPreviousEventId(this.lastEventId).withLambdaFunctionSucceededEventDetails((new LambdaFunctionSucceededEventDetails()).withOutput(output)));
    }

    private StateResult exitOnSuccess(String output) {
        LoggingConfiguration loggingConfiguration = this.executionModel.getStateMachineModel() == null ? null : this.executionModel.getStateMachineModel().getLoggingConfiguration();
        this.lastEventId = this.executionHistoryRepo.addStateExitedEvent(this.getExecutionArn(), this.lastEventId, HistoryEventType.TaskStateExited, output, this.stateName, loggingConfiguration);
        return StateResult.builder().executionStatus(ExecutionStatus.SUCCEEDED).result(output).nextState(this.taskState.getNextState()).lastEventId(this.lastEventId).build();
    }

    private StateResult exitOnFailure(String error, String cause) {
        this.lastEventId = this.executionHistoryRepo.addEvent(this.getExecutionArn(), (new HistoryEvent()).withType(HistoryEventType.LambdaFunctionFailed).withPreviousEventId(this.lastEventId).withLambdaFunctionFailedEventDetails((new LambdaFunctionFailedEventDetails()).withError(error).withCause(cause)));
        return StateResult.builder().executionStatus(ExecutionStatus.FAILED).error(StateError.builder().cause(cause).error(error).build()).lastEventId(this.lastEventId).build();
    }

    private StateResult exitOnTimeout() {
        this.lastEventId = this.executionHistoryRepo.addEvent(this.getExecutionArn(), (new HistoryEvent()).withType(HistoryEventType.LambdaFunctionTimedOut).withPreviousEventId(this.lastEventId).withLambdaFunctionTimedOutEventDetails((new LambdaFunctionTimedOutEventDetails()).withError("States.Timeout").withCause((String)null)));
        return StateResult.builder().executionStatus(ExecutionStatus.FAILED).error(StateError.builder().error("States.Timeout").cause((String)null).build()).lastEventId(this.lastEventId).build();
    }
}
