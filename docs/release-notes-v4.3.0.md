# HyperOS MTZ Studio v4.3.0

## Modern Xiaomi Temalar akışı

- Xiaomi Temalar 10.8.7.6 ve sonrası sürümlerde, Shizuku/Shevery ile içe aktarılan MTZ hem Studio kitaplığına hem Xiaomi'nin yerel tema kitaplığına eklenebilir.
- Xiaomi tarafından oluşturulan yerel tema kimliği Studio kaydıyla eşleştirilir; sonraki **Uygula** işlemi yeniden içe aktarma istemeden doğrudan tema ayrıntısı ve uygulama isteğine geçer.
- Eski Global tester yolu ve Root köprüsü korunur; desteklenmeyen bir cihazda başarılı Studio içe aktarımı silinmez.

## Kitaplık yönetimi

- **Tümünü Yönet** bağlantısı daha açıklayıcı **Kitaplığı Yönet** adına geçirildi.
- Temalar kutucuklarla seçilebilir, topluca seçilebilir ve tek işlemde kaldırılabilir.
- Desteklenen Tema Yöneticisi profillerinde yerel Xiaomi temalarını Studio kitaplığına alma işlemi aynı yönetim ekranına taşındı.

## Güncelleme deneyimi

- Uygulama her ön plana geldiğinde GitHub sürümünü sessizce denetler.
- Yeni sürüm bulunduğunda indirme başlamadan önce kullanıcıya bildirilir; **İndir ve Kur** veya **Daha Sonra** seçilebilir.
- APK indirme yüzdesi hem uygulama içinde hem arka plan bildiriminde canlı gösterilir.
- Paket adı, sürüm, SHA-256 ve imza doğrulamaları kurulum ekranından önce tamamlanır.

## Arayüz ve bakım

- MTZ Import ekranındaki tekli seçim **Bir MTZ Seçin** olarak sadeleştirildi.
- Tekli ve çoklu seçim düğmeleri aynı vurgu rengi ve boyutuyla iki ayrı işlem olarak belirginleştirildi.
- Shizuku ile Tema Yöneticisi sürüm düşürme işleminde APK önce güvenli sistem kurulum alanına taşınır.
