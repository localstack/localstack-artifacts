package cloud.localstack;

import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.amazonaws.stepfunctions.local.dagger.DaggerSfnLocalComponent;
import com.amazonaws.stepfunctions.local.dagger.SfnLocalComponent;
import com.amazonaws.stepfunctions.local.http.HttpRequestHandlers;
import com.amazonaws.stepfunctions.local.runtime.Config;
import com.amazonaws.stepfunctions.local.runtime.exceptions.InterruptiveArgsException;
import com.amazonaws.stepfunctions.local.runtime.exceptions.InvalidArgsException;
import com.amazonaws.stepfunctions.local.repo.ExecutionRepo;
import com.amazonaws.stepfunctions.local.runtime.executors.task.external.*;
import com.amazonaws.stepfunctions.local.runtime.executors.task.external.apigateway.ApiGatewayInvokeCaller;
import com.amazonaws.stepfunctions.local.runtime.executors.task.external.apigateway.ApiGatewayInvokeRequest;
import com.amazonaws.stepfunctions.local.runtime.executors.task.external.states.DescribeExecution;
import com.amazonaws.stepfunctions.local.util.ApiEndpointParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.amazonaws.stepfunctions.local.runtime.executors.StateExecutor;
import com.amazonaws.stepfunctions.local.runtime.executors.task.TaskStateExecutor;
import com.amazonaws.swf.auth.arn.ARN;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import software.amazon.awssdk.utils.http.SdkHttpUtils;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

// TODO: might make sense to rename the aspect at some point since it doesn't only handle region customizations now
@Aspect
public class RegionAspect {

    // maps region names to RequestHandlers instances
    static final Map<String, SfnLocalComponent> COMPONENT_PER_REGION = new HashMap<>();

    static SfnLocalComponent getComponentForRegion(String region) throws InterruptiveArgsException, InvalidArgsException {
        SfnLocalComponent component = COMPONENT_PER_REGION.get(region);
        if (component == null) {
            component = DaggerSfnLocalComponent.builder().build();

            // initialize config from cmd line args
            component.config().parseArgs(StepFunctionsStarter.ARGS);

            // adjust region in handler
            component.config().getOptionRegion().setValue(region);
            COMPONENT_PER_REGION.put(region, component);
        }
        return component;
    }

    @Around("execution(* com.amazonaws..HttpRequestHandlers.handle(..))")
    public void aroundHttpHandle(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpRequestHandlers httpHandlers = (HttpRequestHandlers) joinPoint.getTarget();
        Field f = httpHandlers.getClass().getDeclaredField("requestHandler");
        f.setAccessible(true);

        // extract region from request
        HttpServletRequest request = (HttpServletRequest) joinPoint.getArgs()[2];
        String authHeader = request.getHeader("Authorization");
        String region = authHeader.split("Credential=")[1].split("/")[2];

        // determine request handler for region
        SfnLocalComponent component = getComponentForRegion(region);

        // update requestHandler for this request, then proceed with invocation
        f.set(httpHandlers, component.requestHandlers());
        joinPoint.proceed();
    }

    @Around("execution(* com.amazonaws..Service_Factory.get(..))")
    public Service afterServiceConstructor(ProceedingJoinPoint joinPoint) throws Throwable {
        Service service = (Service) joinPoint.proceed(joinPoint.getArgs());
        AsyncServiceAPI asyncServiceAPI = (AsyncServiceAPI) service.getAPI("states", "startExecution.sync");
        DescribeExecution de = (DescribeExecution) asyncServiceAPI.getPoller();
        Field f1 = de.getClass().getDeclaredField("sfn");
        Field f2 = de.getClass().getDeclaredField("mapper");
        Field f3 = de.getClass().getDeclaredField("executionRepo");
        f1.setAccessible(true);
        f2.setAccessible(true);
        f3.setAccessible(true);
        AWSStepFunctions sfn = (AWSStepFunctions) f1.get(de);
        ObjectMapper mapper = (ObjectMapper) f2.get(de);
        ExecutionRepo executionRepo = (ExecutionRepo) f3.get(de);
        DescribeExecutionParsed de2 = new DescribeExecutionParsed(sfn, mapper, executionRepo);
        service.register(new Async2ServiceApi(asyncServiceAPI.getCaller(), de2));
        return service;
    }

