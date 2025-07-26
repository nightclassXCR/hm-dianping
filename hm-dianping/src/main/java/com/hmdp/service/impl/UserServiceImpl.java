package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;


import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import javax.swing.plaf.BorderUIResource;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.RegexUtils.isPhoneInvalid;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {


    @Resource
    private StringRedisTemplate stringredisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {
        if(phone == null || isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        String code = RandomUtil.randomNumbers(6);
        log.debug("发送验证码：{}", code);

        stringredisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code.trim(), LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 因为发送验证码需要阿里云短信服务，这里先返回成功
        log.info("发送验证码成功，验证码为：{}", code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session){
        String phone = loginForm.getPhone();

        if (phone == null || isPhoneInvalid(loginForm.getPhone())){
            return Result.fail("手机号格式错误");
        }

        String cacheCode = stringredisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        log.info("cacheCode: " + cacheCode);
        cacheCode = cacheCode.trim();
        String code = loginForm.getCode();
        log.info("code: " + code);
        if (!cacheCode.equals(code)){
            return Result.fail("验证码错误");
        }


        // 查询用户 SELECT * FROM user WHERE phone = ?
        User user = query().eq("phone", loginForm.getPhone()).one();

        if (user == null){
            user = createUserWithPhone(loginForm.getPhone());
        }

        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        stringredisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        stringredisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }


    @Override
    public Result logout() {
        UserHolder.removeUser();
        return Result.ok();
    }


}
