package cloud.localstack;

import com.amazonaws.stepfunctions.local.dagger.DaggerSfnLocalComponent;
import com.amazonaws.stepfunctions.local.dagger.SfnLocalComponent;
import com.amazonaws.stepfunctions.local.http.HttpRequestHandlers;
import com.amazonaws.stepfunctions.local.runtime.exceptions.InterruptiveArgsException;
import com.amazonaws.stepfunctions.local.runtime.exceptions.InvalidArgsException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

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

}
