# Stage 1: Build dự án với Maven và OpenJDK 17
FROM maven:3.8.8-amazoncorretto-17 AS build
WORKDIR /app

# Copy file cấu hình pom.xml và tải trước các dependencies (tận dụng cache của Docker)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy toàn bộ mã nguồn vào trong container để build
COPY src ./src

# Build ra file .jar ứng dụng và bỏ qua chạy bộ test để tăng tốc độ build

RUN mvn clean package -Dmaven.test.skip=true

# Stage 2: Tạo ảnh Runtime siêu nhẹ để chạy ứng dụng
FROM amazoncorretto:17-alpine
WORKDIR /app

# Copy file .jar đã build xong từ Stage 1 sang Stage 2
COPY --from=build /app/target/*.jar app.jar

# Khai báo cổng ứng dụng (Ví dụ Spring Boot của nhóm chạy cổng 8080)
EXPOSE 8080

# Lệnh khởi chạy ứng dụng Spring Boot khi Container cất cánh
ENTRYPOINT ["java", "-jar", "app.jar"]
