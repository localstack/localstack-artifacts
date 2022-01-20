package cloud.localstack;


import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.amazonaws.services.stepfunctions.model.*;
import com.amazonaws.states.connectors.ServiceError;
import com.amazonaws.stepfunctions.local.repo.ExecutionRepo;
import com.amazonaws.stepfunctions.local.runtime.executors.task.external.AsyncAPIContext;
import com.amazonaws.stepfunctions.local.runtime.executors.task.external.AsyncAPIResult;
import com.amazonaws.stepfunctions.local.runtime.executors.task.external.Poller;
import com.amazonaws.stepfunctions.local.runtime.executors.task.external.SyncServiceAPI;
import com.amazonaws.stepfunctions.local.util.Arn.ArnUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.inject.Inject;

public class DescribeExecutionParsed extends SyncServiceAPI<DescribeExecutionRequest, DescribeExecutionResult> implements Poller {
    private final AWSStepFunctions sfn;
    private final ObjectMapper mapper;
    private final ExecutionRepo executionRepo;

    @Inject
    public DescribeExecutionParsed(AWSStepFunctions sfn, ObjectMapper mapper, ExecutionRepo executionRepo) {
        super(DescribeExecutionRequest.class, DescribeExecutionResult.class);
        this.sfn = sfn;
        this.mapper = mapper;
        this.executionRepo = executionRepo;
    }

    public String serviceName() {
        return "states";
    }

    public String getServiceNameInError() {
        return "StepFunctions";
    }

    public DescribeExecutionResult invoke(DescribeExecutionRequest describeExecutionRequest) {
        return ArnUtils.getStateMachineTypeFromExecutionArn(describeExecutionRequest.getExecutionArn()) == StateMachineType.EXPRESS ? this.executionRepo.pollExpressExecutionModel(describeExecutionRequest.getExecutionArn()) : this.sfn.describeExecution(describeExecutionRequest);
    }

    public AsyncAPIResult poll(AsyncAPIContext context) throws ServiceError {
        StartExecutionResult result = (StartExecutionResult)context.getResult();
        String executionArn = result.getExecutionArn();
        DescribeExecutionResult executionResult = this.invoke((new DescribeExecutionRequest()).withExecutionArn(executionArn));
        String status = executionResult.getStatus();
        if (status.equals(ExecutionStatus.RUNNING.toString())) {
            return AsyncAPIResult.createRunning();
        } else {
            return status.equals(ExecutionStatus.SUCCEEDED.toString()) ? AsyncAPIResult.createSuccess(this.serializeJobDetail(executionResult)) : AsyncAPIResult.createFailure(this.serializeJobDetail(executionResult));
        }
    }

    private String serializeJobDetail(DescribeExecutionResult executionResult) throws ServiceError {
        try {
            this.mapper.setPropertyNamingStrategy(PropertyNamingStrategy.UPPER_CAMEL_CASE);
            String result = this.mapper.writeValueAsString(executionResult);
            ObjectNode jsonNode = (ObjectNode) this.mapper.readTree(result);
            String oldInput = jsonNode.get("Input").textValue();
            String oldOutput = jsonNode.get("Output").textValue();
            JsonNode parsedInput = this.mapper.readTree(oldInput);
            JsonNode parsedOutput = this.mapper.readTree(oldOutput);
            jsonNode.replace("Input", parsedInput);
            jsonNode.replace("Output", parsedOutput);
            return jsonNode.toString();
        } catch (Exception var3) {
            throw new ServiceError(String.format("Unable to serialize %s into json string.", executionResult.getClass().getSimpleName()));
        }
    }
}
