package com.travelplatform.inventory.infrastructure;

import com.travelplatform.inventory.domain.InventoryUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Atomic inventory lock/release backed by Redis Lua scripts. The hot counters
 * live in a Redis hash per inventory unit; the database remains the source of
 * truth for the initial total and the audit trail.
 */
@Component
public class InventoryLockRedis {

    private static final DefaultRedisScript<Long> LOCK_SCRIPT = new DefaultRedisScript<>(
            """
            local avail = tonumber(redis.call('HGET', KEYS[1], 'available') or '-1')
            if avail < 0 then return -1 end
            if avail >= tonumber(ARGV[1]) then
              redis.call('HINCRBY', KEYS[1], 'available', -tonumber(ARGV[1]))
              redis.call('HINCRBY', KEYS[1], 'locked', tonumber(ARGV[1]))
              return 1
            end
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            """
            local flag = redis.call('SETNX', KEYS[2], '1')
            if flag == 1 then
              redis.call('EXPIRE', KEYS[2], 604800)
              local locked = tonumber(redis.call('HGET', KEYS[1], 'locked') or '0')
              if locked >= tonumber(ARGV[1]) then
                redis.call('HINCRBY', KEYS[1], 'locked', -tonumber(ARGV[1]))
                redis.call('HINCRBY', KEYS[1], 'available', tonumber(ARGV[1]))
                return 1
              else
                redis.call('DEL', KEYS[2])
                return 0
              end
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public InventoryLockRedis(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void initialize(InventoryUnit unit) {
        String key = unitKey(unit.getId());
        redisTemplate.opsForHash().putIfAbsent(key, "total", String.valueOf(unit.getTotal()));
        redisTemplate.opsForHash().putIfAbsent(key, "available", String.valueOf(unit.getAvailable()));
        redisTemplate.opsForHash().putIfAbsent(key, "locked", String.valueOf(unit.getLocked()));
    }

    /**
     * @return 1 locked, 0 insufficient stock, -1 not initialized.
     */
    public long tryLock(String unitId, int quantity) {
        return redisTemplate.execute(LOCK_SCRIPT, List.of(unitKey(unitId)), String.valueOf(quantity));
    }

    /**
     * @return 1 released, 0 already released (idempotent no-op).
     */
    public long release(String lockId, String unitId, int quantity) {
        return redisTemplate.execute(
                RELEASE_SCRIPT,
                List.of(unitKey(unitId), releaseFlagKey(lockId)),
                String.valueOf(quantity));
    }

    public Integer available(String unitId) {
        String value = (String) redisTemplate.opsForHash().get(unitKey(unitId), "available");
        return value == null ? null : Integer.valueOf(value);
    }

    private String unitKey(String unitId) {
        return "inventory:unit:" + unitId;
    }

    private String releaseFlagKey(String lockId) {
        return "inventory:released:" + lockId;
    }
}
