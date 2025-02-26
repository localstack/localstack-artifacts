package cloud.localstack.authz;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tinkerpop.gremlin.process.traversal.Bytecode;
import org.apache.tinkerpop.gremlin.process.traversal.Bytecode.Instruction;
import org.apache.tinkerpop.gremlin.server.auth.AuthenticatedUser;
import org.apache.tinkerpop.gremlin.server.authz.AuthorizationException;
import org.apache.tinkerpop.gremlin.server.authz.Authorizer;
import org.apache.tinkerpop.gremlin.util.Tokens;
import org.apache.tinkerpop.gremlin.util.message.RequestMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cloud.localstack.auth.IamAuthenticator;
import cloud.localstack.localstack_client.ActionResult;
import cloud.localstack.localstack_client.CheckActionAllowedResponse;
import cloud.localstack.localstack_client.LocalStackClient;

public class IamAuthorizer implements Authorizer {
    // These steps were taken by inspecting the steps that implemented `package
    // org.apache.tinkerpop.gremlin.process.traversal.step.Writing`
    // and `package org.apache.tinkerpop.gremlin.process.traversal.step.Deleting`. A
    // better solution in the future could use the translator
    // to convert to the acgtual steps and verify if they subclass Writing or
    // Deleting
    private static final List<String> WRITE_STEPS = new ArrayList<>(
            List.of("addE", "addV", "mergeV", "mergeE", "property"));
    private static final List<String> DELETE_STEPS = new ArrayList<>(
            List.of("drop", "mergeV", "mergeE", "property"));
    private static final Pattern pattern = Pattern.compile("\\.(\\w+)\\(");

    private static final String READ_ACTION = "neptune-db:ReadDataViaQuery";
    private static final String WRITE_ACTION = "neptune-db:WriteDataViaQuery";
    private static final String DELETE_ACTION = "neptune-db:DeleteDataViaQuery";

    private static final Logger logger = LoggerFactory.getLogger(IamAuthenticator.class);

    private static boolean enforceIam;
    private static String regionName;
    private static String resourceArn;

    private static LocalStackClient localStackClient;

    @Override
    public void setup(Map<String, Object> config) throws AuthorizationException {
        logger.info("Initializing authorization with the {}", IamAuthenticator.class.getName());
        enforceIam = Boolean.parseBoolean(config.get("EnforceIam").toString());
        regionName = config.get("regionName").toString();
        resourceArn = config.get("resourceArn").toString();

        localStackClient = new LocalStackClient(config.get("localstackHost").toString());
        logger.info("enforceIam: {}", enforceIam);
    }

    @Override
    public Bytecode authorize(AuthenticatedUser user, Bytecode bytecode, Map<String, String> aliases)
            throws AuthorizationException {

        boolean containsWrite = false;
        boolean containsDelete = false;

        for (final Instruction instruction : bytecode.getStepInstructions()) {
            final String operator = instruction.getOperator();
            if (WRITE_STEPS.contains(operator)) {
                containsWrite = true;
            }
            if (DELETE_STEPS.contains(operator)) {
                containsDelete = true;
            }
            if (containsDelete && containsWrite) {
                // Leave early if both delete and write were found
                break;
            }
        }
        validatePermission(user, containsWrite, containsDelete);
        return bytecode;
    }

    @Override
    public void authorize(AuthenticatedUser user, RequestMessage msg) throws AuthorizationException {
        // Since we don't have access to the ByteCode, we will use a regex to identify
        // all operators.
        // This solution isn't ideal and finding the way to convert the string into
        // ByteCode would be preferable,
        // but this should do for now.
        String gremlinArg = msg.getArg(Tokens.ARGS_GREMLIN);
        Matcher matcher = pattern.matcher(gremlinArg);

        Boolean foundAll = false;
        Boolean containsWrite = false;
        Boolean containsDelete = false;

        while (matcher.find() && !foundAll) {
            String operator = matcher.group(1);
            if (WRITE_STEPS.contains(operator)) {
                containsWrite = true;
            }
            if (DELETE_STEPS.contains(operator)) {
                containsDelete = true;
            }
            if (containsDelete && containsWrite) {
                // Leave early if both delete and write were found
                foundAll = true;
            }
        }
        validatePermission(user, containsWrite, containsDelete);
    }

    private void validatePermission(AuthenticatedUser user, boolean containsWrite, boolean containsDelete)
            throws AuthorizationException {

        List<String> actions = new ArrayList<>();
        if (containsDelete) {
            actions.add(DELETE_ACTION);
        }
        if (containsWrite) {
            actions.add(WRITE_ACTION);
        }
        // Read action seems to always be present?
        actions.add(READ_ACTION);
        logger.info("authorizing actions: {}", actions.toString());

        try {
            CheckActionAllowedResponse response = localStackClient.evaluatePermission(user.getName(), regionName,
                    resourceArn, actions);
            if (!response.allowed && enforceIam) {
                throw new AuthorizationException(buildUnauthorizeResponseMessage(response, actions));
            }
        } catch (Exception e) {
            if (e.getClass().getName() == AuthorizationException.class.getName()) {
                throw e;
            }
            logger.error("unhandled error in the authorizer", e);
            throw new AuthorizationException("Authorizer server error");
        }

    }

    private String buildUnauthorizeResponseMessage(CheckActionAllowedResponse response, List<String> actions) {
        if (response.explicitDeny.size() == 0 && response.implicitDeny.size() == 0) {
            return "Forbidden.";
        }
        List<String> deniedAction = new ArrayList<String>();

        for (ActionResult action : response.explicitDeny) {
            deniedAction.add(action.action);
        }
        for (ActionResult action : response.implicitDeny) {
            deniedAction.add(action.action);
        }
        String actionString = deniedAction.toString().replace("[", "").replace("]", "");
        return "User: " + response.source_principal.arn + " is not authorized to perform: "
                + actionString + " on resource: " + resourceArn;
    }
}
