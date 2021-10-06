
package com.amazonaws.stepfunctions.local.runtime;

import com.amazonaws.regions.Regions;
import com.amazonaws.stepfunctions.local.runtime.Properties.Build;
import com.amazonaws.stepfunctions.local.runtime.config.ConfigOption;
import com.amazonaws.stepfunctions.local.runtime.exceptions.InterruptiveArgsException;
import com.amazonaws.stepfunctions.local.runtime.exceptions.InvalidArgsException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Generated;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

@Singleton
public class Config {
    private List<ConfigOption> options;
    public static final String OPTION_REGION_DEFAULT;
    public static final String OPTION_ACCESS_KEY_DEFAULT = "abcd";
    public static final String OPTION_SECRET_KEY_DEFAULT = "1234";
    public static final String OPTION_ACCOUNT_DEFAULT = "123456789012";
    public static final int OPTION_PORT_DEFAULT = 8083;
    private static final int DISPLAY_HELP_FORMATTER_WIDTH = 1024;
    private ConfigOption optionHelp;
    private ConfigOption optionVersion;
    private ConfigOption optionAccount;
    private ConfigOption optionRegion;
    private ConfigOption optionLambdaEndpoint;
    private ConfigOption optionBatchEndpoint;
    private ConfigOption optionDynamodbEndpoint;
    private ConfigOption optionECSEndpoint;
    private ConfigOption optionGlueEndpoint;
    private ConfigOption optionSageMakerEndpoint;
    private ConfigOption optionSQSEndpoint;
    private ConfigOption optionSNSEndpoint;
    private ConfigOption optionStepFunctionsEndpoint;
    private ConfigOption optionAthenaEndpoint;
    private ConfigOption optionEKSEndpoint;
    private ConfigOption optionDataBrewEndpoint;
    private ConfigOption optionApiGatewayEndpoint;
    private ConfigOption optionEventBridgeEndpoint;
    private ConfigOption optionEMRContainersEndpoint;
    private ConfigOption optionWaitTimeScale;
    private ConfigOption optionPort;
    private final Properties properties;

    public final String getRegion() {
        return this.optionRegion.getStringValue();
    }

    public final String getAccount() {
        return this.optionAccount.getStringValue();
    }

    public final Float getWaitTimeScale() {
        return this.optionWaitTimeScale.getFloatValue();
    }

    public final Integer getPort() {
        return this.optionPort.getIntegerValue();
    }

    public final String getLambdaEndpoint() {
        return this.optionLambdaEndpoint.getStringValue();
    }

    public final String getBatchEndpoint() {
        return this.optionBatchEndpoint.getStringValue();
    }

    public final String getDynamodbEndpoint() {
        return this.optionDynamodbEndpoint.getStringValue();
    }

    public final String getECSEndpoint() {
        return this.optionECSEndpoint.getStringValue();
    }

    public final String getGlueEndpoint() {
        return this.optionGlueEndpoint.getStringValue();
    }

    public final String getSageMakerEndpoint() {
        return this.optionSageMakerEndpoint.getStringValue();
    }

    public final String getSQSEndpoint() {
        return this.optionSQSEndpoint.getStringValue();
    }

    public final String getSNSEndpoint() {
        return this.optionSNSEndpoint.getStringValue();
    }

    public final String getStepFunctionsEndpoint() {
        return this.optionStepFunctionsEndpoint.getStringValue();
    }

    public final String getAthenaEndpoint() {
        return this.optionAthenaEndpoint.getStringValue();
    }

    public final String getEKSEndpoint() {
        return this.optionEKSEndpoint.getStringValue();
    }

    public final String getDataBrewEndpoint() {
        return this.optionDataBrewEndpoint.getStringValue();
    }

    public final String getApiGatewayEndpoint() {
        return this.optionApiGatewayEndpoint.getStringValue();
    }

    public final String getEventBridgeEndpoint() {
        return this.optionEventBridgeEndpoint.getStringValue();
    }

    public final String getEMRContainersEndpoint() {
        return this.optionEMRContainersEndpoint.getStringValue();
    }

