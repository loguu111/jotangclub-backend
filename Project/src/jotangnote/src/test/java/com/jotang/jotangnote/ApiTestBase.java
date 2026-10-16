package com.jotang.jotangnote;

import com.jotang.jotangnote.entity.User;
import com.jotang.jotangnote.mapper.NoteMapper;
import com.jotang.jotangnote.mapper.UserMapper;
import com.jotang.jotangnote.service.NoteCacheService;
import com.jotang.jotangnote.service.TokenService;
import com.jotang.jotangnote.service.UserService;
import com.jotang.jotangnote.entity.Note;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 接口测试的公共基类：负责"造用户 / 拿 token / 轮询等待 / 清理数据"。
 *
 * ⚠️ 为什么这些测试【不再用 @Transactional】？
 *   P3-01 之后写操作变成异步了：接口把消息投递到 RabbitMQ，
 *   后台消费者在【另一个线程】里写数据库。如果测试还挂着 @Transactional：
 *     ① 测试事务里的数据对消费者线程不可见（它查不到那条笔记）；
 *     ② 外键约束直接报错（消费者要插入的 user_id 还没提交）。
 *   所以改成"真实提交 + 手动清理"。
 *
 * 清理三类东西：Redis 里的 token、笔记缓存，数据库里的笔记和用户
 * （顺序不能反：notes.user_id 有外键指向 users）。
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class ApiTestBase
{
    @Autowired protected MockMvc mockMvc;
    @Autowired protected UserService userService;
    @Autowired protected UserMapper userMapper;
    @Autowired protected TokenService tokenService;
    @Autowired protected NoteMapper noteMapper;
    @Autowired protected NoteCacheService noteCache;
    @Autowired protected StringRedisTemplate redisTemplate;
    @Autowired protected JdbcTemplate jdbcTemplate;

    protected final List<Long> userIdsToClean = new ArrayList<>();
    protected final List<Long> noteIdsToClean = new ArrayList<>();
    protected final List<String> tokensToClean = new ArrayList<>();

    /** 当前用例的登录用户 */
    protected Long userId;
    protected String token;

    @BeforeEach
    void setUpCurrentUser()
    {
        userId = registerUser(newUsername()).getId();
        token = tokenService.createToken(userId);
        tokensToClean.add(token);
    }

    @AfterEach
    void cleanUpAll()
    {
        tokensToClean.forEach(tokenService::removeToken);
        tokensToClean.clear();

        noteIdsToClean.forEach(noteCache::evict);
        noteIdsToClean.clear();

        // 先删笔记再删用户（外键顺序）
        userIdsToClean.forEach(id -> jdbcTemplate.update("DELETE FROM notes WHERE user_id = ?", id));
        userIdsToClean.forEach(id -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", id));
        userIdsToClean.clear();
    }

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

    // ---------- 造数据 ----------

    /** 从登录响应里抠出 token */
    protected String tokenOf(String json)
    {
        Matcher matcher = TOKEN_PATTERN.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    protected String newUsername()
    {
        return "t" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** 注册一个用户，并登记到待清理列表 */
    protected User registerUser(String username)
    {
        User user = userService.register(username, "123456");
        userIdsToClean.add(user.getId());
        return user;
    }

    /** 注册并直接发一个 token（省掉走 HTTP 登录） */
    protected String registerAndLogin(String username)
    {
        String newToken = tokenService.createToken(registerUser(username).getId());
        tokensToClean.add(newToken);
        return newToken;
    }

    /** 用 Mapper 直接插一条笔记（当前用户的），并登记清理 */
    protected Note insertNote(String title, String content)
    {
        Note note = new Note();
        note.setTitle(title);
        note.setContent(content);
        note.setUserId(userId);
        noteMapper.insert(note);
        trackNote(note.getId());
        return note;
    }

    /** 登记一个笔记 id：结束后删缓存 */
    protected void trackNote(Long noteId)
    {
        noteIdsToClean.add(noteId);
    }

    protected String bearer()
    {
        return "Bearer " + token;
    }

    protected String bearer(String someToken)
    {
        return "Bearer " + someToken;
    }

    // ---------- 等待异步 ----------

    /**
     * 轮询等待条件成立。
     * 写操作现在是异步的，测试不能"发完请求就断言"，必须等后台消费者处理完。
     */
    protected void awaitTrue(String what, Supplier<Boolean> condition)
    {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline)
        {
            if (Boolean.TRUE.equals(condition.get()))
            {
                return;
            }
            try
            {
                Thread.sleep(100);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                break;
            }
        }
        fail("等待超时：" + what);
    }

    /** 查当前用户标题为 title 的笔记数量（用来确认消费者是否已经写库） */
    protected int countNotesByTitle(String title)
    {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notes WHERE user_id = ? AND title = ?",
                Integer.class, userId, title);
        return n == null ? 0 : n;
    }

    /** 查当前用户标题为 title 的笔记 id（没有就返回 null） */
    protected Long findNoteIdByTitle(String title)
    {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM notes WHERE user_id = ? AND title = ?",
                Long.class, userId, title);
        return ids.isEmpty() ? null : ids.get(0);
    }
}
