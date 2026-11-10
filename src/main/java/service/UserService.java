package service;

import pojo.User;

public interface UserService {

    /**
     * 注册用户
     * @param user
     * @return
     */
    boolean registerUser(User user);

    /**
     * 登录
     * @param email
     * @param password
     * @return
     */
    User login(String email,String password);

    /**
     * 重置密码
     * @param email
     * @param password
     * @return
     */
    boolean resetPassword(String email,String password);


    /**
     * 通过邮箱判断用户是否存在
     * @param email
     * @return
     */
    boolean existsByEmail(String email);


    /**
     * 更新用户信息
     *
     * @param user
     * @return
     */
    Object updateUserProfile(User user);
}


