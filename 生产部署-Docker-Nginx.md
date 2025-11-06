# 生产环境（Linux + Docker）部署：Nginx 配置与服务启动脚本

> 环境约定：Linux（使用 Docker），域名 `com.linaa.shiyan`，对外端口 `6639`。
> 目标：提供可直接复制使用的 Nginx 配置与 Docker Compose 脚本，快速上线前后端服务。

---

## 0. 前置准备与构建产物

- 前端：在构建机或仓库根目录执行（确保设置生产 API 基址）：
  - 创建 `frontend/.env.production` 内容：
    ```env
    VITE_API_BASE=http://com.linaa.shiyan:6639/api
    ```
  - 构建静态站点：
    ```bash
    cd frontend
    npm ci
    npm run build
    # 生成 dist/ 目录
    ```
- 后端：在仓库根目录执行打包（需 Java 17 与 Maven）：
  ```bash
  cd backend
  mvn -q -DskipTests clean package
  # 生成 target/backend-0.0.1-SNAPSHOT.jar
  ```
- 上传目录：在服务器准备持久化目录（Docker 容器挂载使用）：
  ```bash
  mkdir -p /opt/yunbq/uploads/avatars
  ```

> 说明：以下 Compose 脚本将挂载本地 `frontend/dist` 与 `backend/target/*.jar` 到容器中；请先构建完成再执行部署。

---

## 1. Nginx 配置（监听 6639，前端静态 + `/api` 反向代理）

- 在 Docker 容器中的 Nginx 配置文件 `default.conf` 建议内容如下（可直接复制）：

```nginx
# Nginx 站点配置：
# - 监听端口：6639（按需调整为 80/443，如需标准 HTTP/HTTPS）；
# - 域名：com.linaa.shiyan；
# - 前端静态：/usr/share/nginx/html（挂载 frontend/dist）；
# - 反向代理：/api/ -> backend:8080（Docker 内部服务名），保留 /api 前缀；
# - 上传映射：/uploads/ -> 容器内 /opt/yunbq/uploads（挂载宿主机 uploads）。

server {
    listen 6639;
    server_name com.linaa.shiyan;

    # 前端静态站点根目录（由 Docker 挂载 /usr/share/nginx/html）
    root /usr/share/nginx/html;
    index index.html;

    # 单页应用：不存在的路径回退到 index.html
    location / {
        try_files $uri /index.html;
    }

    # 反向代理到后端（保持 /api 前缀），后端 Spring Boot 默认 8080
    location /api/ {
        proxy_pass http://backend:8080/api/;
        # 透传常用头部，确保后端可感知真实来源与协议
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        # 长连接与超时按需调整
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_read_timeout 60s;
    }

    # 直接服务上传目录（可选）：提升头像等静态资源访问效率
    # 需在 Docker 中将宿主机 /opt/yunbq/uploads 挂载到 /opt/yunbq/uploads
    location /uploads/ {
        alias /opt/yunbq/uploads/;
        autoindex off;
    }
}
```

> 若需 HTTPS：建议另开 443 监听与证书配置；如必须用 `6639` 端口走 TLS，需自备证书并在该端口启用 `ssl`，配置略有差异，不在此示例内展开。

---

## 2. Docker Compose（可直接复制）

