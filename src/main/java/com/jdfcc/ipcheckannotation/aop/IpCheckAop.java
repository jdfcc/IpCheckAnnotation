package com.jdfcc.ipcheckannotation.aop;

import com.jdfcc.ipcheckannotation.annotation.IpCheckAnnotation;
import com.jdfcc.ipcheckannotation.util.HttpUtil;
import com.jdfcc.ipcheckannotation.util.MyLock;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import static com.jdfcc.ipcheckannotation.common.constant.IP_CACHE_KEY;

/**
 * @author Jdfcc
 * @Description IpCheckAop
 * @DateTime 2023/7/14 15:48
 */

@Aspect
@Component
@Order(0)
@Slf4j
public class IpCheckAop {

    @Resource
    private StringRedisTemplate redisTemplate;


    @Around("@annotation(com.jdfcc.ipcheckannotation.annotation.IpCheckAnnotation) || @within(com.jdfcc.ipcheckannotation.annotation.IpCheckAnnotation)")
    public Object checkIpCut(ProceedingJoinPoint pjp) throws Throwable {
        ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = servletRequestAttributes.getRequest();
        String ipAddress = HttpUtil.getIpAddress(request);
        Class<?> clazz = pjp.getTarget().getClass();
        IpCheckAnnotation annotation = clazz.getAnnotation(IpCheckAnnotation.class);
        MyLock myLock = new MyLock(redisTemplate);
        //        判断类上是否有此注释，如果有这个注解，则不再判断方法里是否有此注解，因为作用粒度覆盖了整个类
        if (annotation != null) {

            myLock.lock(ipAddress);
            Boolean isValid = this.validAndHandleIp(ipAddress, annotation);
            myLock.unlock(ipAddress);
            if (!isValid) {
                return null;
            }
            return pjp.proceed();
        }
        MethodSignature methodSignature = (MethodSignature) pjp.getSignature();
        Method method = methodSignature.getMethod();
        if (method.isAnnotationPresent(IpCheckAnnotation.class)) {
            annotation = method.getAnnotation(IpCheckAnnotation.class);
        }
        if (annotation == null) {
            //        放行
            return pjp.proceed();
        }
        myLock.lock(ipAddress);
        Boolean isValid = this.validAndHandleIp(ipAddress, annotation);
        myLock.unlock(ipAddress);
        if (!isValid) {
            return null;
        }
        return pjp.proceed();
    }

    private Boolean validAndHandleIp(String ip, IpCheckAnnotation annotation) {
        String key = IP_CACHE_KEY + ip;

        Object count = redisTemplate.opsForHash().get(key, "count");
        int limitCount = annotation.count();
        int time = annotation.time();

        Object sec = redisTemplate.opsForHash().get(key, "time");
        LocalDateTime now = LocalDateTime.now();
        Long nowSec = now.toEpochSecond(ZoneOffset.UTC);

        if (count == null || sec == null) {
            redisTemplate.opsForHash().put(key, "count", String.valueOf(1));
            redisTemplate.opsForHash().put(key, "time", String.valueOf(nowSec));
            redisTemplate.expire(key, time, TimeUnit.SECONDS);
            return true;
        }


        int lastCount = Integer.parseInt((String) count);
        Integer lastSec = Integer.valueOf((String) sec);
        if (limitCount <= 0) {
            throw new IllegalArgumentException("Count can not be 0 and even smaller");
        }

        //        允许访问

        int step = time / limitCount;

        long durSec = nowSec - lastSec;

        //        按照步长减少访问次数
        int tem = Math.toIntExact((durSec / step));

        lastCount -= tem;

        if ((lastCount) >= limitCount && nowSec >= lastSec) {
//            规定时间内问次数达到上限，不能访问。过期时间为步长.
            return false;
        }

        lastCount = Math.max(lastCount, 0);
        redisTemplate.opsForHash().put(key, "count", String.valueOf(lastCount + 1));
        redisTemplate.opsForHash().put(key, "time", String.valueOf(nowSec));
        redisTemplate.expire(key, time, TimeUnit.SECONDS);
        return true;
    }


}