    @Around("execution(* com.amazonaws..TaskStateExecutor.createExecutor(..))")
    public StateExecutor aroundCreateExecutor(ProceedingJoinPoint joinPoint) throws Throwable {
        TaskStateExecutor taskStateExecutor = (TaskStateExecutor) joinPoint.getTarget();
        Field f = taskStateExecutor.getClass().getDeclaredField("localComponent");
        f.setAccessible(true);
        ARN resourceArn = (ARN) joinPoint.getArgs()[0];
        SfnLocalComponent component = COMPONENT_PER_REGION.get(resourceArn.getRegion());

        if (Objects.equals(resourceArn.getResourceType(), "aws-sdk")) {
            return component.lambdaTaskStateExecutor();
        } else {
            return (StateExecutor) joinPoint.proceed(joinPoint.getArgs());
        }
    }

    /* ============================================ */
    /* ============ APIGATEWAY PATCHES ============ */
    /* ============================================ */

    @Around("execution(static * com.amazonaws..ApiEndpointParser.isApiValid(..))")
    public boolean aroundIsApiValid(ProceedingJoinPoint joinPoint) throws Throwable {
        String[] parts = (String[]) joinPoint.getArgs()[0];

        if (parts.length == 1) {
            // http://localhost:4566/....
            return parts[0].startsWith("localhost");
        } else if (parts.length == 6 && Objects.equals(parts[4], "localstack")) {
            // http(s)://<restapi-id>.execute-api.us-east-1.localhost.localstack.cloud/...
            return true;
        } else if (parts.length == 5 && Objects.equals(parts[3], "localstack")) {
            // http(s)://<restapi-id>.execute-api.localhost.localstack.cloud/...
            return true;
        }

        return (boolean) joinPoint.proceed(joinPoint.getArgs());
    }

    /**
     * This just needs to return *some* string for now (won't be used later on)
     */
    @Around("execution(* com.amazonaws..ApiGatewayInvokeCaller.parseRegionFromEndpoint(..))")
    public String aroundParseRegionFromEndpoint(ProceedingJoinPoint joinPoint) throws Throwable {
        return "replaceme";
    }

    /**
     *  We're using the config in the arguments to set the region argument
     *  This would otherwise be the "replaceme" string from above
     */
    @Around("execution(* com.amazonaws..ApiGatewayInvokeCaller.signRequestWithSigV4IfNecessary(..))")
    public String aroundSignRequestWithSigV4IfNecessary(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        Config config = (Config) joinPoint.getArgs()[3];
        args[2] = config.getRegion();
        return (String) joinPoint.proceed(args);
    }

    /**
     * Patches the generated URLs for the localstack specific URLS (localhost  or ... localhost.localstack.cloud)
     */
    @Around("execution(* com.amazonaws..ApiGatewayInvokeCaller.createUrl(..))")
    public URI aroundCreateUrl(ProceedingJoinPoint joinPoint) throws Throwable {
        ApiGatewayInvokeRequest invokeRequest = (ApiGatewayInvokeRequest) joinPoint.getArgs()[0];
        String endpoint = invokeRequest.getApiEndpoint();

        if (endpoint.contains("localhost:4566")) {
            // remove region for localstack
            StringBuilder urlBuilder = (new StringBuilder("http://")).append(endpoint);
            if (invokeRequest.getStage() != null) {
                urlBuilder.append("/").append(SdkHttpUtils.urlEncodeIgnoreSlashes(invokeRequest.getStage()));
            }

            if (invokeRequest.getPath() != null) {
                String path = StringUtils.removeStart(invokeRequest.getPath(), "/");
                path = StringUtils.removeEnd(path, "/");
                urlBuilder.append("/").append(SdkHttpUtils.urlEncodeIgnoreSlashes(path));
            }

            return URI.create(urlBuilder.toString());
        } else {
            // remove region for localstack
            int start = endpoint.indexOf(".", endpoint.indexOf(".") + 1);
            int end = endpoint.indexOf(".", start + 1);
            String endpointWithoutRegion = endpoint.substring(0, start) + endpoint.substring(end);

            StringBuilder urlBuilder = (new StringBuilder("https://")).append(endpointWithoutRegion).append(":4566");
            if (invokeRequest.getStage() != null) {
                urlBuilder.append("/").append(SdkHttpUtils.urlEncodeIgnoreSlashes(invokeRequest.getStage()));
            }

            if (invokeRequest.getPath() != null) {
                String path = StringUtils.removeStart(invokeRequest.getPath(), "/");
                path = StringUtils.removeEnd(path, "/");
                urlBuilder.append("/").append(SdkHttpUtils.urlEncodeIgnoreSlashes(path));
            }

            return URI.create(urlBuilder.toString());
        }
    }

}
