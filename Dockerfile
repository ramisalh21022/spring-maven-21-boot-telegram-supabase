# ----------------------
# مرحلة البناء
# ----------------------
FROM maven:3.9.3-eclipse-temurin-21 AS build

# إعداد مجلد العمل
WORKDIR /app

# نسخ ملفات المشروع
COPY pom.xml .
COPY src ./src

# بناء المشروع (سيتم توليد target وملفات .mvn داخليًا)
RUN mvn clean package -DskipTests

# ----------------------
# مرحلة التشغيل
# ----------------------
FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

# نسخ ملف الـ jar الناتج من مرحلة البناء
COPY --from=build /app/target/*.jar app.jar

# تحديد الأمر لتشغيل التطبيق
ENTRYPOINT ["java", "-jar", "app.jar"]
