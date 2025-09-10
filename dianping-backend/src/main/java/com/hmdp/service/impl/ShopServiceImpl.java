package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        //缓存穿透
//        Shop shop = queryWithPassThrough(id);
          // 使用泛型，封装redis  this::getById
//        Shop shop  = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,(ID) -> getById(ID),CACHE_SHOP_TTL,TimeUnit.MINUTES);
        //   缓存击穿 互斥锁方案
//        Shop shop = queryWithPassMutex(id);
        // 缓存击穿  缓存逻辑过期方案
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
//        Shop shop = queryWithLogicalExpire(id);
        if(shop==null){
            return Result.fail("店铺不存在!");
        }
        return Result.ok(shop);
    }

    public Shop queryWithPassMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isNotBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson,Shop.class);
            return shop;
        }
        //判断是否为空值
        if (shopJson != null){
            return null;
        }
        // 查询数据库，并建立缓存
        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            if(!isLock){
                Thread.sleep(50);
                return queryWithPassMutex(id);
            }

            shop = getById(id);
            // 模拟重建延时
//            Thread.sleep(200);
            if(shop==null){
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);

        }catch (InterruptedException e){
            throw new RuntimeException(e);
        }
        finally {
            unLock(lockKey);
        }
        return shop;
    }


    public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isNotBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson,Shop.class);
            return shop;
        }
        //判断是否为空值
        if (shopJson != null){
            return null;
        }
        // 查询数据库，并建立缓存
        Shop shop = getById(id);
        if(shop==null){
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public Shop queryWithLogicalExpire(Long id)  {
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isBlank(shopJson)){
//            Shop shop = JSONUtil.toBean(shopJson,Shop.class);
            return null;
        }
        //判断是否为空值
        // 查询数据库，并建立缓存
        RedisData redisData = JSONUtil.toBean(shopJson,RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject)redisData.getData(),Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())){
            return shop;
        }

        // 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if(isLock){

                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    try {
                        this.saveShop2Redis(id,CACHE_SHOP_TTL);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    finally {
                        unLock(lockKey);
                    }
                });
            }

//            unLock(lockKey);

        return shop;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    public void saveShop2Redis(Long id,Long expireSconds) throws InterruptedException {
        Shop shop = getById(id);
        Thread.sleep(200);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSconds));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空!");
        }
        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

}
