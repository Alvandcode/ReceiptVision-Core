# ReceiptVision-Core

[![Stars](https://img.shields.io/github/stars/Alvandcode/ReceiptVision-Core?style=flat-square)](https://github.com/Alvandcode/ReceiptVision-Core/stargazers) [![License](https://img.shields.io/github/license/Alvandcode/ReceiptVision-Core?style=flat-square)](./LICENSE) [![Last commit](https://img.shields.io/github/last-commit/Alvandcode/ReceiptVision-Core?style=flat-square)](https://github.com/Alvandcode/ReceiptVision-Core/commits)

> Receipt OCR service with Spring Boot & Tesseract — fully Dockerized, Persian web UI + developer API.

<div dir="rtl">

## سرویس استخراج متن از رسید

سرویس استخراج متن از رسید با Spring Boot و Tesseract؛ کاملا داکرایز شده با رابط وب فارسی برای کاربران عادی و API برای توسعه‌دهندگان.

</div>

---

<div align="center">

# 🧾 ReceiptVision — دو نسخه در یک ایمیج

**👤 نسخه کاربر عادی:** رابط وب فارسی در `http://localhost:8080/` — بدون نیاز به دانش فنی
**🧑‍💻 نسخه دولوپر:** API مستند در `http://localhost:8080/api/receipts` + Swagger

ساخته‌شده با Spring Boot + Tesseract OCR • کاملاً Dockerized • بدون نیاز به نصب چیزی جز Docker

</div>

---

> ### 👤 کاربر عادی هستید و داکر/کد بلد نیستید؟
> **نه داکر لازم است، نه نصب، نه دستور.** فقط راهنما را بخوانید: **[USER-GUIDE-FA.md](USER-GUIDE-FA.md)**
> خلاصه: از صفحه **Releases** فایل `ReceiptVision-Windows.zip` را دانلود کنید → بازش کنید → روی `Start.bat` دابل‌کلیک کنید → برنامه باز می‌شود.

## 📖 درباره‌ی پروژه

ReceiptVision Core یک سرویس بک‌اند است که تصویر رسید را دریافت می‌کند، با استفاده از **Tesseract OCR** (داخل کانتینر، با دیتای فارسی `fas` و انگلیسی `eng`) متن آن را استخراج می‌کند و **فقط متن استخراج‌شده** را در دیتابیس H2 ذخیره می‌کند (خود عکس نگه داشته نمی‌شود).

از این نسخه به بعد **دو حالت مصرف** دارد که هر دو از یک ایمیج Docker می‌آیند:
- **👤 عادی:** `GET /` — صفحه فارسی drag&drop، پیش‌نمایش، نمایش متن، لیست/حذف. بدون `curl`. ورود با کوکی امن (`HttpOnly`) و تمدید خودکار نشست.
- **🧑‍💻 دولوپر:** `POST/GET /api/receipts` + `Swagger UI` + `Actuator` + احراز هویت با `Bearer` یا کوکی + رفرش‌توکن چرخشی (`POST /api/auth/refresh`).

## ✨ امکانات

- 📤 آپلود تصویر رسید از طریق API (`POST /api/receipts`)
- 🔍 استخراج خودکار متن با Tesseract CLI (پشتیبانی `fas+eng`، قابل تنظیم با `OCR_LANGUAGES`)
- 💾 ذخیره‌سازی در H2 فایلی روی `/data` (persist با والیوم داکر)
- 🐳 اجرای کامل با Docker / Compose، کاربر غیرروت، `HEALTHCHECK`
- 🌐 مستندات تعاملی Swagger UI + Actuator health
- ✅ ولیدیشن واقعی تصویر (magic-byte، سقف ابعاد و پیکسل)، هندلینگ خطای استاندارد، لیست صفحه‌بندی‌شده
- 🔑 نشست امن: access کوتاه‌عمر (`2h`) + رفرش‌توکن چرخشی (`30d`)، کوکی `HttpOnly` برای وب و `Bearer` برای API، بلک‌لیست ماندگار خروج
- 🛡️ ضد سوءاستفاده: ریت‌لیمیت لاگین/آپلود و سقف `2000` رسید برای هر کاربر
- 💾 اسکریپت بکاپ آماده (`Backup-ReceiptVision.bat` و `backup-receiptvision.sh`)

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

**۲. ساخت کلید امنیتی (فقط بار اول — بدون این، برنامه بالا نمی‌آید)**
```bash
# لینوکس/مک:
export APP_JWT_SECRET="$(openssl rand -base64 48)"
# ویندوز (PowerShell):
$b = New-Object byte[] 36; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); $env:APP_JWT_SECRET = [Convert]::ToBase64String($b)
```

**۳. اجرا با Compose (پیشنهادشده)**
```bash
docker compose up --build -d
```

**۳′. اجرا بدون Compose**
```bash
docker build -t receiptvision:0.1.0 .
docker run -d --name receiptvision -p 8080:8080 -v receipt-data:/data \
  -e APP_JWT_SECRET="$APP_JWT_SECRET" receiptvision:0.1.0
```

> ویندوزی‌ها راحت‌ترین راه: بدون کلون و دستور، فقط `Start-ReceiptVision.bat` را از ریپو دانلود و دابل‌کلیک کنید (کلید را خودش می‌سازد). جزئیات در [USER-GUIDE-FA.md](USER-GUIDE-FA.md).

> والیوم `receipt-data:/data` با `jdbc:h2:file:/data/receiptsdb` جفت شده و دیتا بعد از ری‌استارت باقی می‌ماند.

**۴. دسترسی**

| نسخه | سرویس | آدرس |
|---|---|---|
| 👤 عادی | وب فارسی | http://localhost:8080/ |
| 🧑‍💻 دولوپر | API | http://localhost:8080/api/receipts |
| 🧑‍💻 دولوپر | Swagger UI | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |
| کنسول H2 (فقط dev) | http://localhost:8080/h2-console |

> H2 Console به‌صورت پیش‌فرض **خاموش** است (`H2_CONSOLE_ENABLED=false`). برای توسعه موقتاً (سکرت هم لازم است):
> ```bash
> export APP_JWT_SECRET="$(openssl rand -base64 48)"
> H2_CONSOLE_ENABLED=true docker compose up
> ```

مشخصات اتصال H2:
- حالت dev (پروفایل `dev`): دیتابیس موقت در حافظه — `JDBC URL: jdbc:h2:mem:receiptsdb` • `User: sa` • `Password:` خالی
- حالت داکر: فایل روی والیوم — `JDBC URL: jdbc:h2:file:/data/receiptsdb` • `User: sa` • `Password:` خالی (مگر با `SPRING_DATASOURCE_PASSWORD` عوضش کنید)

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
- شناسه رسید دیگری → `404` (نه `403` با محتوا) تا لو نرود چه چیزی وجود دارد. پیام لاگین اشتباه همیشه یکسان است (`401 Invalid username or password`) تا نام کاربری لو نرود. نام تکراری در ثبت‌نام `409` می‌دهد و با ریت‌لیمیت ضد شمارش است.
- نام کاربری به حروف کوچک نرمال می‌شود (`Ali` و `ali` یکی‌اند) تا جعل هویتی نشود. رمز ≥۸ حرف شامل **حرف+عدد**، هش `BCrypt(12)`.
- توکن `JWT HS256` کوتاه‌عمر (`2h`) با `jti` + بلک‌لیست **ماندگار در DB** (ری‌استارت پاک نمی‌شود، purge ساعتی) + رفرش‌توکن opaque چرخشی (`30d`، هش SHA-256، reuse یعنی سرقت → ابطال همه نشست‌ها). بدون `APP_JWT_SECRET` برنامه بالا نمی‌آید.
- آپلود فقط تصویر واقعی (`ImageIO` + سقف ابعاد 8000 و ۵۰ مگاپیکسل)، سقف `10MB` و سهمیه `2000` رسید/کاربر، ریت‌لیمیت `20/min` برای auth و `30/min` برای آپلود.
- لاگ‌ها هرگز `ocrText` و مسیر موقت چاپ نمی‌کنند؛ خطای OCR به کلاینت جنریک است (`422 OCR failed`).
- فایل‌های قدیمی بدون مالک (`owner IS NULL`) در استارت‌آپ **حذف قطعی** می‌شوند (`OrphanReceiptPurgeRunner`) مگر `PURGE_ORPHANS=false` که فقط سرو را متوقف می‌کند.
- وب‌UI با کوکی `HttpOnly` (`rv_at`/`rv_rt`، `SameSite=Strict`) کار می‌کند — توکن در `localStorage` نیست (مقاوم در برابر XSS). API/Curl همچنان `Authorization: Bearer` قبول می‌کند. درخواست کوکیِ تغییردهنده نیاز به هدر `X-Requested-With` یا هم‌مبدا بودن دارد.
- `H2 Console` پیش‌فرض `false`. بدون نشست معتبر همه `/api/receipts` می‌شوند `401`. هدرهای `CSP/HSTS/X-Frame-Deny` و `CORS` محدود به same-origin فعال‌اند.
- بکاپ: `Backup-ReceiptVision.bat` (ویندوز) یا `sh backup-receiptvision.sh` (لینوکس) — والیوم `receipt-data` و پوشه `data/` را فشرده در `backups/` می‌گذارد. قبل هر آپدیت بکاپ بگیر.
- برای پروداکشن حتماً پشت `HTTPS` (reverse proxy) بگذارید و `APP_JWT_SECRET` رندوم بدهید:
```bash
export APP_JWT_SECRET="$(openssl rand -base64 48)"
docker compose up --build -d
```

## 📡 مستندات API (همه خصوصی، نیازمند JWT)

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"ali_90","password":"S3curePass1"}'
# -> {"username":"ali_90","token":"eyJ...","refreshToken":"...","tokenType":"Bearer"}
# (مرورگر توکن‌ها را در کوکی HttpOnly می‌گیرد و لازم نیست جایی ذخیره‌شان کند)
# قانون نام کاربری و رمز: انگلیسی ۳ تا ۳۲ حرف (`a-z 0-9 _ -`)، رمز ۸ تا ۱۰۰ حرف شامل حداقل یک حرف و یک عدد

TOKEN=eyJ...
REFRESH=...
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

محدودیت‌ها: فقط `image/jpeg,png,webp,tiff,bmp` واقعی (magic-byte) • حداکثر `10MB` و ابعاد 8000 • فایل خالی و نام شامل `..` رد می‌شود (`400`) • تصویر غیرواقعی `400` • نام کاربری تکراری `409` • خطای OCR جنریک `422` • حجم بیش از حد `413` • احراز هویت زیاد `429` • سهمیه هر کاربر `2000` رسید • فقط متن عکس ذخیره می‌شود (بایت تصویر نگه داشته نمی‌شود)، فیلد `sort` فقط `id,createdAt,fileName,size`.

```bash
# تمدید نشست (مرورگر با کوکی خودکار انجامش می‌دهد؛ برای API رفرش‌توکن را بدهید):
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"..."}'
# -> access + refresh جدید (refresh قبلی باطل می‌شود؛ استفاده مجدد از refresh باطل‌شده همه نشست‌ها را می‌بندد)

# مشاهده حساب جاری:
curl http://localhost:8080/api/auth/me -H "Authorization: Bearer $TOKEN"
# -> {"username":"ali_90","token":"","refreshToken":null,"tokenType":"Bearer"}

# خروج (ابطال access + refresh + پاک‌سازی کوکی):
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer $TOKEN" -i
# 204 No Content
```

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
| `APP_JWT_SECRET` | — | **اجباری، بدون پیش‌فرض** — برنامه بدون آن بالا نمی‌آید (fail-closed)، ≥۳۲ بایت رندوم |
| `JWT_EXPIRATION_HOURS` | `2` | عمر access توکن (۱ تا ۲۴ ساعت). نشست طولانی با رفرش‌توکن (۳۰ روزه) تمدید می‌شود |
| `REFRESH_DAYS` | `30` | عمر رفرش‌توکن opaque (۱ تا ۹۰ روز، چرخشی) |
| `COOKIE_SECURE` | `false` | در پروداکشن HTTPS حتما `true` (کوکی فقط روی TLS) |
| `RATELIMIT_AUTH_PER_MINUTE` | `20` | سقف درخواست احراز هویت در دقیقه (ضد بروت‌فورس) |
| `RATELIMIT_UPLOAD_PER_MINUTE` | `30` | سقف آپلود OCR در دقیقه |
| `MAX_RECEIPTS_PER_USER` | `2000` | سقف تعداد رسید هر کاربر (ضد اسپم دیتابیس) |
| `PURGE_ORPHANS` | `true` | حذف ردیف‌های بدون مالک در استارت (`false` = نگه‌دار ولی سرو نکن) |
| `DDL_AUTO` | `update` | حالت Hibernate؛ در محیط سخت‌گیرانه `validate` بگذارید |
| `PURGE_INTERVAL_MS` | `3600000` | فاصله پاک‌سازی توکن‌های منقضی (۱ ساعت) |
| `OCR_LANGUAGES` | `fas+eng` | زبان‌های Tesseract |
| `OCR_PSM` | `3` | Page segmentation mode |
| `OCR_TIMEOUT_SECONDS` | `30` | تایم‌اوت OCR |
| `JAVA_OPTS` | `-XX:MaxRAMPercentage=75.0 ...` | تنظیمات JVM در کانتینر |

---

## 🛠️ عیب‌یابی

| مشکل | علت محتمل / راه‌حل |
|---|---|
| `docker compose up` خطای `APP_JWT_SECRET` می‌دهد | عمدی است: بدون سکرت برنامه بالا نمی‌آید. اول قدم «۲. ساخت کلید امنیتی» همین راهنما را انجام دهید (لینوکس `export`، ویندوز PowerShell) یا از `Start-ReceiptVision.bat` استفاده کنید |
| `401` وسط کار / «نشست منقضی شد» | access هر `2h` می‌میرد؛ مرورگر با رفرش‌توکن (۳۰ روزه) خودش تمدید می‌کند. با Curl باید `POST /api/auth/refresh` بزنید وگرنه دوباره لاگین کنید |
| `409` موقع ثبت‌نام | نام کاربری گرفته شده؛ یکی دیگر انتخاب کنید |
| `429 Too Many Requests` | ریت‌لیمیت (۲۰ لاگین یا ۳۰ آپلود در دقیقه)؛ یک دقیقه صبر کنید |
| `docker build` خطای `COPY failed: pom.xml` | نسخه قدیمی ریپو بود؛ الان `pom.xml` موجود است. `git pull` کنید. |
| دیتا بعد از ری‌استارت پاک می‌شود | حتماً `-v receipt-data:/data` یا Compose استفاده کنید؛ `SPRING_DATASOURCE_URL` را به `mem:` تغییر ندهید. |
| فارسی خراب OCR می‌شود | ایمیج جدید `tesseract-ocr-fas` دارد؛ `docker compose up --build` بزنید و `OCR_LANGUAGES=fas+eng` باشد. |
| `413 Payload Too Large` | فایل >۱۰MB است؛ کوچک/فشرده کنید یا `spring.servlet.multipart.max-file-size` را بالا ببرید. |
| `422 Unprocessable Entity` | Tesseract متن استخراج نکرد/fail شد؛ لاگ کانتینر (`docker logs`) را ببینید. |

---

## 🤝 مشارکت

ایشو یا PR بفرستید. قبل از PR حتماً `mvn -B verify` سبز باشد.

---

## 📄 لایسنس

این پروژه تحت لایسنس [MIT](LICENSE) منتشر شده است.

---

## Contributing / مشارکت

- EN: Issues and Pull Requests are welcome. Please see `CONTRIBUTING.md`.
- FA: برای گزارش مشکل یا پیشنهاد قابلیت جدید، لطفا ایشو یا پول‌ریکوئست ثبت کنید.

## License / لایسنس

MIT — see [LICENSE](./LICENSE).

## Contact / ارتباط

- Telegram: https://t.me/a_c_official
- Website: https://alvandcode.github.io
