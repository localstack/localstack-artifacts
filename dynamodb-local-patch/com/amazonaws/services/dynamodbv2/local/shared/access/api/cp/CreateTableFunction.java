package com.amazonaws.services.dynamodbv2.local.shared.access.api.cp;

import com.amazonaws.services.dynamodbv2.exceptions.AWSExceptionFactory;
import com.amazonaws.services.dynamodbv2.exceptions.AmazonServiceExceptionType;
import com.amazonaws.services.dynamodbv2.local.shared.access.LocalDBAccess;
import com.amazonaws.services.dynamodbv2.local.shared.access.LocalDBUtils;
import com.amazonaws.services.dynamodbv2.local.shared.access.LocalDBAccess.WriteLockWithTimeout;
import com.amazonaws.services.dynamodbv2.local.shared.exceptions.LocalDBClientExceptionMessage;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.CreateTableResult;
import com.amazonaws.services.dynamodbv2.model.GlobalSecondaryIndex;
import com.amazonaws.services.dynamodbv2.model.GlobalSecondaryIndexDescription;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.LocalSecondaryIndex;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.StreamSpecification;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.util.CollectionUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class CreateTableFunction extends ControlPlaneFunction<CreateTableRequest, CreateTableResult> {
    public static final int MAX_GSI_INDEXES = 100;

    public CreateTableFunction(LocalDBAccess dbAccess) {
        super(dbAccess);
    }

    public CreateTableResult apply(CreateTableRequest createTableRequest) {
        if (createTableRequest == null) {
            throw AWSExceptionFactory.buildAWSException(AmazonServiceExceptionType.INVALID_PARAMETER_VALUE, "createTableRequest was null");
        } else {
            final String tableName = createTableRequest.getTableName();
            this.validateTableName(tableName);
            List<KeySchemaElement> keySchema = createTableRequest.getKeySchema();
            this.validateKeySchema(keySchema);
            int numKeysOnBaseTable = keySchema.size();
            boolean isHashAndRangeKey = numKeysOnBaseTable == 2;
            final List<AttributeDefinition> allAttributes = createTableRequest.getAttributeDefinitions();
            this.validateAttributeDefinitions(allAttributes);
            final AttributeDefinition hashKey = LocalDBUtils.findAttributeDefinition((KeySchemaElement)keySchema.get(0), allAttributes);
            if (hashKey == null) {
                throw AWSExceptionFactory.buildAWSException(AmazonServiceExceptionType.VALIDATION_EXCEPTION, LocalDBClientExceptionMessage.NON_SPECIFIED_HASH_KEY.getMessage());
            } else {
                final AttributeDefinition rangeKey = isHashAndRangeKey ? LocalDBUtils.findAttributeDefinition((KeySchemaElement)keySchema.get(1), allAttributes) : null;
                if (isHashAndRangeKey && rangeKey == null) {
                    throw AWSExceptionFactory.buildAWSException(AmazonServiceExceptionType.VALIDATION_EXCEPTION, LocalDBClientExceptionMessage.NON_SPECIFIED_RANGE_KEY.getMessage());
                } else {
                    final List<LocalSecondaryIndex> lsiIndexes = createTableRequest.getLocalSecondaryIndexes();
                    if (!isHashAndRangeKey && lsiIndexes != null && lsiIndexes.size() > 0) {
                        throw AWSExceptionFactory.buildAWSException(AmazonServiceExceptionType.VALIDATION_EXCEPTION, LocalDBClientExceptionMessage.NO_LSI_ALLOWED.getMessage());
                    } else {
                        Set<String> lsiNames = new HashSet();
                        List<String> lsiProjAttr = new ArrayList();
                        int numLSIKeys = this.validateLSISchema(lsiIndexes, hashKey.getAttributeName(), allAttributes, rangeKey, lsiNames, lsiProjAttr);
                        final List<GlobalSecondaryIndex> gsiIndexes = createTableRequest.getGlobalSecondaryIndexes();
                        boolean isTheRequestCreatingGSIs = gsiIndexes != null;
                        if (isTheRequestCreatingGSIs) {
                            if (gsiIndexes.isEmpty()) {
                                throw AWSExceptionFactory.buildAWSException(AmazonServiceExceptionType.VALIDATION_EXCEPTION, LocalDBClientExceptionMessage.EMPTY_GSI_LIST.getMessage());
                            }

//                             if (gsiIndexes.size() > 20) {
                            if (gsiIndexes.size() > MAX_GSI_INDEXES) {
                                throw AWSExceptionFactory.buildAWSException(AmazonServiceExceptionType.VALIDATION_EXCEPTION, LocalDBClientExceptionMessage.TOO_MANY_GSI_VALIDATION_EXCEPTION.getMessage());
                            }
                        }

                        String billingModeString = createTableRequest.getBillingMode();
                        final BillingMode billingMode = StringUtils.isNotBlank(billingModeString) && BillingMode.PAY_PER_REQUEST.equals(BillingMode.fromValue(billingModeString)) ? BillingMode.PAY_PER_REQUEST : BillingMode.PROVISIONED;
                        List<GlobalSecondaryIndexDescription> gsiDescList = LocalDBUtils.getGsiDescListFrom(gsiIndexes);
                        int numGSIKeys = this.validateGSISchemas(gsiDescList, hashKey, rangeKey, allAttributes, new ArrayList(lsiNames), lsiProjAttr.size(), billingMode);
                        int maxSize = isHashAndRangeKey ? 2 + numLSIKeys + numGSIKeys : 1 + numGSIKeys;
                        if (allAttributes.size() > maxSize) {
                            throw AWSExceptionFactory.buildAWSException(AmazonServiceExceptionType.VALIDATION_EXCEPTION, LocalDBClientExceptionMessage.TOO_MANY_ATTRIBUTES.getMessage());
                        } else {
                            final List<GlobalSecondaryIndex> modifiedGsiIndexes = new ArrayList();
                            final ProvisionedThroughput throughput;
                            if (BillingMode.PROVISIONED.equals(billingMode)) {
                                throughput = createTableRequest.getProvisionedThroughput();
                                this.validateProvisionedThroughputIncrease(throughput, (ProvisionedThroughput)null);
                                this.validateProvisionedThroughputWithGSIs(tableName, throughput, gsiDescList);
                            } else {
                                throughput = ZERO_PROVISIONED_THROUGHPUT;
                                if (!CollectionUtils.isNullOrEmpty(gsiIndexes)) {
                                    modifiedGsiIndexes.addAll((Collection)gsiIndexes.stream().map(GlobalSecondaryIndex::clone).map((globalSecondaryIndex) -> {
                                        return globalSecondaryIndex.withProvisionedThroughput(ZERO_PROVISIONED_THROUGHPUT);
                                    }).collect(Collectors.toList()));
                                }
                            }

                            final StreamSpecification spec = createTableRequest.getStreamSpecification();
                            this.validateStreamSpecification(spec, (StreamSpecification)null, true);
                            (new WriteLockWithTimeout(this.dbAccess.getLockForTable(tableName), 10) {
                                public void criticalSection() {
                                    CreateTableFunction.this.validateTableNotExists(tableName);
                                    CreateTableFunction.this.dbAccess.createTable(tableName, hashKey, rangeKey, allAttributes, lsiIndexes, (List)(modifiedGsiIndexes.isEmpty() ? gsiIndexes : modifiedGsiIndexes), throughput, billingMode, spec);
                                }
                            }).execute();
                            TableDescription newTableDesc = this.getTableDescriptionHelper(tableName);
                            newTableDesc.setItemCount(0L);
                            newTableDesc.setTableSizeBytes(0L);
                            return (new CreateTableResult()).withTableDescription(newTableDesc);
                        }
                    }
                }
            }
        }
    }

    private int validateLSISchema(List<LocalSecondaryIndex> lsiList, String hashKeyName, List<AttributeDefinition> allAttributes, AttributeDefinition rangeKeyDef, Set<String> lsiNames, List<String> projAttributes) {
        if (CollectionUtils.isNullOrEmpty(lsiList)) {
            return 0;
        } else if (lsiList.isEmpty()) {
            throw AWSExceptionFactory.buildAWSException(AmazonServiceExceptionType.VALIDATION_EXCEPTION, LocalDBClientExceptionMessage.EMPTY_LSI_LIST.getMessage());
        } else if (lsiList.size() > 5) {
            throw AWSExceptionFactory.buildAWSException(AmazonServiceExceptionType.VALIDATION_EXCEPTION, LocalDBClientExceptionMessage.TOO_MANY_LSI.getMessage());
        } else {
            Set<AttributeDefinition> lsiRangeKeys = new HashSet();
            int totalProjectedAttrs = 0;
            Iterator var9 = lsiList.iterator();

            while(var9.hasNext()) {
                LocalSecondaryIndex lsi = (LocalSecondaryIndex)var9.next();
                String lsiName = lsi.getIndexName();
                this.validateTableName(lsiName);
                if (lsiNames.contains(lsiName)) {
                    throw AWSExceptionFactory.buildAWSException(AmazonServiceExceptionType.VALIDATION_EXCEPTION, LocalDBClientExceptionMessage.SAME_NAME_LSI.getMessage());
                }

                totalProjectedAttrs += this.validateProjection(lsi.getProjection(), projAttributes);
                if ((long)totalProjectedAttrs > 100L) {
                    throw AWSExceptionFactory.buildAWSException(AmazonServiceExceptionType.VALIDATION_EXCEPTION, LocalDBClientExceptionMessage.TOO_MANY_PROJECTED.getMessage());
                }

                lsiNames.add(lsiName);
                List<KeySchemaElement> lsiSchema = lsi.getKeySchema();
                this.validateKeySchema(lsiSchema);
                if (lsiSchema.size() < 2) {
                    throw AWSExceptionFactory.buildAWSException(AmazonServiceExceptionType.VALIDATION_EXCEPTION, LocalDBClientExceptionMessage.INVALID_LSI_NO_RANGE.getMessage());
                }

                KeySchemaElement lsiHashKey = (KeySchemaElement)lsiSchema.get(0);
                if (lsiHashKey != null && lsiHashKey.getAttributeName().equals(hashKeyName) && lsiHashKey.getKeyType().equals(KeyType.HASH.toString())) {
                    KeySchemaElement lsiRangeKey = (KeySchemaElement)lsiSchema.get(1);
                    if (lsiRangeKey != null && lsiRangeKey.getKeyType().equals(KeyType.RANGE.toString())) {
                        AttributeDefinition lsiRangeKeyDef = LocalDBUtils.findAttributeDefinition(lsiRangeKey, allAttributes);
                        if (lsiRangeKeyDef == null) {
                            throw AWSExceptionFactory.buildAWSException(AmazonServiceExceptionType.VALIDATION_EXCEPTION, LocalDBClientExceptionMessage.NON_SPECIFIED_LSI_RANGE_KEY.getMessage());
                        }

                        if (!lsiRangeKeyDef.equals(rangeKeyDef)) {
                            lsiRangeKeys.add(lsiRangeKeyDef);
                        }
                        continue;
                    }

                    throw AWSExceptionFactory.buildAWSException(AmazonServiceExceptionType.VALIDATION_EXCEPTION, LocalDBClientExceptionMessage.INVALID_LSI_NO_RANGE.getMessage());
                }

                throw AWSExceptionFactory.buildAWSException(AmazonServiceExceptionType.VALIDATION_EXCEPTION, LocalDBClientExceptionMessage.INVALID_LSI.getMessage());
            }

            return lsiRangeKeys.size();
        }
    }

    private void validateTableNotExists(String tableName) {
        if (this.dbAccess.getTableInfo(tableName) != null) {
            throw AWSExceptionFactory.buildAWSException(AmazonServiceExceptionType.RESOURCE_IN_USE_EXCEPTION, LocalDBClientExceptionMessage.TABLE_ALREADY_EXISTS.getMessage());
        }
    }
}
