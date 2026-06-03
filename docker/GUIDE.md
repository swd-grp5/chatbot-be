# Deploy Spring BE bằng Docker Compose

Stack này chạy **API Spring Boot (cổng 8080)**. **Không** chạy MySQL — DB phải có sẵn (VPS, Portainer stack khác, managed DB).

> Chạy lệnh tại **thư mục gốc repo** (`chatbot-be`, có `docker-compose.yml`).

---

## Yêu cầu

- Docker / Portainer có quyền build image
- MySQL đã chạy và cho phép kết nối từ host/container BE

---

## Cấu trúc

```txt
chatbot-be/
├── docker-compose.yml   # service be → :8080
├── .env.example
└── be/
    └── Dockerfile
```

---

## Biến môi trường

| Biến | Bắt buộc | Mô tả |
|------|----------|--------|
| `MYSQL_HOST` | Có | IP/hostname MySQL (vd. IP VPS) |
| `MYSQL_PASSWORD` | Có | Mật khẩu user DB |
| `JWT_SECRET` | Có | Secret JWT |
| `MYSQL_PORT` | Không (3306) | Cổng MySQL |
| `MYSQL_DATABASE` | Không | Tên database |
| `MYSQL_USER` | Không | User DB |
| `SPRING_PORT` | Không (8080) | Cổng **host** map vào API |
| `GOOGLE_CLIENT_ID`, `MAIL_*`, `CORS_*`, `APP_PUBLIC_URL` | Tùy tính năng | Xem `.env.example` |

Compose **không** dùng `env_file` (tránh lỗi Portainer thiếu `.env`).

### Local

```powershell
Copy-Item .env.example .env
# Điền MYSQL_HOST, MYSQL_PASSWORD, JWT_SECRET, ...
docker compose up -d --build
```

API: `http://localhost:8080` — health: `GET /api/health`

### Portainer

**Stack → Environment variables** — tối thiểu:

```env
MYSQL_HOST=168.144.98.120
MYSQL_PORT=3306
MYSQL_DATABASE=swdchatbox
MYSQL_USER=swd_user
MYSQL_PASSWORD=...
JWT_SECRET=...
SPRING_PORT=8080
GOOGLE_CLIENT_ID=...
CORS_ALLOWED_ORIGINS=http://168.144.98.120:5173,https://your-fe.com
APP_PUBLIC_URL=http://168.144.98.120:8080
VERIFY_BASE_URL=http://168.144.98.120:8080
```

Redeploy stack (build lại lần đầu hoặc khi đổi code).

Upload tài liệu lưu volume `be-uploads` → `/app/uploads` trong container.

---

## Lệnh hay dùng

```bash
docker compose logs -f be
docker compose down
docker compose up -d --build
```

---

## Troubleshooting

### `Bind for 0.0.0.0:8080 failed: port is already allocated`

Đổi `SPRING_PORT=8081` (hoặc cổng trống) trong stack env, redeploy.

### `Bind for 0.0.0.0:3306` khi deploy stack DB

Đó là stack **MySQL riêng**, không phải file compose BE này. BE chỉ cần `MYSQL_HOST` + `MYSQL_PORT` trỏ DB đó.

### BE không connect DB

- `MYSQL_HOST` reachable từ container (firewall, user `%` / IP cho phép)
- User/password/database đúng
- Nếu MySQL trên **cùng VPS**: có thể dùng IP VPS hoặc IP bridge Docker của container MySQL

---

## MySQL local (tùy chọn, không trong compose BE)

Script init: `docker/mysql/init/`. Có thể chạy MySQL thủ công hoặc stack DB riêng trên Portainer, rồi trỏ `MYSQL_HOST` vào đó.

---

## Bảo mật

Không commit `.env`. Chỉ commit `.env.example`.
