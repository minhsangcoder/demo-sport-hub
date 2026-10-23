package com.sporthub.booking.service;

import static com.sporthub.booking.service.DiscoveryService.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** One group per facility; courts within that group retain their original order and transaction. */
@Component
public class DiscoveryExecutor implements AutoCloseable {
  private final ThreadPoolExecutor pool;

  public DiscoveryExecutor(@Value("${booking.discovery.parallelism:2}") int parallelism) {
    if (parallelism < 1 || parallelism > 4)
      throw new IllegalArgumentException("Discovery parallelism must be 1..4");
    var sequence = new AtomicInteger();
    pool =
        parallelism == 1
            ? null
            : new ThreadPoolExecutor(
                parallelism,
                parallelism,
                0,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(200),
                task -> {
                  var thread = new Thread(task, "sporthub-discovery-" + sequence.incrementAndGet());
                  thread.setDaemon(true);
                  return thread;
                },
                (task, executor) -> {
                  if (executor.isShutdown())
                    throw new RejectedExecutionException("Discovery is stopping");
                  try {
                    executor.getQueue().put(task);
                    if (executor.isShutdown() && executor.remove(task))
                      throw new RejectedExecutionException("Discovery is stopping");
                  } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new RejectedExecutionException(error);
                  }
                });
  }

  List<Result> execute(
      List<DiscoveryPlanner.Group> groups,
      Function<DiscoveryPlanner.Group, DiscoveryResultMapper.Outcome> work) {
    var results = new ArrayList<Result>();
    if (pool == null) {
      for (var group : groups) append(results, work.apply(group));
      return results;
    }
    var futures = new ArrayList<Future<DiscoveryResultMapper.Outcome>>();
    try {
      for (var group : groups) futures.add(pool.submit(() -> work.apply(group)));
      // Await in candidate order so a later failure never replaces the first failure.
      for (var future : futures) append(results, future.get());
      return results;
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Search data is temporarily unavailable; please retry",
          error);
    } catch (ExecutionException error) {
      if (error.getCause() instanceof RuntimeException cause) throw cause;
      if (error.getCause() instanceof Error cause) throw cause;
      throw new IllegalStateException(error.getCause());
    } catch (RejectedExecutionException error) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Search data is temporarily unavailable; please retry",
          error);
    } finally {
      for (var future : futures) if (!future.isDone()) future.cancel(true);
    }
  }

  private void append(List<Result> results, DiscoveryResultMapper.Outcome outcome) {
    if (outcome.error() != null) throw outcome.error();
    results.addAll(outcome.rows());
  }

  @Override
  @jakarta.annotation.PreDestroy
  public void close() {
    if (pool != null)
      for (var task : pool.shutdownNow()) if (task instanceof Future<?> future) future.cancel(true);
  }
}
