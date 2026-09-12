# HyperOS MTZ Studio v4.5.0

## Güncel MTZ her zaman uygulanır

- Tema Dil Aracı bir MTZ'yi çevirdiğinde Xiaomi Temalar kitaplığındaki eski, çevrilmemiş kopyanın bağlantısı artık geçersiz kılınır.
- Shizuku/Shevery hazırsa çevrilen arşiv Xiaomi Temalar'ın yerel kitaplığına otomatik olarak yeniden aktarılır ve yeni yerel tema kimliği Studio kaydıyla eşleştirilir.
- Önceki sürümlerde çevrilmiş temalar, arşiv özeti karşılaştırılarak otomatik olarak algılanır; **Uygula** sırasında eski kopya yerine güncel MTZ yeniden hazırlanır.
- Otomatik eşitleme o anda tamamlanamazsa tema kaybolmaz; sonraki **Uygula** işleminde güncel arşivle güvenli biçimde tekrar denenir.

## Oluşturulan temalar ve Xiaomi kitaplığı

- **Temanı Oluştur** ile hazırlanan yeni MTZ, Studio kitaplığına eklendikten sonra desteklenen modern Xiaomi Temalar sürümlerinde yerel Xiaomi kitaplığına da hazırlanır.
- Böylece içe aktarılan ve oluşturulan temalar aynı Shizuku/Shevery uygulama yolunu kullanır.
- Root akışı ve eski Global Xiaomi Temalar sürümlerindeki mevcut tester yolu korunur.

## Diğer iyileştirmeler

- Rootlu cihazlarda Xiaomi Temalar'dan yerel tema alma seçeneğinin görünürlüğü düzeltildi.
- Uygulama içindeki proje, güncelleme, sorun bildirme ve sabit paket bağlantıları yeni [GloriousApps](https://github.com/GloriousApps) hesabına taşındı.
- Xiaomi Themes 3.0.5.6-global kurtarma paketi her sürüme yeniden eklenmez; doğrulanmış sabit dosya v4.0.0 varlıklarından indirilir.
