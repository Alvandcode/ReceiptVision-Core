<div align="center">

# 🧾 ReceiptVision — دو نسخه در یک ایمیج

**👤 نسخه کاربر عادی:** رابط وب فارسی در `http://localhost:8080/` — بدون نیاز به دانش فنی
**🧑‍💻 نسخه دولوپر:** API مستند در `http://localhost:8080/api/receipts` + Swagger

ساخته‌شده با Spring Boot + Tesseract OCR • کاملاً Dockerized • بدون نیاز به نصب چیزی جز Docker

</div>

---

> ### 👤 کاربر عادی هستید و داکر/کد بلد نیستید؟
> **هیچ دستوری لازم نیست.** فقط راهنمای ۳قدمی را بخوانید: **[USER-GUIDE-FA.md](USER-GUIDE-FA.md)**
> خلاصه: Docker Desktop نصب کنید → روی `Start-ReceiptVision.bat` دابل‌کلیک کنید → مرورگر باز می‌شود.
> ایمیج آماده است (`ghcr.io/alvandcode/receiptvision-core:latest`) و نیازی به سورس‌کد، جاوا یا `docker build` نیست.

## 📖 درباره‌ی پروژه

ReceiptVision Core یک سرویس بک‌اند است که تصویر رسید را دریافت می‌کند، با استفاده از **Tesseract OCR** (داخل کانتینر، با دیتای فارسی `fas` و انگلیسی `eng`) متن آن را استخراج می‌کند و در دیتابیس H2 ذخیره می‌کند.

از این نسخه به بعد **دو حالت مصرف** دارد که هر دو از یک ایمیج Docker می‌آیند و API هیچ تغییری نکرده:
- **👤 عادی:** `GET /` — صفحه فارسی drag&drop، پیش‌نمایش، نمایش متن، لیست/حذف. بدون `curl`.
- **🧑‍💻 دولوپر:** `POST/GET /api/receipts` + `Swagger UI` + `Actuator`.

## ✨ امکانات

- 📤 آپلود تصویر رسید از طریق API (`POST /api/receipts`)
- 🔍 استخراج خودکار متن با Tesseract CLI (پشتیبانی `fas+eng`، قابل تنظیم با `OCR_LANGUAGES`)
- 💾 ذخیره‌سازی در H2 فایلی روی `/data` (persist با والیوم داکر)
- 🐳 اجرای کامل با Docker / Compose، کاربر غیرروت، `HEALTHCHECK`
- 🌐 مستندات تعاملی Swagger UI + Actuator health
- ✅ ولیدیشن نوع/حجم فایل، هندلینگ خطای استاندارد، لیست صفحه‌بندی‌شده

---

## 🚀 اجرای سریع با Docker (پیشنهادشده)

### پیش‌نیاز

فقط **Docker** (اختیاری: Docker Compose).

### مراحل

**۱. کلون کردن پروژه**
```bash
git clone https://github.com/Alvandcode/ReceiptVision-Core.git
cd ReceiptVision-Core
```

**۲. اجرا با Compose (پیشنهادشده)**
```bash
docker compose up --build -d
```

**۲′. اجرا بدون Compose**
```bash
docker build -t receiptvision:0.1.0 .
docker run -d --name receiptvision -p 8080:8080 -v receipt-data:/data receiptvision:0.1.0
```

> والیوم `receipt-data:/data` با `jdbc:h2:file:/data/receiptsdb` جفت شده و دیتا بعد از ری‌استارت باقی می‌ماند.

**۳. دسترسی**

| نسخه | سرویس | آدرس |
|---|---|---|
| 👤 عادی | وب فارسی | http://localhost:8080/ |
| 🧑‍💻 دولوپر | API | http://localhost:8080/api/receipts |
| 🧑‍💻 دولوپر | Swagger UI | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |
| کنسول H2 (فقط dev) | http://localhost:8080/h2-console |

> H2 Console به‌صورت پیش‌فرض **خاموش** است (`H2_CONSOLE_ENABLED=false`). برای توسعه: `H2_CONSOLE_ENABLED=true docker compose up`.

مشخصات اتصال H2 در حالت dev:
`JDBC URL: jdbc:h2:file:/data/receiptsdb` • `User: sa` • `Password: (خالی، مگر با SPRING_DATASOURCE_PASSWORD عوضش کنید)`

---

## 🧪 اجرای بدون Docker (برای توسعه‌دهنده‌ها)

پیش‌نیاز: Java 17، Maven 3.9+، و Tesseract OCR (به همراه `tesseract-ocr-fas` برای فارسی).

```bash
# اوبونتو/دبیان:
sudo apt install -y tesseract-ocr tesseract-ocr-fas tesseract-ocr-eng

# اجرا (پروفایل dev = دیتابیس in-memory + کنسول H2 روشن):
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# تست‌ها:
mvn -B verify
```

---

## 🔒 حریم خصوصی (سخت‌گیرانه)