- 文件路径建议：`/opt/yunbq/docker-compose.yml`
- 内容如下（请先构建前后端产物，并把仓库内容放到 `/opt/yunbq` 或按路径调整挂载）：

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    container_name: yunbq-mysql
    environment:
      MYSQL_DATABASE: yunbq
      MYSQL_USER: yunbq
      MYSQL_PASSWORD: "<强密码>"
      MYSQL_ROOT_PASSWORD: "<更强的密码>"
    command: ["--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci"]
    volumes:
      - ./data/mysql:/var/lib/mysql
    networks:
      - yunbq-net
    ports:
      - "3306:3306"
    restart: always

  redis:
    image: redis:7
    container_name: yunbq-redis
    command: ["redis-server", "--appendonly", "yes"]
    volumes:
      - ./data/redis:/data
    networks:
      - yunbq-net
    ports:
      - "6379:6379"
    restart: always

  backend:
    image: eclipse-temurin:17-jre
    container_name: yunbq-backend
    working_dir: /app
    # 将构建好的 jar 与上传目录挂载到容器
    volumes:
      - ./backend/target/backend-0.0.1-SNAPSHOT.jar:/app/app.jar:ro
      - ./uploads:/opt/yunbq/uploads
    environment:
      SPRING_DATASOURCE_URL: "jdbc:mysql://mysql:3306/yunbq?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false"
      SPRING_DATASOURCE_USERNAME: "yunbq"
      SPRING_DATASOURCE_PASSWORD: "<强密码>"
      SPRING_DATA_REDIS_HOST: "redis"
      SPRING_DATA_REDIS_PORT: "6379"
      JWT_SECRET: "<长随机密钥>"
      JWT_ISSUER: "com.linaa.shiyan"
      JWT_EXPIRE_MINUTES: "1440"
      # 首次部署如需自动建表，可在环境变量中开启；后续请改为 never
      # SPRING_SQL_INIT_MODE: "always"
    command: ["java","-Xms256m","-Xmx512m","-jar","/app/app.jar","--spring.profiles.active=prod"]
    networks:
      - yunbq-net
    expose:
      - "8080"
    depends_on:
      - mysql
      - redis
    restart: always

  nginx:
    image: nginx:stable
    container_name: yunbq-nginx
    # 前端静态、Nginx 配置与上传目录挂载
    volumes:
      - ./frontend/dist:/usr/share/nginx/html:ro
      - ./nginx/default.conf:/etc/nginx/conf.d/default.conf:ro
      - ./uploads:/opt/yunbq/uploads:ro
    networks:
      - yunbq-net
    ports:
      - "6639:6639"
    depends_on:
      - backend
    restart: always

networks:
  yunbq-net:
    driver: bridge
