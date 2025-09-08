package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

//import javax.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
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
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //获得用户手机号
        //1.校验手机号正确性
        if (RegexUtils.isPhoneInvalid(phone))
            return Result.fail("手机号格式不正确");
//        session.setAttribute("phone", phone);
        //2.生成验证码
        String code = RandomUtil.randomNumbers(6);
        //3.保存验证码 redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES); //5 minutes,这是通用的
//        session.setAttribute("code", code);
        //4.发送验证码    模拟发送验证码
        log.debug("发送短信验证码成功, 验证码:{}",code);
        //5.返回ok
        return Result.ok();
//        return null;
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号格式是否正确
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)) return Result.fail("手机号格式不正确!");//这里不知道为啥,不用return true代表格式不正确
        //2.校验手机号是否和session里面的一致
//        String cachePhone = (String) session.getAttribute("phone");
//        if (!cachePhone.equals(phone)) return Result.fail("前后手机号不匹配!");
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if(cacheCode == null || !cacheCode.equals(code)){
            return Result.fail("验证码错误!");
        }
        //3.校验验证码
//        Object cacheCode = session.getAttribute("code");
//        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) return Result.fail("验证码错误!");
        //4.判断用户是否存在
        User user = query().eq("phone", phone).one();
        //5.不存在的话直接创建一个用户
        if (user == null) user = createUserWithPhone(phone);
        //6.保存用户信息到session中 before
        //6.保存用户信息到redis中
        String token = UUID.randomUUID().toString(true);
        String tokenKey = LOGIN_USER_KEY + token;

        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

//        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.SECONDS);
        return Result.ok(token);
    }
    //注册
    private User createUserWithPhone(String phone) {
        //1.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        //2.保存用户到数据库中
        save(user);
        return user;
    }

}
