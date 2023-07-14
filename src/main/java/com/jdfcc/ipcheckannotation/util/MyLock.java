package com.jdfcc.ipcheckannotation.util;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;


import java.util.ArrayList;
import java.util.List;

/**
 * @author Jdfcc
 * @Description lockUtil
 * @DateTime 2023/7/14 16:39
 */

public class MyLock {

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("lua/unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    StringRedisTemplate redisTemplate;

    public MyLock(StringRedisTemplate redisTemplate){
        this.redisTemplate = redisTemplate;
    }

    public boolean lock(String key) {
        long id = Thread.currentThread().getId();
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key,String.valueOf(id)));
    }

    public  void unlock(String key) {
        List<String> keys = new ArrayList<>();
        keys.add(key);
        String value = String.valueOf(Thread.currentThread().getId());
        redisTemplate.execute(UNLOCK_SCRIPT, keys, value);
    }
}
