package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;
    // 在redis中查询所有商铺类型
    // 1.从redis中查询商铺类型
    // 2.判断redis中是否存在
    // 3.存在直接返回
    // 4.不存在，查询数据库
    // 5.不存在返回错误
    // 6.存在写入Redis
    // 7.返回
    @Override
    public Result queryTypeList() {
        // 1.从 Redis 中查询商铺缓存
        String shopTypeJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);

        // 2.判断 Redis 中是否存在数据
        if (StrUtil.isNotBlank(shopTypeJson)) {
            // 2.1.存在，则返回
            List<ShopType> shopTypes = JSONUtil.toList(shopTypeJson, ShopType.class);
            return Result.ok(shopTypes);
        }
        // 2.2.Redis 中不存在，则从数据库中查询
        List<ShopType> shopTypes = query().orderByAsc("sort").list();

        // 3.判断数据库中是否存在
        if (shopTypes == null) {
            // 3.1.数据库中也不存在，则返回 false
            return Result.fail("分类不存在！");
        }
        // 3.3.2.1.数据库中存在，则将查询到的信息存入 Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(shopTypes));
        // 3.3.2.2.返回
        return Result.ok(shopTypes);

    }
}
