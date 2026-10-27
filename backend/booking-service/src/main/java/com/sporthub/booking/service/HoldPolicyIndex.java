package com.sporthub.booking.service;

import java.util.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Rebuildable 48-hour event window and source dedup; a DB revision fences stale deliveries. */
@Component
public class HoldPolicyIndex {
  private static final DefaultRedisScript<Long> REBUILD =
      new DefaultRedisScript<>(
          """
redis.call('DEL',KEYS[1],KEYS[2],KEYS[3])
for i=2,#ARGV,3 do
 redis.call('HSET',KEYS[3],ARGV[i],ARGV[i+1]..':'..ARGV[i+2])
 redis.call('ZADD',KEYS[2],ARGV[i+2],ARGV[i]..':'..ARGV[i+1])
end
redis.call('SET',KEYS[1],ARGV[1],'EX',259200)
redis.call('EXPIRE',KEYS[2],259200)
redis.call('EXPIRE',KEYS[3],259200)
return 1
""",
          Long.class);
  private final StringRedisTemplate redis;

  public HoldPolicyIndex(StringRedisTemplate redis) {
    this.redis = redis;
  }

  static String prefix(UUID user) {
    return "booking:hold-policy:{" + user + "}:";
  }

  void publish(UUID user, HoldPolicyLedger.Snapshot snapshot) {
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Only committed admission facts may reach Redis");
    var args = new ArrayList<String>();
    args.add(snapshot.revision().toString());
    for (var fact : snapshot.facts()) {
      args.add(fact.sourceId().toString());
      args.add(fact.outcome().name());
      args.add(Long.toString(fact.at().toEpochMilli()));
    }
    String key = prefix(user);
    if (!Long.valueOf(1)
        .equals(
            redis.execute(
                REBUILD,
                List.of(key + "revision", key + "window", key + "sources"),
                args.toArray())))
      throw new IllegalStateException("Admission index rebuild did not complete");
  }

  boolean current(UUID user, UUID revision) {
    return revision.toString().equals(redis.opsForValue().get(prefix(user) + "revision"));
  }
}
