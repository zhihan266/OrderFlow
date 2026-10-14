package com.orderflow.orderflowbackend.product.service;


import com.orderflow.orderflowbackend.exception.BusinessException;
import com.orderflow.orderflowbackend.product.cache.ProductDetailCacheData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ProductCacheService {

    private static final Logger log =
            LoggerFactory.getLogger(ProductCacheService.class);
    private static final String PREFIX = "product:detail:";
    private static final String NULL_VALUE = "__NULL__";
    private static final String LOCK_PREFIX = "product:detail:lock:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    //使用Lur原子释放锁
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();

        UNLOCK_SCRIPT.setScriptText("""
                if redis.call('get',KEYS[1]) == ARGV[1] then
                    return redis.call('del',KEYS[1])
                else
                    return 0
                end        
                """);
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public ProductCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ){

        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public ProductDetailCacheData get(Long productId){

        String value = redisTemplate.opsForValue().get(PREFIX +productId);

        if(value == null){
            return null;
        }

        if(NULL_VALUE.equals(value)){
            throw new BusinessException(
                    40401,
                    "商品不存在"
            );
        }

        try{
            return objectMapper.readValue(
                    value,
                    ProductDetailCacheData.class
            );
        }catch (Exception e){

            throw new RuntimeException(
                    "商品缓存解析失败",
                    e
            );
        }
    }

    public void set (
            Long productId,
            ProductDetailCacheData data
    ){
        try {

            String json = objectMapper.writeValueAsString(data);

            long ttlMinutes = ThreadLocalRandom.current().nextLong(25,36);
            redisTemplate.opsForValue().set(
                    PREFIX  + productId,
                    json,
                    Duration.ofMinutes(ttlMinutes)
            );

        }catch (Exception e){
            throw new RuntimeException(
                    "商品缓存写入失败",
                    e
            );
        }

    }

    public void setNull(Long productId){

        redisTemplate.opsForValue().set(
                PREFIX +productId,
                NULL_VALUE,
                Duration.ofMinutes(2)
        );
    }

    public void delete(Long productId){

        redisTemplate.delete(
                PREFIX + productId
        );
    }

    //抢redis锁
    public boolean tryLock(
            Long productId,
            String lockValue
    ){
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(
                        LOCK_PREFIX + productId,
                        lockValue,
                        Duration.ofSeconds(10)
                );
        return Boolean.TRUE.equals(success);
    }

    //释放redis锁
    public void unlock(
            Long productId,
            String lockValue
    ){

        redisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(
                        LOCK_PREFIX + productId
                ),
                lockValue
        );
    }

    public void deleteAfterCommit(Long productId) {

        /*
         * 当前存在真实数据库事务，
         * 并且 Spring 已开启事务同步机制。
         */
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {

            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {

                        @Override
                        public void afterCommit() {

                            try {

                                delete(productId);

                                log.info(
                                        "商品缓存提交后删除成功, productId={}",
                                        productId
                                );

                            } catch (Exception e) {

                                /*
                                 * 注意：
                                 *
                                 * 这里数据库已经 COMMIT。
                                 *
                                 * Redis 删除失败不能再告诉客户端
                                 * “数据库业务失败”，否则会出现：
                                 *
                                 * DB 已成功
                                 * 但接口返回失败
                                 *
                                 * 所以这里记录日志，
                                 * 以后生产环境可以增加重试/补偿。
                                 */
                                log.error(
                                        "商品缓存提交后删除失败, productId={}",
                                        productId,
                                        e
                                );
                            }
                        }
                    }
            );

            return;
        }


        /*
         * 如果当前根本不在事务中，
         * 就直接删除。
         *
         * 这样这个方法在普通场景下也可以复用。
         */
        delete(productId);
    }
}
