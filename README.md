<div align="center">

# 🧾 ReceiptVision Core

**سرویس ساده و سریع برای خواندن هوشمند رسیدها (OCR) و مدیریت آن‌ها**

ساخته‌شده با Spring Boot + Tesseract OCR • کاملاً Dockerized • بدون نیاز به نصب چیزی جز Docker

</div>

---

## 📖 درباره‌ی پروژه

ReceiptVision Core یک سرویس بک‌اند است که تصویر رسید را دریافت می‌کند، با استفاده از **Tesseract OCR** متن آن را استخراج می‌کند و اطلاعات را در دیتابیس ذخیره می‌کند. این پروژه برای کسی طراحی شده که می‌خواهد بدون درگیر شدن با نصب Java، Maven یا Tesseract، فقط با یک دستور Docker سرویس را بالا بیاورد.

## ✨ امکانات

- 📤 آپلود تصویر رسید از طریق API
- 🔍 استخراج خودکار متن با Tesseract OCR (پشتیبانی از فارسی و انگلیسی)
- 💾 ذخیره‌سازی رسیدها در دیتابیس (H2، با قابلیت persist کردن روی دیسک)
- 🐳 اجرای کامل با یک دستور Docker، بدون نیاز به نصب پیش‌نیاز
- 🌐 پنل مدیریت دیتابیس تحت وب (H2 Console)

---

## 🚀 اجرای سریع با Docker (پیشنهادشده)

### پیش‌نیاز
فقط **Docker** روی سیستم شما نصب باشد.

### مراحل

**۱. کلون کردن پروژه**
```bash
git clone https://github.com/<your-username>/ReceiptVision-Core.git
cd ReceiptVision-Core
```

**۲. ساخت ایمیج**
```bash
docker build -t receiptvision .
```

**۳. اجرا**
```bash
docker run -p 8080:8080 -v receipt-data:/data receiptvision
```
> فلگ `-v receipt-data:/data` باعث می‌شود داده‌های شما بعد از ری‌استارت کانتینر هم باقی بمانند.

**۴. دسترسی**

| سرویس | آدرس |
|---|---|
| API | http://localhost:8080 |
| کنسول دیتابیس H2 | http://localhost:8080/h2-console |

---

## 🧪 اجرای بدون Docker (برای توسعه‌دهنده‌ها)

پیش‌نیاز: Java 17، Maven، و Tesseract OCR نصب‌شده روی سیستم (به همراه پکیج زبان فارسی `tesseract-ocr-fas`).

```bash
mvn spring-boot:run
```

---

## 📡 مستندات API

### آپلود رسید
```http
POST /api/receipts
Content-Type: multipart/form-data

file: <image>
```

### دریافت لیست رسیدها
```http
GET /api/receipts
```

### دریافت یک رسید خاص
```http
GET /api/receipts/{id}
```

---

## ⚙️ تنظیمات پیشرفته

می‌توانید با متغیرهای محیطی زیر رفتار سرویس را تغییر دهید:

| متغیر | مقدار پیش‌فرض | توضیح |
|---|---|---|
| `PORT` | `8080` | پورت اجرای سرویس |
| `SPRING_DATASOURCE_URL` | `jdbc:h2:file:/data/receiptsdb` | آدرس دیتابیس |

---

## 🤝 مشارکت

اگر پیشنهاد، باگ یا قابلیت جدیدی مد نظرتان است، خوشحال می‌شویم از طریق Issue یا Pull Request با ما در میان بگذارید.

---

## 💛 حمایت از پروژه

اگر این پروژه براتون مفید بود:

- ⭐️ به ریپازیتوری یک **Star** بدید
- 👤 صفحه‌ی گیت‌هاب رو **Follow** کنید تا از پروژه‌های بعدی باخبر بشید
- 📢 عضو کانال تلگرام ما بشید برای اطلاع از آپدیت‌ها و پروژه‌های جدید:

  **[@a_c_official](https://t.me/a_c_official)**

---

## 📄 لایسنس

این پروژه تحت لایسنس [MIT](LICENSE) منتشر شده است.

</div>
