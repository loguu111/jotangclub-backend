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