    @Inject
    public Config(Properties properties) {
        this.properties = properties;
        this.optionAccount = new ConfigOption("Account", "account", "aws-account", "AWS_ACCOUNT_ID", "the AWS account used to create state machines, activities and executions,\nthis is also the account of your Lambda and other resources.\nBy Default, it is set to [123456789012], this is NOT a real account id.", "123456789012");
        this.optionRegion = new ConfigOption("Region", "region", "aws-region", "AWS_DEFAULT_REGION", "the region where the state machines, activities and executions will be created,\nthis is also the region of other AWS resources referred in the state machine.\nBy Default, it is set to [" + OPTION_REGION_DEFAULT + "].", Regions.US_EAST_1.getName());
        this.optionHelp = new ConfigOption("Help", "h", "help", (String)null, "Show this help information.", (Object)null);
        this.optionVersion = new ConfigOption("Version", "v", "version", (String)null, "Show the version and build of Step Functions Local.", (Object)null);
        this.optionLambdaEndpoint = new ConfigOption("Lambda Endpoint", "the local endpoint of Lambda.\ne.g. http://localhost:4574", "");
        this.optionBatchEndpoint = new ConfigOption("Batch Endpoint", "the local endpoint of Batch.\ne.g. http://localhost:4574", "");
        this.optionDynamodbEndpoint = new ConfigOption("DynamoDB Endpoint", "the local endpoint of DynamoDB.\ne.g. http://localhost:4574", "");
        this.optionECSEndpoint = new ConfigOption("ECS Endpoint", "the local endpoint of ECS.\ne.g. http://localhost:4574", "");
        this.optionGlueEndpoint = new ConfigOption("Glue Endpoint", "the local endpoint of Glue.\ne.g. http://localhost:4574", "");
        this.optionSageMakerEndpoint = new ConfigOption("SageMaker Endpoint", "the local endpoint of SageMaker.\ne.g. http://localhost:4574", "");
        this.optionSQSEndpoint = new ConfigOption("SQS Endpoint", "the local endpoint of SQS.\ne.g. http://localhost:4574", "");
        this.optionSNSEndpoint = new ConfigOption("SNS Endpoint", "the local endpoint of SNS.\ne.g. http://localhost:4574", "");
        this.optionStepFunctionsEndpoint = new ConfigOption("Step Functions Endpoint", "the local endpoint of Step Functions.\ne.g. http://localhost:4574", "");
        this.optionAthenaEndpoint = new ConfigOption("Step Functions Athena", "the local endpoint of Athena.\ne.g. http://localhost:4574", "");
        this.optionEKSEndpoint = new ConfigOption("Step Functions EKS", "the local endpoint of EKS.\ne.g. http://localhost:4574", "");
        this.optionDataBrewEndpoint = new ConfigOption("Step Functions DataBrew", "the local endpoint of DataBrew.\ne.g. http://localhost:4574", "");
        this.optionApiGatewayEndpoint = new ConfigOption("API Gateway Endpoint", "the local endpoint of API Gateway.\ne.g. http://localhost:4574", "");
        this.optionEventBridgeEndpoint = new ConfigOption("EventBridge Endpoint", "the local endpoint of EventBridge.\ne.g. http://localhost:4574", "");
        this.optionEMRContainersEndpoint = new ConfigOption("EMR on EKS Endpoint", "the local endpoint of EMR on EKS.\ne.g. http://localhost:4574", "");
        this.optionWaitTimeScale = new ConfigOption("Wait Time Scale", "the scale of the wait time in the Wait state\ne.g. 0.5 means cut the original wait time to half\ne.g. 0 means no wait time\ne.g. 2 means double the original wait time", (Object)null);
        this.optionPort = new ConfigOption("Port", "the port to listen on.\nBy Default, it is set to [8083].", 8083);
        this.options = Arrays.asList(this.optionHelp, this.optionVersion, this.optionAccount, this.optionRegion, this.optionLambdaEndpoint, this.optionBatchEndpoint, this.optionDynamodbEndpoint, this.optionECSEndpoint, this.optionGlueEndpoint, this.optionSageMakerEndpoint, this.optionSQSEndpoint, this.optionSNSEndpoint, this.optionStepFunctionsEndpoint, this.optionAthenaEndpoint, this.optionEKSEndpoint, this.optionDataBrewEndpoint, this.optionEMRContainersEndpoint,
                this.optionEventBridgeEndpoint,  // Note: added by whummer - TODO remove this patch once available upstream!
                this.optionWaitTimeScale, this.optionPort);
    }

    public void parseArgs(String[] args) throws InvalidArgsException, InterruptiveArgsException {
        Options cmdOptions = new Options();
        this.options.forEach((optionx) -> {
            OptionBuilder.withArgName(optionx.getName());
            OptionBuilder.hasArgs(optionx.getNumOfArgs());
            OptionBuilder.withDescription(optionx.getDescription());
            OptionBuilder.withLongOpt(optionx.getCmdLongName());
            cmdOptions.addOption(OptionBuilder.create(optionx.getCmdShortName()));
        });
        BasicParser parser = new BasicParser();

        try {
            CommandLine line = parser.parse(cmdOptions, args);
            Iterator var5 = this.options.iterator();

            while(var5.hasNext()) {
                ConfigOption option = (ConfigOption)var5.next();
                if (line.hasOption(option.getCmdShortName())) {
                    if (option == this.optionHelp) {
                        this.showHelp(cmdOptions);
                        throw new InterruptiveArgsException();
                    }

                    if (option == this.optionVersion) {
                        this.printVersion();
                        throw new InterruptiveArgsException();
                    }

                    if (option.hasArg()) {
                        String value = line.getOptionValue(option.getCmdShortName());
                        Log.info("Configure [%s] to [%s]", new Object[]{option.getName(), value});
                        if (option == this.optionWaitTimeScale) {
                            this.setWaitTimeScale(value);
                        } else {
                            option.setValue(value);
                        }
                    }
                }
            }

        } catch (ParseException var8) {
            this.showHelp(cmdOptions);
            throw new InvalidArgsException(var8.getMessage());
        }
    }

