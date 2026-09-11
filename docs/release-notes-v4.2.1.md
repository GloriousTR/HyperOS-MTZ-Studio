# HyperOS MTZ Studio v4.2.1

## Düzeltme

- Uygulama açılışındaki otomatik güncelleme kontrolü ile “Güncellemeleri denetle” düğmesinin aynı APK dosyasını eşzamanlı indirmesine engel olundu.
- Güncelleme kontrolü artık tek işlem olarak çalışır. Bir kontrol sürerken gelen ikinci istek yeni indirme başlatmadan mevcut işlemin sonucunu kullanır.
- Geçici “güncelleme paketi uyumlu değil” ve dosya konumu ekranlarının görünmesine neden olan yarış durumu giderildi.
- Arka plan kontrolü, imza, paket adı, sürüm kodu ve SHA-256 doğrulamaları korunmuştur.
