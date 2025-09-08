package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;

public class LoginInterceptor implements HandlerInterceptor {

//    private StringRedisTemplate stringRedisTemplate;
//
//    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
//        this.stringRedisTemplate = stringRedisTemplate;
//    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        //1.获取session中的用户  获取token
//        //HttpSession session = request.getSession();
//        String token = request.getHeader("authorization");
//        if(StrUtil.isBlank(token)){
//            response.setStatus(401);
//            return false;
//        }
////        Object user = session.getAttribute("user");
//         Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
//        //2.用户是否存在,不存在返回false,并设置状态码
//         if (userMap.isEmpty()){
//            response.setStatus(401);
//            return false;
//        }
//
//         UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(),false);
//        //3.存在就将当前用户存储在ThreadLocal中
//        UserHolder.saveUser(userDTO);
//        stringRedisTemplate.expire(LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS); //30minutes
        UserDTO userDto = UserHolder.getUser();
        //2.判断是否有user用户
        if (userDto == null) {
            response.setStatus(401);
            return false;
        }
        return true;
//        return HandlerInterceptor.super.preHandle(request, response, handler);

    }
//    @Override
//    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
//            UserHolder.removeUser();
//    }
}
