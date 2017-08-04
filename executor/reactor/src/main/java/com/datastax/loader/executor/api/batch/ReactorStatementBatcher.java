/*
 * Copyright (C) 2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.loader.executor.api.batch;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.CodecRegistry;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.Statement;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** A subclass of {@link StatementBatcher} that adds reactive-style capabilities to it. */
public class ReactorStatementBatcher extends StatementBatcher {

  /**
   * Creates a new {@link ReactorStatementBatcher} that produces {@link
   * com.datastax.driver.core.BatchStatement.Type#UNLOGGED unlogged} batches, operates in {@link
   * BatchMode#PARTITION_KEY partition key} mode and uses the {@link
   * ProtocolVersion#NEWEST_SUPPORTED latest stable} protocol version and the default {@link
   * CodecRegistry#DEFAULT_INSTANCE CodecRegistry} instance.
   */
  public ReactorStatementBatcher() {}

  /**
   * Creates a new {@link ReactorStatementBatcher} that produces {@link
   * com.datastax.driver.core.BatchStatement.Type#UNLOGGED unlogged} batches, operates in {@link
   * BatchMode#PARTITION_KEY partition key} mode and uses the given {@link Cluster} as its source
   * for the {@link ProtocolVersion protocol version} and the {@link CodecRegistry} instance to use.
   *
   * @param cluster The {@link Cluster} to use; cannot be {@code null}.
   */
  public ReactorStatementBatcher(Cluster cluster) {
    super(cluster);
  }

  /**
   * Creates a new {@link ReactorStatementBatcher} that produces {@link
   * com.datastax.driver.core.BatchStatement.Type#UNLOGGED unlogged} batches, operates in the
   * specified {@link BatchMode batch mode} and uses the given {@link Cluster} as its source for the
   * {@link ProtocolVersion protocol version} and the {@link CodecRegistry} instance to use.
   *
   * @param cluster The {@link Cluster} to use; cannot be {@code null}.
   * @param batchMode The {@link BatchMode batch mode} to use; cannot be {@code null}.
   */
  public ReactorStatementBatcher(Cluster cluster, BatchMode batchMode) {
    super(cluster, batchMode);
  }

  /**
   * Creates a new {@link ReactorStatementBatcher} that produces batches of the given {@link
   * com.datastax.driver.core.BatchStatement.Type batch type}, operates in the specified {@link
   * BatchMode batch mode} and uses the given {@link Cluster} as its source for the {@link
   * ProtocolVersion protocol version} and the {@link CodecRegistry} instance to use.
   *
   * @param cluster The {@link Cluster} to use; cannot be {@code null}.
   * @param batchMode The {@link BatchMode batch mode} to use; cannot be {@code null}.
   * @param batchType The {@link BatchStatement.Type batch type} to use; cannot be {@code null}.
   */
  public ReactorStatementBatcher(
      Cluster cluster, BatchMode batchMode, BatchStatement.Type batchType) {
    super(cluster, batchMode, batchType);
  }

  /**
   * Batches together the given statements into groups of statements having the same grouping key.
   *
   * <p>The grouping key to use is determined by the {@link BatchMode batch mode} in use by this
   * statement batcher.
   *
   * <p>When {@link BatchMode#PARTITION_KEY PARTITION_KEY} is used, the grouping key is the
   * statement's {@link Statement#getRoutingKey(ProtocolVersion, CodecRegistry) routing key} or
   * {@link Statement#getRoutingToken() routing token}, whichever is available.
   *
   * <p>When {@link BatchMode#REPLICA_SET REPLICA_SET} is used, the grouping key is the replica set
   * owning the statement's {@link Statement#getRoutingKey(ProtocolVersion, CodecRegistry) routing
   * key} or {@link Statement#getRoutingToken() routing token}, whichever is available.
   *
   * @param statements the statements to batch together.
   * @return A publisher of batched statements.
   */
  public Flux<Statement> batchByGroupingKey(Publisher<? extends Statement> statements) {
    return Flux.from(statements).groupBy(this::groupingKey).flatMap(this::batchAll);
  }

  /**
   * Batches together all the given statements into one single {@link BatchStatement}. The returned
   * {@link Publisher} is a {@link Mono} and hence guaranteed to only emit one single item.
   *
   * <p>Note that when given one single statement, this method will not create a batch statement
   * containing that single statement; instead, it will return that same statement.
   *
   * <p>Use this method with caution; if the given statements do not share the same {@link
   * Statement#getRoutingKey(ProtocolVersion, CodecRegistry) routing key}, the resulting batch could
   * lead to write throughput degradation.
   *
   * @param statements the statements to batch together.
   * @return A {@link Mono} of one single {@link BatchStatement} containing all the given statements
   *     batched together, or a {@link Mono} of the original statement, if only one was provided.
   */
  public Mono<Statement> batchAll(Publisher<? extends Statement> statements) {
    return Flux.from(statements)
        .reduce(new BatchStatement(batchType), BatchStatement::add)
        // Don't wrap single statements in batch.
        .map(batch -> batch.size() == 1 ? batch.getStatements().iterator().next() : batch);
  }
}