- هر رسید `owner_id` دارد. کوئری‌ها فقط `findByOwner` هستند؛ هیچ `findAll` بدون مالک در کد نیست.
- شناسه чужой → `404` (نه `403` با محتوا) تا اوراکل وجودی لو نرود. پیام لاگین اشتباه همیشه یکسان است (`401 Invalid username or password`) تا نام کاربری لو نرود.
- رمزها `BCrypt(12)`، توکن `JWT HS256` با `APP_JWT_SECRET` ≥۳۲ بایت. لاگ‌ها هرگز `ocrText` چاپ نمی‌کنند.
- فایل‌های قدیمی بدون مالک (`owner IS NULL`) در استارت‌آپ **حذف قطعی** می‌شوند (`OrphanReceiptPurgeRunner`) چون مالک قابل اثبات ندارند.
- `H2 Console` پیش‌فرض `false`. بدون `Authorization: Bearer <jwt>` همه `/api/receipts` می‌شوند `401`.
- برای پروداکشن حتماً پشت `HTTPS` (reverse proxy) بگذارید و `APP_JWT_SECRET` رندوم بدهید:
```bash
export APP_JWT_SECRET="$(openssl rand -base64 48)"
docker compose up --build -d
```

## 📡 مستندات API (همه خصوصی، نیازمند JWT)

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"ali_90","password":"S3curePass!"}'
# -> {"username":"ali_90","token":"eyJ...","tokenType":"Bearer"}

TOKEN=eyJ...
curl -X POST http://localhost:8080/api/receipts \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/path/to/receipt.jpg"
# 201 Created, فقط در حساب شما
```

پاسخ موفق `201`:
```json
{
  "id": 1,
  "fileName": "receipt.jpg",
  "contentType": "image/jpeg",
  "size": 142331,
  "ocrText": "TOTAL 125000 ...",
  "language": "fas+eng",
  "createdAt": "2026-09-11T10:00:00Z"
}
```

محدودیت‌ها: فقط `image/jpeg,png,webp,tiff,bmp` • حداکثر `10MB` • فایل خالی و نام شامل `..` رد می‌شود (`400`) • خطای OCR می‌شود `422` • حجم بیش از حد می‌شود `413`.

### دریافت لیست رسیدهای خودم

```bash
curl "http://localhost:8080/api/receipts?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

پاسخ، `Page` استاندارد Spring است (`content`, `totalElements`, `totalPages`, `number`, `size`). سقف `size=100` برای جلوگیری از OOM.

### دریافت یک رسید خاص

```bash
curl http://localhost:8080/api/receipts/1 -H "Authorization: Bearer $TOKEN"
# 404 اگر مال شما نباشد یا نباشد (عمداً یکسان تا لو نرود)
```

### حذف یک رسید

```bash
curl -X DELETE http://localhost:8080/api/receipts/1 -H "Authorization: Bearer $TOKEN" -i
# 204 No Content (فقط مال خودتان)
```

---

## ⚙️ تنظیمات (متغیرهای محیطی)

| متغیر | پیش‌فرض | توضیح |
|---|---|---|
| `PORT` | `8080` | پورت (`server.port=${PORT}`) |
| `SPRING_DATASOURCE_URL` | `jdbc:h2:file:/data/receiptsdb;...` | آدرس دیتابیس |
| `SPRING_DATASOURCE_USERNAME` | `sa` | یوزر دیتابیس |
| `SPRING_DATASOURCE_PASSWORD` | `` (خالی) | در پروداکشن حتماً عوض کنید |
| `H2_CONSOLE_ENABLED` | `false` | فقط برای dev موقتاً `true` کنید (با دیتای واقعی هرگز) |
| `APP_JWT_SECRET` | — | **اجباری در پروداکشن**، ≥۳۲ بایت رندوم |
| `JWT_EXPIRATION_HOURS` | `168` | عمر توکن |
| `OCR_LANGUAGES` | `fas+eng` | زبان‌های Tesseract |
| `OCR_PSM` | `3` | Page segmentation mode |
| `OCR_TIMEOUT_SECONDS` | `30` | تایم‌اوت OCR |
| `JAVA_OPTS` | `-XX:MaxRAMPercentage=75.0 ...` | تنظیمات JVM در کانتینر |

---

## 🛠️ عیب‌یابی

| مشکل | علت محتمل / راه‌حل |
|---|---|
| `docker build` خطای `COPY failed: pom.xml` | نسخه قدیمی ریپو بود؛ الان `pom.xml` موجود است. `git pull` کنید. |
| دیتا بعد از ری‌استارت پاک می‌شود | حتماً `-v receipt-data:/data` یا Compose استفاده کنید؛ `SPRING_DATASOURCE_URL` را به `mem:` تغییر ندهید. |
| فارسی خراب OCR می‌شود | ایمیج جدید `tesseract-ocr-fas` دارد؛ `docker compose up --build` بزنید و `OCR_LANGUAGES=fas+eng` باشد. |
| `413 Payload Too Large` | فایل >۱۰MB است؛ کوچک/فشرده کنید یا `spring.servlet.multipart.max-file-size` را بالا ببرید. |
| `422 Unprocessable Entity` | Tesseract متن استخراج نکرد/fail شد؛ لاگ کانتینر (`docker logs`) را ببینید. |

---

## 🤝 مشارکت

ایssue یا PR بفرستید. قبل از PR حتماً `mvn -B verify` سبز باشد.

---

## 📄 لایسنس

این پروژه تحت لایسنس [MIT](LICENSE) منتشر شده است.
