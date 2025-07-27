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
import org.apache.ibatis.jdbc.Null;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

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
//       // 缓存穿透
//        Shop shop = cacheClient
//                .queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 互斥锁解决缓存击穿
        Shop shop = cacheClient
                .queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

////         逻辑过期解决缓存击穿
//        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }

        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {

        Long id = shop.getId();
        if(id == null){
            return Result.fail("商户ID不能为空");
        }
        // 1更新数据库
        updateById(shop);
        // 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);

        return Result.ok();
    }


    // 获取锁
    private boolean tryLock(String key){
       Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1",10, TimeUnit.SECONDS);
       return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key){
       stringRedisTemplate.delete(key);
    }

//    // 缓存穿透
//    public Shop queryWithPassThrough(Long id){
//        // 1.从商户查
//        String shopjson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//        // 2.判断商户是否存在
//        if(StrUtil.isNotBlank(shopjson)){
//            // 3存在直接返回
//            Shop shop = JSONUtil.toBean(shopjson, Shop.class);
//            return shop;
//        }
//        // 不存在，查询数据库
//        Shop shop = getById(id);
//        if(shop == null){
//            // 不存在返回错误
//            // 防止缓存穿透，将空值缓存，设置过期时间
//            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "",CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
//        // 存在写入Redis
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        // 返回
//        return shop;
//    }

//    public Shop queryWithMutex(Long id){
//        // 1.从商户查
//        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//        // 2.判断商户是否存在
//        if(StrUtil.isNotBlank(shopJson)){
//            // 3存在直接返回
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        Shop shop = null;
//        // 实现缓存重建
//        // 获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        try {
//            boolean isLock = tryLock(lockKey);
//            // 判断是否获取锁成功
//            if (!isLock) {
//                // 获取锁失败，休眠并重新获取锁
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//            // 再次检查Redis中是否存在数据
//            String DoubleShopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//
//            if (StrUtil.isNotBlank(DoubleShopJson)) {
//                return JSONUtil.toBean(DoubleShopJson, Shop.class);
//            }
//            // 不存在，查询数据库
//            shop = getById(id);
//
//            if (shop == null) {
//                // 不存在返回错误
//                // 防止缓存穿透，将空值缓存，设置过期时间
//                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            // 存在写入Redis
//            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        }catch (Exception e){
//            throw new RuntimeException(e);
//        }finally {
//            unLock(lockKey);
//        }
//        // 返回
//        return shop;
//    }
//
//
//    // 缓存重建,逻辑过期
//    public void saveShop2Redis(Long id, Long expireSeconds) {
//        // 查询
//        Shop shop = getById(id);
//        // 封装逻辑过期时间
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(CACHE_SHOP_TTL));
//        // 写入Redis
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
//
//    }
//
//    // 缓存重建线程池
//
//
//    public Shop queryWithLogicalExpire(Long id){
//        // 1.从商户查
//        String shopjson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//        // 2.判断商户是否存在
//        if(StrUtil.isBlank(shopjson)){
//            return  null;
//        }
//        // 3.存在，则返回,判断是否过期把Json反序列化
//        RedisData redisData = JSONUtil.toBean(shopjson, RedisData.class);
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        if(expireTime.isAfter(LocalDateTime.now())){
//            // 未过期，返回店铺信息
//            return shop;
//        }
//        String lockKey = LOCK_SHOP_KEY + id;
//        boolean isLock = tryLock(lockKey);
//            // 判断是否获取锁成功
//        if (isLock) {
//            // 再次检查Redis中是否存在数据
//            String DoubleShopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//
//            if (StrUtil.isNotBlank(DoubleShopJson)) {
//                return JSONUtil.toBean(DoubleShopJson, Shop.class);
//            }
//            CACHE_REBUILD_EXECUTOR.submit(() -> {
//                try {
//                    // 缓存重建
//                    this.saveShop2Redis(id, 20L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                }finally {
//                    unLock(lockKey);
//                }
//            });
//        }
//        return shop;
//    }
}