```

> 说明：
> - 将仓库中的 `frontend/dist`、`backend/target/*.jar` 与 `uploads` 同级放置在 `/opt/yunbq/` 下；
> - 将本文提供的 Nginx `default.conf` 保存为 `/opt/yunbq/nginx/default.conf`；
> - 端口映射 `6639:6639`，外网访问为 `http://com.linaa.shiyan:6639/` 与 `http://com.linaa.shiyan:6639/api/...`；
> - 后端容器内部端口使用默认 `8080`，由 Nginx 通过服务名 `backend` 反代。

---

## 3. 一键服务管理（systemd 管理 Docker Compose）

- 在 Linux 上可用 `systemd` 管理 Compose，便于开机自启与统一日志。
- 创建 `/etc/systemd/system/yunbq-compose.service` 内容如下（按需调整路径）：

```ini
[Unit]
Description=YunBQ Docker Compose Stack
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
WorkingDirectory=/opt/yunbq
RemainAfterExit=true
ExecStart=/usr/bin/docker compose -f /opt/yunbq/docker-compose.yml up -d
ExecStop=/usr/bin/docker compose -f /opt/yunbq/docker-compose.yml down

[Install]
WantedBy=multi-user.target
```

- 启用与管理：
  ```bash
  sudo systemctl daemon-reload
  sudo systemctl enable yunbq-compose
  sudo systemctl start yunbq-compose
  # 查看状态与日志
  systemctl status yunbq-compose
  docker compose -f /opt/yunbq/docker-compose.yml ps
  ```

> 如不希望 `ExecStop` 在停止时销毁容器，可改为 `docker compose stop`，并在需要清理时手动执行 `down`。

---

## 4. 启动步骤（Checklist）

1) 将仓库内容上传至服务器 `/opt/yunbq`（或你选定的路径）。
2) 构建前端与后端产物（参考第 0 节），确保存在：
   - `/opt/yunbq/frontend/dist` 与 `/opt/yunbq/backend/target/backend-0.0.1-SNAPSHOT.jar`
3) 创建上传目录：`mkdir -p /opt/yunbq/uploads/avatars`
4) 写入 Nginx 配置文件：`/opt/yunbq/nginx/default.conf`（内容见第 1 节）。
5) 写入 Compose 文件：`/opt/yunbq/docker-compose.yml`（内容见第 2 节）。
6) 启动：
   - `docker compose -f /opt/yunbq/docker-compose.yml up -d`
   - 或启用 `systemd`：`sudo systemctl enable --now yunbq-compose`
7) 验证：
   - 浏览器访问 `http://com.linaa.shiyan:6639/` 能打开前端页面；
   - 访问 `http://com.linaa.shiyan:6639/api/account/me`（需登录态）返回用户信息；
   - 管理页可打开并拉取日志与用户列表；
   - 上传头像后，`/uploads/avatars/...` 可直接访问。

---

## 5. 注意事项与安全建议

- 强制替换默认凭据与密钥：
  - `SPRING_DATASOURCE_PASSWORD`、`MYSQL_ROOT_PASSWORD`、`JWT_SECRET` 等请使用你自己的安全值。
- CORS 与域名：
  - 前端 `VITE_API_BASE` 必须与 Nginx 对外地址一致（域名 + 端口 + `/api` 前缀）。
  - 后端 `application-prod.yml` 中的 `cors.allowed-origins` 建议配置为 `http://com.linaa.shiyan:6639`（或对应 HTTPS）。
- SQL 初始化：
  - 首次部署如需自动建表，可在 `backend` 容器通过 `SPRING_SQL_INIT_MODE=always` 打开；初始化完成后改回 `never`。
- 日志与资源占用：
  - 观察后端 `Xmx` 与容器资源使用，按需调整 JVM 内存与 Compose 资源限制；
  - 调整 `logdb.retention-*` 保留天数与 `request-sampling-percent` 控制日志数据量。
- HTTPS：
  - 生产强烈建议使用 HTTPS 与标准端口 `443`；如维持自定义端口 `6639`，请评估浏览器与中间设备的兼容性。

---

## 6. 故障排查

- 前端 404：检查 Nginx `root` 挂载路径是否正确，`try_files` 是否命中 `index.html`。
- API 504/502：检查 `backend` 容器是否存活，`nginx` 到 `backend:8080` 的网络是否正常。
- 头像 404：确认 `/opt/yunbq/uploads/avatars` 已挂载到 Nginx 或由后端映射 `/uploads/**` 并可访问。
- 跨域报错：确保后端 CORS 配置允许前端域名与方法（含 `PATCH`）。

---

如需将端口调整为 80/443 并启用证书，我可以基于你的证书路径或自动签发方式（如 `certbot`）提供相应的 Nginx 配置片段与 Compose 变更。

---

## 附：本地静态运行与快速验证（不启用开发工具）

若需在本地以接近生产的方式进行端到端验证（不启用任何开发服务器或热重载），请参考根目录文档：

- `本地运行-不启用开发工具.md`

该文档提供两种本地运行路径：
- 纯本机：后端使用打包 Jar 启动，前端以静态服务器或 Nginx 提供 `dist/`
- Docker：使用最小 `docker-compose.local.yml` 统一编排 MySQL、Redis、后端 Jar 与前端 Nginx

对齐规则与校验要点：
- 设置 `VITE_API_BASE=http://localhost:6639/api` 并进行前端生产构建
- 后端以 `--spring.profiles.active=prod` 启动，并显式传入数据库/Redis/JWT 密钥
- 浏览器访问 `http://localhost:5173`；接口调用指向 `http://localhost:6639/api`

---

## 附：一键部署脚本与模板（Windows/Linux）

为快速落地部署，仓库已提供脚本与模板：
- 脚本位置：`scripts/deploy/`
  - Linux：`scripts/deploy/linux/compose_deploy.sh`、`scripts/deploy/linux/compose_stop.sh`
  - Windows：`scripts/deploy/windows/compose_deploy.ps1`、`scripts/deploy/windows/compose_stop.ps1`
- 模板位置：`scripts/templates/`
  - Compose：`scripts/templates/docker-compose.yml`
  - Nginx：`scripts/templates/nginx/default.conf`
- 环境变量示例：`scripts/env/docker-compose.env.example`（复制为部署目录下的 `docker-compose.env` 并填写）

> Linux 脚本默认部署到 `/opt/yunbq`，并包含健康检查；Windows 脚本适用于本机验证。模板与脚本均含详细注释，可直接套用。