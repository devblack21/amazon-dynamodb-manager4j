package br.com.devblack21.dynamodb.resilience.writer.sync;

import br.com.devblack21.dynamodb.resilience.backoff.BackoffExecutor;
import br.com.devblack21.dynamodb.resilience.backoff.ErrorRecoverer;
import br.com.devblack21.dynamodb.resilience.factory.BatchDeleteClientFactory;
import br.com.devblack21.dynamodb.resilience.interceptor.RequestInterceptor;
import br.com.devblack21.dynamodb.resilience.writer.DynamoDbResilienceBatchDelete;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.ExecutionException;

import static org.mockito.Mockito.*;

class DynamoDbResilienceBatchDeleteSyncTest {

  private DynamoDBMapper dynamoDBMapper;
  private BackoffExecutor mockBackoffExecutor;
  private ErrorRecoverer<Object> mockErrorRecoverer;
  private RequestInterceptor<Object> mockRequestInterceptor;
  private DynamoDbResilienceBatchDelete<Object> testWriter;
  private DynamoDbResilienceBatchDelete<Object> testWriterWithoutBackoffAndRecoverer;

  @BeforeEach
  void setUp() {
    dynamoDBMapper = mock(DynamoDBMapper.class);
    mockBackoffExecutor = mock(BackoffExecutor.class);
    mockErrorRecoverer = mock(ErrorRecoverer.class);
    mockRequestInterceptor = mock(RequestInterceptor.class);

    testWriter = BatchDeleteClientFactory.createSyncClient(
      dynamoDBMapper,
      mockBackoffExecutor,
      mockErrorRecoverer,
      mockRequestInterceptor
    );

    testWriterWithoutBackoffAndRecoverer = BatchDeleteClientFactory.createSyncClient(
      dynamoDBMapper,
      null,
      null,
      mockRequestInterceptor
    );
  }

  @Test
  void shouldExecuteSuccessfullyWithoutErrors() {
    final Object entity = new Object();

    testWriter.batchDelete(entity);

    verifyNoInteractions(mockBackoffExecutor, mockErrorRecoverer);
    verify(mockRequestInterceptor, times(1)).logSuccess(entity);
    verify(mockRequestInterceptor, never()).logError(any(), any());
  }

  @Test
  void shouldRetryOnFailure() throws ExecutionException, InterruptedException {
    final Object entity = new Object();

    simulateDynamoDbFailure();
    captureRunnableForRetry();

    testWriter.batchDelete(entity);


    verify(mockBackoffExecutor, times(1)).execute(any(Runnable.class));
    verifyNoInteractions(mockErrorRecoverer);
    verify(mockRequestInterceptor, never()).logError(eq(entity), any());


  }

  @Test
  void shouldRecoverOnFailureWhenBackoffExecutorFails() throws ExecutionException, InterruptedException {
    final Object entity = new Object();

    simulateDynamoDbFailure();
    simulateBackoffFailure();

    testWriter.batchDelete(entity);


    verify(mockBackoffExecutor, times(1)).execute(any(Runnable.class));
    verify(mockErrorRecoverer, times(1)).recover(entity);
    verify(mockRequestInterceptor, times(1)).logError(any(Object.class), any(RuntimeException.class));

  }

  @Test
  void shouldLogErrorWhenRecoveryFails() throws ExecutionException, InterruptedException {
    final Object entity = new Object();

    simulateDynamoDbFailure();
    simulateBackoffFailure();
    simulateRecoveryFailure();

    Assertions.assertThrows(RuntimeException.class, () -> testWriter.batchDelete(entity));


    verify(mockBackoffExecutor, times(1)).execute(any(Runnable.class));
    verify(mockErrorRecoverer, times(1)).recover(entity);
    verify(mockRequestInterceptor, never()).logError(eq(entity), any());
    verify(mockRequestInterceptor, never()).logSuccess(any());

  }

  @Test
  void shouldLogErrorWhenNoRecoveryAndNoBackoff() {
    final Object entity = new Object();

    simulateDynamoDbFailure();

    Assertions.assertThrows(RuntimeException.class, () -> testWriterWithoutBackoffAndRecoverer.batchDelete(entity));

    verifyNoInteractions(mockBackoffExecutor, mockErrorRecoverer);
    verify(mockRequestInterceptor, times(1)).logError(eq(entity), any());

  }

  private void simulateDynamoDbFailure() {
    doThrow(RuntimeException.class).when(dynamoDBMapper).batchDelete(any(Object.class));
  }

  private void simulateBackoffFailure() throws ExecutionException, InterruptedException {
    final ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    doThrow(RuntimeException.class).when(mockBackoffExecutor).execute(runnableCaptor.capture());
  }

  private void simulateRecoveryFailure() {
    doThrow(RuntimeException.class).when(mockErrorRecoverer).recover(any());
  }

  private void captureRunnableForRetry() throws ExecutionException, InterruptedException {
    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    doNothing().when(mockBackoffExecutor).execute(runnableCaptor.capture());
  }

}
