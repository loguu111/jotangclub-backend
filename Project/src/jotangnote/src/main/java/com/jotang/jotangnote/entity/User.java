package com.jotang.jotangnote.entity;

import java.time.LocalDateTime;

/**
 * 用户实体，对应 users 表。
 *
 * ⚠️ 注意：password 字段里存的是 BCrypt 哈希，不是明文密码。
 * 但即便这样，这个对象也【不要】直接返回给前端——AuthController 用了 Map
 * 只返回 id 和 username，就是为了一劳永逸地避免密码字段泄露。
 */
public class User
{
    private Long id;
    private String username;
    private String password;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
