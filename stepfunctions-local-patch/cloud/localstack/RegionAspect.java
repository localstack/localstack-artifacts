package cloud.localstack;

import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.amazonaws.stepfunctions.local.dagger.DaggerSfnLocalComponent;
import com.amazonaws.stepfunctions.local.dagger.SfnLocalComponent;
import com.amazonaws.stepfunctions.local.http.HttpRequestHandlers;
import com.amazonaws.stepfunctions.local.runtime.exceptions.InterruptiveArgsException;
import com.amazonaws.stepfunctions.local.runtime.exceptions.InvalidArgsException;
import com.amazonaws.stepfunctions.local.repo.ExecutionRepo;
import com.amazonaws.stepfunctions.local.runtime.executors.task.external.*;
import com.amazonaws.stepfunctions.local.runtime.executors.task.external.states.DescribeExecution;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

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
}
