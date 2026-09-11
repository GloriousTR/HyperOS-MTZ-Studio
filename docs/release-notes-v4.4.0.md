# HyperOS MTZ Studio v4.4.0

## Shizuku ile modern Xiaomi Temalar desteği

- Xiaomi Temalar 10.8.7.6 üzerinde MTZ Studio'dan **Uygula** işlemi gerçek cihazda doğrulandı.
- MTZ, Shizuku/Shevery üzerinden Xiaomi Temalar'ın yerel kitaplığına aktarılır ve oluşan yerel tema kimliği Studio kaydıyla eşleştirilir.
- Modern Temalar sürümlerinde yerel tema kimliği hazır olduğunda işlem artık yanlışlıkla root gerektiren eski tester yoluna düşmez.
- Daha önce Studio kitaplığına eklenen temalar, uygulama öncesinde Xiaomi'nin yerel kitaplığıyla otomatik eşleştirilir.
- Root izni verilmemiş Shizuku cihazlarında gereksiz **Root erişimi gerekli** uyarısı engellendi.

## Korunan akışlar

- Eski Global Xiaomi Temalar sürümleri için mevcut tester akışı korunur.
- Root modu ve tema kalıcılığı mekanizması değişmeden çalışmaya devam eder.
- Güncelleme denetimi, ilerlemeli indirme ve paket/imza doğrulaması korunur.