    private void setWaitTimeScale(String timeScale) {
        try {
            Float newWaitTimeScale = new Float(timeScale);
            newWaitTimeScale = Math.abs(newWaitTimeScale);
            this.optionWaitTimeScale.setValue(newWaitTimeScale);
            Log.info("Scale of wait time is set to %f", new Object[]{newWaitTimeScale});
        } catch (NumberFormatException var3) {
            Log.error("%s is not a valid scale because %s", new Object[]{timeScale, var3.getMessage()});
        }

    }

    public void readEnv() {
        Map<String, String> envs = System.getenv();
        this.options.stream().filter((option) -> {
            return option.getEnvName() != null && option.hasArg();
        }).forEach((option) -> {
            if (envs.containsKey(option.getEnvName())) {
                String name = option.getEnvName();
                String value = (String)envs.get(name);
                Log.info("Configure [%s] to [%s]", new Object[]{name, value});
                if (option == this.optionWaitTimeScale) {
                    this.setWaitTimeScale(value);
                } else {
                    option.setValue(value);
                }
            }

        });
    }

    private void showHelp(Options options) {
        String header = "Start a Step Functions Local server\n\n";
        String footer = this.properties.getHelp();
        HelpFormatter formatter = new HelpFormatter();
        formatter.setWidth(1024);
        formatter.printHelp("java -jar StepFunctionsLocal.jar", header, options, footer);
    }

    public void printVersion() {
        Build build = this.properties.getBuild();
        System.out.println("Step Functions Local");
        System.out.println(String.format("Version: %s.%s.%s", build.getMajor(), build.getMinor(), build.getPatch()));
        System.out.println(String.format("Build: %s", build.getDate()));
        if (!build.getCommit().isEmpty()) {
            System.out.println(String.format("Commit: %s", build.getCommit()));
        }

    }

    @Generated
    public ConfigOption getOptionHelp() {
        return this.optionHelp;
    }

    @Generated
    public ConfigOption getOptionVersion() {
        return this.optionVersion;
    }

    @Generated
    public ConfigOption getOptionAccount() {
        return this.optionAccount;
    }

    @Generated
    public ConfigOption getOptionRegion() {
        return this.optionRegion;
    }

    @Generated
    public ConfigOption getOptionLambdaEndpoint() {
        return this.optionLambdaEndpoint;
    }

    @Generated
    public ConfigOption getOptionBatchEndpoint() {
        return this.optionBatchEndpoint;
    }

    @Generated
    public ConfigOption getOptionDynamodbEndpoint() {
        return this.optionDynamodbEndpoint;
    }

    @Generated
    public ConfigOption getOptionECSEndpoint() {
        return this.optionECSEndpoint;
    }

    @Generated
    public ConfigOption getOptionGlueEndpoint() {
        return this.optionGlueEndpoint;
    }

    @Generated
    public ConfigOption getOptionSageMakerEndpoint() {
        return this.optionSageMakerEndpoint;
    }

    @Generated
    public ConfigOption getOptionSQSEndpoint() {
        return this.optionSQSEndpoint;
    }

    @Generated
    public ConfigOption getOptionSNSEndpoint() {
        return this.optionSNSEndpoint;
    }

    @Generated
    public ConfigOption getOptionStepFunctionsEndpoint() {
        return this.optionStepFunctionsEndpoint;
    }

    @Generated
    public ConfigOption getOptionAthenaEndpoint() {
        return this.optionAthenaEndpoint;
    }

    @Generated
    public ConfigOption getOptionEKSEndpoint() {
        return this.optionEKSEndpoint;
    }

    @Generated
    public ConfigOption getOptionDataBrewEndpoint() {
        return this.optionDataBrewEndpoint;
    }

    @Generated
    public ConfigOption getOptionApiGatewayEndpoint() {
        return this.optionApiGatewayEndpoint;
    }

    @Generated
    public ConfigOption getOptionEventBridgeEndpoint() {
        return this.optionEventBridgeEndpoint;
    }

    @Generated
    public ConfigOption getOptionEMRContainersEndpoint() {
        return this.optionEMRContainersEndpoint;
    }

    @Generated
    public ConfigOption getOptionWaitTimeScale() {
        return this.optionWaitTimeScale;
    }

    @Generated
    public ConfigOption getOptionPort() {
        return this.optionPort;
    }

    static {
        OPTION_REGION_DEFAULT = Regions.US_EAST_1.getName();
    }
}
