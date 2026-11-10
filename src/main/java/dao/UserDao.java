package dao;

import pojo.User;

public interface UserDao {

    /**
     * 根据用户名查询用户ID
     * @param username
     * @return
     */
    Long queryIdByUsername(String username);

    /**
     *
     * @param id
     * @return
     */
    User findById(Long id);
    /**
     * 根据用户ID查询用户头像地址
     * @param id
     * @return
     */
    String queryAvatarByUserId(Long id);

    /**
     * 通过用户名查询用户信息
     * @param Username
     * @return
     */
    User queryUserByUsername(String Username);


    /**
     * 通过邮箱判断用户是否存在
     * @param email
     * @return
     */
    boolean existsByEmail(String email);

    /**
     * 添加用户
     * @param user
     * @return
     */
    boolean addUser(User user);

    /**
     * 通过邮箱查询用户（包含密码哈希，用于登录验证）
     * @param email
     * @return 用户对象（含password字段），不存在时返回null
     */
    User findByEmail(String email);

    /**
     * 更新用户信息
     * @param user
     * @return
     */
    boolean updateUser(User user);

    boolean updatePasswordByEmail(String email,String password);

}
