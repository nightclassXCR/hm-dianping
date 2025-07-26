package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;


import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_CODE_TTL;
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

        stringredisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL);

        // 因为发送验证码需要阿里云短信服务，这里先返回成功
        log.info("发送验证码成功，验证码为：{}", code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session){
        if (loginForm.getPhone() == null || isPhoneInvalid(loginForm.getPhone())){
            return Result.fail("手机号格式错误");
        }


        Object cacheCode = session.getAttribute("code");
        String code = loginForm.getCode();
        if(!cacheCode.toString().equals(code)){
            return Result.fail("验证码错误");
        }
        if(session.getAttribute("code")== null){
            return Result.fail("验证码已过期");
        }


        // 查询用户 SELECT * FROM user WHERE phone = ?
        User user = query().eq("phone", loginForm.getPhone()).one();

        if (user == null){
            user = createUserWithPhone(loginForm.getPhone());
        }

        UserDTO userDTO = new UserDTO();
        userDTO.setIcon(user.getIcon());
        userDTO.setNickName(user.getNickName());
        userDTO.setId(user.getId());

        session.setAttribute("user", userDTO);
        return Result.ok();
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
