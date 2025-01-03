package br.com.devblack21.dynamodb.manager4j.writer.simple.sync;

import br.com.devblack21.dynamodb.manager4j.configuration.WriteRetryPolicyConfiguration;
import br.com.devblack21.dynamodb.manager4j.interceptor.RequestInterceptor;
import br.com.devblack21.dynamodb.manager4j.model.TableEntity;
import br.com.devblack21.dynamodb.manager4j.model.UnprocessedItem;
import br.com.devblack21.dynamodb.manager4j.transform.FailedBatchPutRequestTransformer;
import br.com.devblack21.dynamodb.manager4j.writer.simple.BatchSaveManager;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;

import java.util.List;

public class BatchSaveManagerSync extends AbstractSyncBatchWriter implements BatchSaveManager {

  private final DynamoDBMapper dynamoDBMapper;
  private final FailedBatchPutRequestTransformer<? extends TableEntity> failedBatchTransformer;

  private BatchSaveManagerSync(final Builder builder) {
    super(builder.retryPolicyConfiguration, builder.requestInterceptor);
    this.dynamoDBMapper = builder.dynamoDBMapper;
    this.failedBatchTransformer = builder.failedBatchTransformer;
  }

  @Override
  public void batchSave(final List<? extends TableEntity> entities) {
    this.execute(entities);
  }

  @Override
  protected List<UnprocessedItem> executor(final List<? extends TableEntity> entities) {
    try {
      return UnprocessedItem.unprocessedItems(this.failedBatchTransformer.transform(dynamoDBMapper.batchSave(entities)));
    } catch (final Exception e) {
      return UnprocessedItem.unprocessedItems(entities);
    }
  }

  public static class Builder {
    private final DynamoDBMapper dynamoDBMapper;
    private final FailedBatchPutRequestTransformer<? extends TableEntity> failedBatchTransformer;
    private WriteRetryPolicyConfiguration retryPolicyConfiguration;
    private RequestInterceptor requestInterceptor;

    public Builder(final DynamoDBMapper dynamoDBMapper, final FailedBatchPutRequestTransformer<? extends TableEntity> failedBatchTransformer) {
      this.dynamoDBMapper = dynamoDBMapper;
      this.failedBatchTransformer = failedBatchTransformer;
      validate();
    }

    public Builder retryPolicyConfiguration(final WriteRetryPolicyConfiguration retryPolicyConfiguration) {
      this.retryPolicyConfiguration = retryPolicyConfiguration;
      return this;
    }

    public Builder requestInterceptor(final RequestInterceptor requestInterceptor) {
      this.requestInterceptor = requestInterceptor;
      return this;
    }

    public BatchSaveManagerSync build() {
      if (dynamoDBMapper == null || failedBatchTransformer == null) {
        throw new IllegalStateException("DynamoDBMapper and FailedBatchTransformer are required");
      }
      return new BatchSaveManagerSync(this);
    }


    private void validate() {
      if (dynamoDBMapper == null || failedBatchTransformer == null) {
        throw new IllegalArgumentException("DynamoDBMapper e FailedBatchTransformer são obrigatórios.");
      }
    }
  }
}