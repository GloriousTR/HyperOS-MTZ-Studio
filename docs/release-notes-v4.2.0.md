# HyperOS MTZ Studio v4.2.0

## Yenilikler

- Uygulama her açıldığında GitHub üzerindeki son sürüm Android arka plan göreviyle otomatik olarak denetlenir; uygulama ekrandan ayrılsa da ağ uygun olduğunda işlem devam eder.
- Yeni sürüm bulunduğunda imzalı APK otomatik indirilir; paket adı, sürüm kodu, SHA-256 özeti ve uygulama imzası doğrulandıktan sonra 20 dilde “Güncelleme bulundu” bildirimi gönderilir. Bildirime dokunulduğunda kurulum açılır.
- Shizuku/Shevery kurulum sihirbazı, uygulama açıkken bildirimden dönüldüğünde de güvenilir biçimde yeniden açılır.
- Shizuku ADB izni açıklamaları rootsuz kullanım akışına göre 20 dilde düzeltildi.
- Tema koruma servisi bağlantı kesildiğinde beklemede kalır, kullanıcıyı bilgilendirir ve Shizuku/Shevery geri geldiğinde uygulama açılmadan otomatik devam eder.
- Tema geri dönüşü denetim aralığı kısaltıldı ve otomatik yeniden uygulama doğrulaması hızlandırıldı.
- Uyumsuz Xiaomi Temalar sürümü açıklaması güncel, doğrulanmış 3.0.5.6 sürüm düşürme akışına göre 20 dilde yenilendi.

## Güvenlik

Xiaomi Temalar sürüm düşürme ve MTZ Studio güncelleme işlemleri paket, sürüm, SHA-256 ve imza kontrolleri tamamlanmadan başlatılmaz. Android, uygulama güncellemesinin son kurulum onayını kullanıcıdan ister.
