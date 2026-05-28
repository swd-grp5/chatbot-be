# Hướng dẫn chạy MySQL bằng Docker (SWDChatBox)

Project này dùng **Docker Compose** để chạy **MySQL 8.4** local cho backend.

> Chạy các lệnh bên dưới tại **thư mục gốc repo** (nơi có `docker-compose.yml`).

---

## Yêu cầu

- Docker Desktop
- Java 17+
- Maven (hoặc chạy bằng IDE)

Kiểm tra Docker:

```bash
docker --version
docker compose version
```

---

## Cấu trúc liên quan

```txt
SWDChatBox/
├── docker-compose.yml
├── .env.example
└── docker/
    └── mysql/
        └── init/
            └── 01-init.sql
```

---

## Thiết lập lần đầu

### 1) Tạo file môi trường `.env`

Mac/Linux:

```bash
cp .env.example .env
```

Windows (PowerShell):

```powershell
Copy-Item .env.example .env
```

### 2) Cấu hình MySQL trong `.env`

Ví dụ:

```env
MYSQL_DATABASE=swdchatbox
MYSQL_USER=swd_user
MYSQL_PASSWORD=your_password_here
MYSQL_ROOT_PASSWORD=your_root_password_here
MYSQL_PORT=3307
```

---

## Chạy / dừng database

### Chạy MySQL

```bash
docker compose up -d
```

### Xem log

```bash
docker logs -f swdchatbox
```

### Dừng MySQL (giữ nguyên data)

```bash
docker compose down
```

---

## 1 lệnh xoá sạch volume data DB (mất toàn bộ dữ liệu)

> Volume đang dùng trong `docker-compose.yml` là `swdchatbox`.

```bash
docker compose down -v --remove-orphans
```

Lệnh này sẽ xoá container + **volume data** (tức là DB sạch 100%).

---

## 1 lệnh khởi tạo lại DB (tạo DB mới từ đầu)

```bash
docker compose up -d --force-recreate
```

Lưu ý:
- Nếu bạn vừa xoá volume ở bước trên thì `up` sẽ tạo một MySQL “fresh” và chạy script init trong `docker/mysql/init/`.

---

## Kết nối DB từ IntelliJ / DataGrip

Thông tin kết nối (dựa theo `.env`):

```txt
Host: localhost
Port: 3307
Database: swdchatbox
Username: swd_user
Password: your_password_here
```

---

## Các lệnh hay dùng

### Xem container đang chạy

```bash
docker ps
```

### Xem volumes

```bash
docker volume ls
```

### Xoá volume thủ công (nếu cần)

```bash
docker volume rm swdchatbox
```

### Vào MySQL container

```bash
docker exec -it swdchatbox bash
```

Đăng nhập MySQL (root password lấy từ `.env`):

```bash
mysql -u root -p
```

---

## Troubleshooting

### Port bị chiếm

Nếu gặp lỗi kiểu:

```txt
Bind for 0.0.0.0:3307 failed
```

Sửa `.env` ví dụ:

```env
MYSQL_PORT=3308
```

Rồi chạy lại:

```bash
docker compose down
docker compose up -d
```

### Backend không connect được DB

Checklist nhanh:
- Container `swdchatbox` đang chạy (`docker ps`)
- Thông tin `.env` khớp với `be/src/main/resources/application.properties`
- Port đúng (`MYSQL_PORT`)

---

## Lưu ý bảo mật

Không commit `.env`. Chỉ commit `.env.example`.