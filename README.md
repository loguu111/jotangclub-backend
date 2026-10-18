本项目代码基本由 dsh 撰写，本人理解各代码部分

Notes			 学习笔记目录  

    MySQL		 MySQL相关笔记目录  

    Redis		 Redis相关笔记目录  

    Java         java相关笔记目录  

    RabbitMQ     RabbitMQ相关学习记录  

    AI Agent     AI Agent相关学习记录

Project 		 项目目录  

    src	    	 源码目录  

        jotangnote  项目主体  

            pom.xml 依赖与构建配置

            src     项目源码

                main主代码（Java 源码 + 配置文件 + SQL 映射 + 前端代码）

                test测试源码

    docs         项目说明文档目录  

        images   图片

如何运行

1. 配置环境变量

```bash
cd ~/project/jotangclub-backend && code .env
```

DB_PASSWORD=

在等号后面写上 MySQL root 密码

```bash
set -a && source .env && set +a
```

2. 建库建表

```bash
cd ~/project/jotangclub-backend && mysql -uroot -p < Notes/MySQL/schema.sql
```

3. 启动后端

```bash
终端 1
cd ~/project/jotangclub-backend/Project/src/jotangnote
mvn -o spring-boot:run

终端 2
redis-cli MONITOR

终端 3
mysql -uroot -p jotangnote
```

### 4. 打开前端

http://localhost:8080/index.html

### 5. 启用 AI 对话

在 `.env` 里填 `DEEPSEEK_API_KEY=sk-...`，重新 `source .env` 后重启。

## 接口清单

Base URL：`http://localhost:8080`
鉴权：需要登录的接口在请求头带 `Authorization: Bearer <token>`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/auth/register` | 注册（密码 BCrypt 加盐哈希后入库，不存明文） |
| POST | `/auth/login` | 登录（返回 token，存 Redis，有效期 7 天） |
| POST | `/auth/logout` | 退出登录（删掉 Redis 里的 token，立即失效） |
| GET | `/notes` | 笔记列表（最新在前；**公开可读**；不分页） |
| GET | `/notes/{id}` | 查一篇笔记（走 Redis 缓存，Cache-Aside） |
| POST | `/notes` | 新增笔记（需登录）→ 投 MQ，返回 **202** 已受理 |
| PUT | `/notes/{id}` | 修改笔记（需登录，只能改自己的）→ 投 MQ，返回 **202** |
| DELETE | `/notes/{id}` | 删除笔记（需登录，只能删自己的，**硬删**）→ 投 MQ，返回 **202** |
| POST | `/chat` | 与 AI 对话（带 `conversationId` 续同一会话；AI 可自行调用 `get_note_by_id` 工具） |
| GET | `/index.html` | 前端页面（登录、笔记列表、AI 对话；与后端同源） |

**状态码约定**

| 码 | 含义 |
| --- | --- |
| 200 | 查询成功 / 注册登录成功 |
| 202 | 写操作已受理，**后台异步处理中**（响应带 `messageId`） |
| 204 | 退出成功（无响应体） |
| 400 | 参数不合法（标题为空、密码太短等） |
| 401 | 未登录 / token 无效或已过期 |
| 403 | 笔记存在，但不是你的 |
| 404 | 笔记不存在 |
| 409 | 用户名已存在 |
| 502 | 调用大模型失败（没配 `DEEPSEEK_API_KEY` 等） |