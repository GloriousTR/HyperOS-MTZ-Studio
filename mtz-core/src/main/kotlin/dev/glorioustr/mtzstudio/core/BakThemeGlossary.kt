package dev.glorioustr.mtzstudio.core

import java.util.Locale

/**
 * Domain-specific glossary and natural language processor for Xiaomi HyperOS / MIUI themes.
 * Provides high-accuracy, natural, native translations for UI widgets, weather conditions,
 * battery/charging statuses, calendar/date formats, health metrics, gestures, and settings.
 */
object BakThemeGlossary {

    private val CHINESE_CHARS = Regex("[\\p{IsHan}]+")

    fun containsChinese(text: String): Boolean = CHINESE_CHARS.containsMatchIn(text)

    /**
     * Protects Xiaomi/MAML product terms from being interpreted as ordinary Chinese words by
     * the on-device model. English is intentional here: this text is fed to the Chinese ->
     * English leg and then translated to the device language.
     */
    fun prepareChineseForEnglishPivot(text: String): String {
        var prepared = text
        CHINESE_PIVOT_TERMS.forEach { (source, replacement) ->
            prepared = prepared.replace(source, " $replacement ")
        }
        return prepared.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Resolves a Chinese phrase using the domain glossary for the target language (defaulting to Turkish).
     * Returns null if no exact or rule-based match is found, signaling that external ML translation is needed.
     */
    fun resolve(chineseText: String, targetLanguage: String = "tr"): String? {
        val trimmed = chineseText.trim()
        if (trimmed.isEmpty()) return null

        val lang = targetLanguage.lowercase(Locale.ROOT)
        if (lang.startsWith("tr")) {
            resolveTurkish(trimmed)?.let { return chineseText.replace(trimmed, it) }
        } else if (lang == "en") {
            resolveEnglish(trimmed)?.let { return chineseText.replace(trimmed, it) }
        }
        if (lang == "tr" || lang == "en") resolveCompound(trimmed, lang)?.let { return it }
        val phrase = Regex("^([^\\p{L}\\d]*)([\\p{IsHan}]+)([^\\p{L}\\d]*)$").matchEntire(chineseText)
        if (phrase != null && (lang == "tr" || lang == "en")) {
            val translated = if (lang == "tr") resolveTurkish(phrase.groupValues[2]) else resolveEnglish(phrase.groupValues[2])
            if (translated != null) return phrase.groupValues[1] + translated + phrase.groupValues[3]
        }

        // Try compound regex patterns (e.g. numbers + units)
        if (lang == "tr" || lang == "en") resolveCompound(trimmed, lang)?.let { return it }

        return null
    }

    /**
     * Converts Chinese date format pattern strings (commonly in formatDate / formatTime)
     * into localized, clean Android SimpleDateFormat patterns.
     */
    fun convertDatePattern(pattern: String, language: String = "tr"): String? {
        if (!containsChinese(pattern)) return null
        // Only token-attached units are syntax. Do not remove 月/日 from ordinary prose.
        var result = pattern
        val monthDay = if (language.startsWith("en")) "MMMM d" else "d MMMM"
        result = Regex("yyyy年M{1,2}月d{1,2}日").replace(result, "$monthDay yyyy")
        result = Regex("M{1,2}月d{1,2}日").replace(result, monthDay)
        result = Regex("yyyy年M{1,2}月").replace(result, "MMMM yyyy")
        result = result.replace("yyyy年", "yyyy")
        result = Regex("M{1,2}月").replace(result, "MMMM")
        result = Regex("d{1,2}日").replace(result, "d")
        result = Regex("H{1,2}[点时]m{1,2}分").replace(result, "HH:mm")
        result = Regex("H{1,2}[点时]").replace(result, "HH:00")
        return result.takeIf { it != pattern }
    }

    /**
     * Resolves compound patterns like "1000 步", "250 千卡", "剩余 80%"
     */
    private fun resolveCompound(text: String, language: String): String? {
        val isTr = language.startsWith("tr")

        // Weekday compound: 周一..周日, 星期一..星期日
        val weekdayMatch = Regex("""^(?:周|星期|礼拜)([一二三四五六日天])$""").matchEntire(text)
        if (weekdayMatch != null) {
            val d = weekdayMatch.groupValues[1]
            return if (isTr) when (d) {
                "一" -> "Pazartesi"
                "二" -> "Salı"
                "三" -> "Çarşamba"
                "四" -> "Perşembe"
                "五" -> "Cuma"
                "六" -> "Cumartesi"
                else -> "Pazar"
            } else when (d) {
                "一" -> "Monday"
                "二" -> "Tuesday"
                "三" -> "Wednesday"
                "四" -> "Thursday"
                "五" -> "Friday"
                "六" -> "Saturday"
                else -> "Sunday"
            }
        }

        // Steps: 1234 步, 1234步, 步数: 1234
        val stepMatch = Regex("""^(\d+)\s*步$""").matchEntire(text)
        if (stepMatch != null) {
            return if (isTr) "${stepMatch.groupValues[1]} Adım" else "${stepMatch.groupValues[1]} Steps"
        }

        // Calories: 500 千卡, 500卡路里
        val calMatch = Regex("""^(\d+)\s*(?:千卡|卡路里)$""").matchEntire(text)
        if (calMatch != null) {
            return "${calMatch.groupValues[1]} kcal"
        }

        // Distance: 5.2 公里, 5.2千米, 500米
        val kmMatch = Regex("""^([\d.]+)\s*(?:公里|千米)$""").matchEntire(text)
        if (kmMatch != null) {
            return "${kmMatch.groupValues[1]} km"
        }
        val mMatch = Regex("""^(\d+)\s*米$""").matchEntire(text)
        if (mMatch != null) {
            return "${mMatch.groupValues[1]} m"
        }

        // Heart rate: 75 次/分
        val hrMatch = Regex("""^(\d+)\s*次/(?:分|分钟)$""").matchEntire(text)
        if (hrMatch != null) {
            return "${hrMatch.groupValues[1]} bpm"
        }

        // Battery: 剩余 80%, 电量 80%
        val battMatch = Regex("""^(?:剩余|当前)?\s*电量\s*[:：]?\s*(\d+)\s*%$""").matchEntire(text)
        if (battMatch != null) {
            return if (isTr) "%${battMatch.groupValues[1]} Pil" else "${battMatch.groupValues[1]}% Battery"
        }
        val remBattMatch = Regex("""^剩余\s*[:：]?\s*(\d+)\s*%$""").matchEntire(text)
        if (remBattMatch != null) {
            return if (isTr) "Kalan %${remBattMatch.groupValues[1]}" else "Remaining ${remBattMatch.groupValues[1]}%"
        }
        val chgBattMatch = Regex("""^已充电\s*[:：]?\s*(\d+)\s*%$""").matchEntire(text)
        if (chgBattMatch != null) {
            return if (isTr) "Şarj Edildi %${chgBattMatch.groupValues[1]}" else "Charged ${chgBattMatch.groupValues[1]}%"
        }

        // Temperature: 25℃, 25度
        val tempMatch = Regex("""^([+-]?\d+)\s*(?:℃|度)$""").matchEntire(text)
        if (tempMatch != null) {
            return "${tempMatch.groupValues[1]}°C"
        }

        return null
    }

    /**
     * Post-processes machine-translated strings to fix awkward, literal translation artifacts.
     */
    fun postProcessTranslation(translatedText: String, targetLanguage: String = "tr"): String {
        // Broad substitutions such as “gölge -> bulutlu” corrupt legitimate settings labels.
        // Domain corrections belong to the source-language glossary, not arbitrary translated prose.
        return translatedText.trim()
    }

    private fun resolveTurkish(text: String): String? =
        RasterThemeGlossary.resolve(text) ?: TURKISH_THEME_UI[text] ?: TURKISH_DICTIONARY[text]
    private fun resolveEnglish(text: String): String? = ENGLISH_DICTIONARY[text]

    private val TURKISH_THEME_UI = mapOf(
        "璟" to "Parıltı",
        "遇见hyper27" to "Hyper27 ile Tanış",
        "免费主题，请勿倒卖！侵权后果自负！" to
            "Bu tema ücretsizdir; lütfen ücret karşılığı satmayın. Telif ihlalinden doğacak sonuçlar kullanıcıya aittir.",
        """◉深、浅色图标跟随系统自动切换（部分app无效）。
◉免费主题，请勿倒卖！侵权后果自负！

本主题混搭、修改详情：
全局、设置：基于OS2/OS3默认设置。
锁屏：《iP26超级3D景深》。
图标：深浅色图标代码来自《亭记》主题。有需要适配深浅色图标的酷安帖子留言。
桌面：默认。
桌面时钟：《AP16超级米果》。
短信：基于《AP景深宠物岛》主题修改。
拨号：基于《AP景深宠物岛》主题修改。
状态栏信号：单卡正常信号来自酷安@阿巴阿巴ovo 。双卡信号来自@tpsxx 米客app双卡模块（来自@MonetCarlos《起源V10》）。
状态栏电池：来自酷安@MonetCarlos《起源架构》 iOS16电池，圆角电池来自酷安@模块盗贼。
状态栏WIFI：酷安@阿巴阿巴ovo
其他：参考《O14》《一时间》《幻想艺术家》等主题。

更新日志：
2025.12.10更新：
1、适配HyperOS3系统。
2、其他深浅色图标更新。

2025.1.19更新：
1、增加控制中心高级材质背景通透度。（来自老王@Mari0us教程代码）
2、控制中心部分磁贴图标重新修改。
3、统一焦点通知背景（手电筒背景需要反编译系统界面组件）。（来自老王@Mari0us教程代码）

2024.12.26更新：
1、使用《亭记》主题代码，深色图标跟随系统深色模式自动切换。

2023.11.21更新详情：

1、适配hyperOS1.0，除控制中心，其他已全部适配完成，删除以前全局按钮代码（hyper已经好看很多了）。
2、更换为《AP景深宠物岛》锁屏，因为动画顺畅很多，很多iOS17特性小组件也很喜欢。""".trimIndent() to
            """◉ Koyu ve açık simgeler sistem temasına göre otomatik değişir (bazı uygulamalarda çalışmayabilir).
◉ Bu tema ücretsizdir; lütfen ücret karşılığı satmayın. Telif ihlalinden doğacak sonuçlar kullanıcıya aittir.

Bu temada kullanılan ve düzenlenen bileşenler:
Genel görünüm ve Ayarlar: OS2/OS3 varsayılanı temel alınmıştır.
Kilit ekranı: “iP26 Süper 3D Derinlik”.
Simgeler: Açık/koyu simge kodu “亭记” temasından alınmıştır. Uyumlanmasını istediğiniz simgeleri Coolapk gönderisine yorum olarak yazabilirsiniz.
Ana ekran: Varsayılan.
Ana ekran saati: “AP16 Süper Miguo”.
Mesajlar ve Arama: “AP Derinlik Efektli Evcil Hayvan Adası” temasından uyarlanmıştır.
Durum çubuğu sinyali: Tek SIM tasarımı Coolapk'te @阿巴阿巴ovo'dan; çift SIM tasarımı @tpsxx'in Mico uygulaması modülünden (@MonetCarlos'un “Origin V10” temasını temel alır).
Durum çubuğu pili: @MonetCarlos'un “Origin Architecture” temasındaki iOS 16 pilinden; yuvarlak köşeli pil @模块盗贼'den alınmıştır.
Durum çubuğu Wi-Fi: Coolapk @阿巴阿巴ovo.
Diğer: “O14”, “一时间”, “幻想艺术家” ve başka temalardan yararlanılmıştır.

Değişiklik günlüğü:
10.12.2025:
1. HyperOS 3 desteği eklendi.
2. Diğer açık/koyu simgeler güncellendi.

19.01.2025:
1. Kontrol merkezinin gelişmiş malzeme arka planı için saydamlık ayarı eklendi (@Mari0us eğitimindeki kod temel alınmıştır).
2. Kontrol merkezindeki bazı kutucuk simgeleri yeniden düzenlendi.
3. Odak bildirimlerinin arka planı birleştirildi. El feneri arka planı için Sistem Arayüzü bileşeninin tersine mühendisliği gerekir (@Mari0us eğitimindeki kod temel alınmıştır).

26.12.2024:
1. “亭记” tema kodu kullanılarak koyu simgelerin sistemin koyu moduyla otomatik değişmesi sağlandı.

21.11.2023:
1. HyperOS 1.0 desteği eklendi. Kontrol merkezi dışındaki bölümler uyarlandı; artık gerek kalmayan eski genel düğme kodları kaldırıldı.
2. Daha akıcı animasyonları ve sevilen iOS 17 tarzı widget'ları nedeniyle kilit ekranı “AP Derinlik Efektli Evcil Hayvan Adası” ile değiştirildi.""".trimIndent(),
        "主题特色：多功能锁屏，锁屏液态玻璃效果，可以调整玻璃颜色，景深壁纸，空间壁纸，专辑壁纸，mini播放器和大封面播放器，可以隐藏指纹，高级材质效果。" to
            "Tema özellikleri: Çok işlevli kilit ekranı, sıvı cam efekti ve ayarlanabilir cam rengi; derinlik efektli, uzamsal ve albüm duvar kâğıtları; mini oynatıcı ve büyük kapaklı oynatıcı; parmak izi simgesini gizleme ve gelişmiş malzeme efektleri.",
        "设置索引：1.插入壁纸 2.壁纸相关 3.解锁偏好 4.超级开屏 5.百变刘海与灵动岛 6.日期时间 7.小组件 8.音乐 9.通知 10.底部卡片 11.控制中心 12.StandBy待机 13.双击黑屏 14.专注模式 15.小白条 16.快捷方式 17.超级胶囊 18.指纹 19.人脸识别 20.状态栏 21.更多 22.桌面时钟" to
            "Ayarlar: 1. Duvar kâğıdı 2. Duvar kâğıdı seçenekleri 3. Kilit açma 4. Ekran açılışı 5. Çentik ve Dinamik Ada 6. Tarih ve saat 7. Widget'lar 8. Müzik 9. Bildirimler 10. Alt kartlar 11. Kontrol merkezi 12. StandBy 13. Çift dokunarak ekranı kapatma 14. Odak modu 15. Hareket çubuğu 16. Kısayollar 17. Süper Kapsül 18. Parmak izi 19. Yüz tanıma 20. Durum çubuğu 21. Diğer 22. Ana ekran saati",
        "1.插入壁纸（注意：壁纸太大会导致锁屏黑屏或卡顿）" to
            "1. Duvar kâğıdı ekle (çok büyük görseller kilit ekranında kararmaya veya takılmaya neden olabilir)",
        "2.壁纸相关设置" to "2. Duvar kâğıdı seçenekleri",
        "3.解锁偏好" to "3. Kilit açma tercihleri",
        "4.超级开屏" to "4. Ekran açılış efekti",
        "5.百变刘海与灵动岛" to "5. Çentik ve Dinamik Ada",
        "6.日期时间设置" to "6. Tarih ve saat ayarları",
        "7.小组件设置" to "7. Widget ayarları",
        "插入壁纸（前景）" to "Ön plan duvar kâğıdı ekle",
        "插入壁纸（背景）" to "Arka plan duvar kâğıdı ekle",
        "公众号：suger苏哥主题" to "Resmî hesap: Suger Temaları",
        "壁纸重力感应" to "Harekete duyarlı duvar kâğıdı",
        "开启重力感应后壁纸会适当放大，并且无法调节景深系数" to
            "Açıldığında duvar kâğıdı hafifçe büyür; derinlik seviyesi ayrıca ayarlanamaz.",
        "3D增强" to "3D efekti güçlendir",
        "重力方向相反" to "Ön ve arka planı ters yönde hareket ettir",
        "合并前景到模糊层" to "Ön planı bulanık katmanla birleştir",
        "重力壁纸放大动画" to "Hareketli duvar kâğıdı büyütme animasyonu",
        "景深壁纸放大动画" to "Derinlik duvar kâğıdı büyütme animasyonu",
        "壁纸由大缩小动画" to "Duvar kâğıdını küçülterek aç",
        "亮屏壁纸模糊变高清" to "Ekran açılırken bulanıklıktan netliğe geç",
        "双击屏幕两侧切换壁纸" to "Kenarlarına çift dokunarak duvar kâğıdını değiştir",
        "文件夹壁纸自动切换" to "Klasördeki duvar kâğıtlarını otomatik değiştir",
        "不使用自动切换" to "Otomatik değiştirme kapalı",
        "每分钟自动切换" to "Her dakika değiştir",
        "每小时自动切换" to "Her saat değiştir",
        "每天自动切换" to "Her gün değiştir",
        "轻微压暗壁纸" to "Duvar kâğıdını hafifçe karart",
        "轻微压暗数值" to "Hafif karartma seviyesi",
        "自动调暗壁纸" to "Duvar kâğıdını otomatik karart",
        "不自动调暗壁纸" to "Otomatik karartma kapalı",
        "深色模式下调暗壁纸" to "Koyu modda karart",
        "日落后调暗壁纸" to "Gün batımından sonra karart",
        "自动调暗壁纸数值" to "Otomatik karartma seviyesi",
        "滑动解锁方案" to "Kaydırarak kilit açma yöntemi",
        "全屏上滑较短距离解锁（快捷）" to "Ekranda kısa mesafe yukarı kaydır (hızlı)",
        "全屏上滑较长距离解锁（防误触）" to "Ekranda uzun mesafe yukarı kaydır (yanlış dokunmayı önler)",
        "从屏幕最底部上滑解锁（防误触）" to "Ekranın altından yukarı kaydır (yanlış dokunmayı önler)",
        "上滑时壁纸变化" to "Yukarı kaydırırken duvar kâğıdı efekti",
        "壁纸无变化" to "Değişiklik yok",
        "壁纸整体模糊" to "Duvar kâğıdının tamamını bulanıklaştır",
        "壁纸从底部往上模糊" to "Aşağıdan yukarı doğru bulanıklaştır",
        "壁纸放大+模糊" to "Büyüt ve bulanıklaştır",
        "上滑时UI变化" to "Yukarı kaydırırken arayüz efekti",
        "仅移动" to "Yalnızca hareket ettir",
        "移动+变透明" to "Hareket ettir ve saydamlaştır",
        "上滑实时炫彩玻璃效果" to "Kaydırırken canlı cam efekti",
        "开灯效果" to "Ekran açılış efekti",
        "无效果" to "Efekt yok",
        "全屏渐亮" to "Tüm ekranı yavaşça aydınlat",
        "从中间渐亮" to "Ortadan başlayarak aydınlat",
        "从电源键渐亮" to "Güç düğmesinden başlayarak aydınlat",
        "圆形从中间放大" to "Ortadan büyüyen daire",
        "圆形从电源键放大" to "Güç düğmesinden büyüyen daire",
        "拉开门" to "Kapı açılma efekti",
        "推开门" to "Kapıyı itme efekti",
        "百变刘海" to "Özelleştirilebilir çentik",
        "百变刘海开关" to "Özelleştirilebilir çentiği kullan",
        "百变刘海样式" to "Çentik görünümü",
        "灵动岛" to "Dinamik Ada",
        "齐刘海" to "Geniş çentik",
        "灵动大额头" to "Geniş Dinamik Ada",
        "灵动岛：常态样式" to "Dinamik Ada: bekleme görünümü",
        "无功能时的样式" to "Etkin bir özellik yokken gösterilecek görünüm",
        "灵动岛：充电" to "Dinamik Ada: şarj",
        "灵动岛：通知" to "Dinamik Ada: bildirimler",
        "灵动岛：手电筒" to "Dinamik Ada: el feneri",
        "灵动岛：音乐" to "Dinamik Ada: müzik",
        "锁屏灵动岛充电功能开关" to "Şarj bilgisini kilit ekranındaki Dinamik Ada'da gösterir.",
        "锁屏灵动岛通知弹窗功能开关" to "Bildirimleri kilit ekranındaki Dinamik Ada'da gösterir.",
        "点亮手电筒时锁屏灵动岛显示动画效果" to "El feneri açıldığında Dinamik Ada'da bir animasyon gösterir.",
        "锁屏灵动岛可交互音乐功能开关" to "Dinamik Ada'daki etkileşimli müzik denetimlerini açar.",
        "灵动岛音乐频谱根据封面染色；注意：可能会影响锁屏流畅度，用小米音乐时可能无法染色" to
            "Müzik görselleştiricisinin rengini albüm kapağına uyarlar. Kilit ekranı akıcılığını etkileyebilir ve Xiaomi Müzik'te çalışmayabilir.",
        "锁屏灵动岛内是否显示歌名（歌词）；需要打开灵动岛音乐开关" to
            "Dinamik Ada'da şarkı adını veya sözleri gösterir. Dinamik Ada müzik seçeneği açık olmalıdır.",
        "灵动岛显示歌名（歌词）" to "Dinamik Ada'da şarkı adını veya sözleri göster",
        "灵动岛长度" to "Dinamik Ada genişliği",
        "灵动岛高度" to "Dinamik Ada yüksekliği",
        "灵动岛距离屏幕顶部距离" to "Dinamik Ada'nın üst kenara uzaklığı",
        "灵动岛x轴偏移" to "Dinamik Ada yatay konumu",
        "输入范围：290-470，默认400. (tips:澎湃系统超级岛音乐岛参考长度:296)" to
            "Aralık: 290–470; varsayılan: 400. HyperOS Süper Ada müzik görünümü için önerilen değer: 296.",
        "输入范围：90-110，默认100. (tips:澎湃系统超级岛参考高度:94)" to
            "Aralık: 90–110; varsayılan: 100. HyperOS Süper Ada için önerilen değer: 94.",
        "修复点击一次切换两首歌的bug,仅澎湃OS3需要开启,非澎湃OS3禁止开启!" to
            "Tek dokunuşta iki şarkı atlama sorununu düzeltir. Yalnızca HyperOS 3'te açın; diğer sürümlerde kapalı tutun.",
        "音乐频谱染色" to "Müzik görselleştiricisini kapak rengine uyarla",
        "澎湃系统" to "HyperOS",
        "超级岛" to "Süper Ada",
        "超级胶囊" to "Süper Kapsül",
        "吐舌" to "Dil çıkarma",
        "emoji小黄脸【吐舌】" to "Emoji sarı yüz (dil çıkarma)",
        "高级设置" to "Gelişmiş ayarlar",
        "右上角设置提示" to "Sağ üst ayar ipucu",
        "锁屏元素动画" to "Kilit ekranı öğe animasyonları",
        "最高:%s° 最低:%s°" to "En yüksek: %s°  En düşük: %s°",
        "分钟可充满" to " dakikada tamamen şarj olur",
        "分充满" to " dk içinde dolar",
        "保持心情愉悦" to "Keyfini yüksek tut",
        "永远相信美好的事情即将发生" to "Güzel şeylerin yakında olacağına hep inan",
        "自定义" to "Özelleştirme",
        "默认壁纸" to "Varsayılan duvar kâğıdı", "自定义壁纸" to "Özel duvar kâğıdı",
        "桌面壁纸" to "Ana ekran duvar kâğıdı", "文件夹壁纸" to "Klasördeki duvar kâğıdı", "内置壁纸" to "Hazır duvar kâğıdı",
        "正在使用默认壁纸" to "Varsayılan duvar kâğıdı kullanılıyor",
        "正在使用自定义壁纸" to "Özel duvar kâğıdı kullanılıyor",
        "正在使用桌面壁纸" to "Ana ekran duvar kâğıdı kullanılıyor",
        "正在使用文件夹壁纸" to "Klasördeki duvar kâğıdı kullanılıyor",
        "正在使用内置壁纸" to "Hazır duvar kâğıdı kullanılıyor",
        "时间设置" to "Saat ayarları", "日期设置" to "Tarih ayarları",
        "小组件设置" to "Widget ayarları", "更多设置" to "Diğer ayarlar",
        "最低" to "En düşük", "最高" to "En yüksek",
        "快速充电" to "Hızlı şarj ediliyor",
        "0~0.3之间，默认0.22" to "0–0,3 arasında; varsayılan: 0,22",
        "不刷新" to "Yenileme kapalı", "个性" to "Kişisel", "个通知" to " bildirim",
        "主要" to "Birincil", "次要" to "İkincil",
        "主题内置18张精美壁纸供您选择" to "Temadaki 18 hazır duvar kâğıdından birini seçebilirsiniz.",
        "五种样式供选择" to "Beş görünüm seçeneği", "四种样式供选择" to "Dört görünüm seçeneği",
        "值越大离底部越远,默认20" to "Değer arttıkça alt kenardan uzaklaşır. Varsayılan: 20.",
        "值越大移动位置越多，不要超过12，默认8,负值为反向" to "Değer arttıkça hareket mesafesi artar. En fazla 12, varsayılan 8. Negatif değer ters yönde hareket ettirir.",
        "值越大越深，默认50，最大255" to "Değer arttıkça koyulaşır. Varsayılan: 50, en fazla: 255.",
        "充滿" to "Tam şarj oldu", "全部隐藏" to "Tümünü gizle",
        "内容" to "İçerik", "内置" to "Hazır", "分鐘" to "dakika", "分钟" to "dakika",
        "分钟相对于小时的透明度，值越大差别越大" to "Dakikanın saate göre saydamlığı. Değer arttıkça aradaki fark artar.",
        "分钟透明度" to "Dakika saydamlığı", "剩余" to "Kalan",
        "卡一" to "SIM 1", "卡二" to "SIM 2", "双击此处" to "Buraya çift dokunun", "可用" to "Kullanılabilir",
        "商务联系" to "İş birliği için iletişim", "嚴重污染" to "Ciddi hava kirliliği",
        "图片尺寸宽400高156,尺寸太大会卡顿" to "Görsel 400 × 156 piksel olmalı. Daha büyük görseller takılmaya neden olabilir.",
        "图片格式为png格式" to "PNG biçiminde bir görsel kullanın.",
        "图片质量最好别超过1m,超过会卡顿" to "Görselin 1 MB'ı aşmaması önerilir; büyük dosyalar takılmaya neden olabilir.",
        "壁纸缩放倍数" to "Duvar kâğıdı ölçeği", "复制到浏览器打开" to "Kopyalayıp tarayıcıda açın",
        "天" to "gün", "天氣" to "Hava durumu", "小时" to "saat", "小時" to "saat",
        "小时刷新+双击刷新" to "Her saat veya çift dokununca yenile",
        "小米主题" to "Xiaomi Temalar", "小组件颜色跟随" to "Widget renk eşleştirmesi",
        "小部件自定义图1" to "Widget özel görseli 1", "小部件自定义图2" to "Widget özel görseli 2",
        "尺寸" to "Boyut", "已关闭" to "Kapalı", "已打开" to "Açık", "平移" to "Kaydırma",
        "开启后主题自带桌面壁纸模糊" to "Temanın ana ekran duvar kâğıdını bulanıklaştırır.",
        "开启后在自定义图颜色将跟随时间颜色,适用于透明图" to "Özel görselin rengi saat rengiyle eşleşir. Saydam görseller için uygundur.",
        "开启后在锁屏界面下滑，主屏元素会跟随下滑" to "Kilit ekranında aşağı kaydırırken ana ekran öğeleri de hareket eder.",
        "开启后在锁屏界面不会自动锁屏，需手动锁屏，仅测试主题使用" to "Otomatik ekran kilidini kapatır; ekranı elle kilitlemeniz gerekir. Yalnızca tema testi içindir.",
        "开启后将自动裁切图片圆角" to "Görselin köşelerini otomatik yuvarlatır.",
        "开启后补全底部缺失的壁纸" to "Duvar kâğıdının altta eksik kalan bölümünü tamamlar.",
        "开启后部分界面显示高光" to "Bazı arayüz öğelerinde parlama efekti gösterir.",
        "开启后锁屏按钮点击震动" to "Kilit ekranı düğmelerine dokunulduğunda titreşim verir.",
        "指纹识别" to "Parmak izi tanıma", "控制中心右上角文字" to "Kontrol merkezi sağ üst metni",
        "文件夹" to "Klasör", "无" to "Yok", "无天气信息" to "Hava durumu bilgisi yok",
        "无套餐" to "Tarife bilgisi yok", "无数据" to "Veri yok", "日一二三四五六" to "Pz Pt Sa Ça Pe Cu Ct",
        "日曆" to "Takvim", "时间整体" to "Saatin tamamı", "時間" to "Saat",
        "景深壁纸上层" to "Derinlik duvar kâğıdı üst katmanı", "景深壁纸网址" to "Derinlik duvar kâğıdı bağlantısı",
        "暂无播放" to "Bir şey çalmıyor", "更多部件" to "Diğer widget'lar", "最多输入4个字" to "En fazla 4 karakter girin.",
        "本月剩余" to "Bu ay kalan", "本月只剩 今天" to "Ayın son günü", "条通知" to " bildirim",
        "桌面壁纸模糊" to "Ana ekran duvar kâğıdını bulanıklaştır",
        "横向" to "Yatay", "横向位置" to "Yatay konum", "歌手" to "Sanatçı",
        "正在使用" to "Kullanımda", "正在使用内置壁纸_" to "Kullanılan hazır duvar kâğıdı: ",
        "正在獲取電量數據" to "Pil bilgisi alınıyor", "正在获取电量数据" to "Pil bilgisi alınıyor",
        "正常显示" to "Normal göster", "正常显示(默认)" to "Normal göster (varsayılan)",
        "每天刷新+双击刷新" to "Her gün veya çift dokununca yenile", "每月刷新+双击刷新" to "Her ay veya çift dokununca yenile",
        "沉浸" to "Tam ekran", "流量卡选择" to "Mobil veri SIM kartı", "测试与反馈" to "Test ve geri bildirim",
        "濕度" to "Nem", "点击获取数据" to "Bilgileri almak için dokunun", "無天氣訊息" to "Hava durumu bilgisi yok",
        "空气良好" to "Hava kalitesi iyi", "空氣優" to "Hava kalitesi çok iyi", "空氣良好" to "Hava kalitesi iyi",
        "站立40次" to "40 kez ayağa kalk", "系统" to "Sistem", "纵向" to "Dikey", "纵向位置" to "Dikey konum",
        "缩放" to "Ölçek", "背景颜色" to "Arka plan rengi",
        "自定义wifi名称" to "Özel Wi-Fi adı", "自定义信号名称" to "Özel operatör adı",
        "自定义图染色" to "Özel görseli renklendir", "自定义底部文字" to "Özel alt metin",
        "自定义文字" to "Özel metin", "自定义日期文字" to "Özel tarih metni", "自定义日期格式" to "Özel tarih biçimi",
        "裁切自定义图" to "Özel görseli kırp", "請稍等" to "Lütfen bekleyin", "请稍等" to "Lütfen bekleyin",
        "计数器刷新" to "Sayaç yenileme", "计数器单位" to "Sayaç birimi", "计数器标题" to "Sayaç başlığı", "计数器目标" to "Sayaç hedefi",
        "设备电量" to "Cihaz pili", "请加群咨询" to "Yardım için gruba katılın", "请勿输入过长" to "Kısa bir metin girin.",
        "跟随底部按钮图标颜色" to "Alt düğme simgesiyle aynı renk", "跟随底部按钮背景颜色" to "Alt düğme arka planıyla aynı renk",
        "跟随时间颜色" to "Saatle aynı renk", "跟随通知文字颜色" to "Bildirim metniyle aynı renk", "跟随通知背景颜色" to "Bildirim arka planıyla aynı renk",
        "輕度污染" to "Hafif hava kirliliği", "酷安搜索" to "Coolapk'te ara", "重力倍数" to "Hareket hassasiyeti",
        "重力壁纸补全" to "Harekete duyarlı duvar kâğıdını tamamla", "锁屏主屏下滑跟随" to "Kilit ekranıyla birlikte aşağı kaydır",
        "锁屏主屏幕" to "Kilit ekranı ana görünümü", "锁屏壁纸下层" to "Kilit ekranı duvar kâğıdı alt katmanı",
        "锁屏小组件" to "Kilit ekranı widget'ları", "锁屏常亮" to "Kilit ekranını açık tut", "锁屏按钮震动" to "Kilit ekranı düğmelerinde titreşim",
        "锁屏控制中心" to "Kilit ekranı kontrol merkezi", "锁屏时间" to "Kilit ekranı saati",
        "锁屏通知距底部" to "Bildirimlerin alt kenara uzaklığı", "锁屏音乐无封面自定义图" to "Kapak yokken gösterilecek özel görsel",
        "锁屏音乐遮罩透明度" to "Müzik kaplaması saydamlığı", "锁屏高光" to "Kilit ekranı parlama efekti",
        "隐藏动画" to "Animasyonu gizle", "隐藏指纹" to "Parmak izini gizle", "隐藏指纹图标" to "Parmak izi simgesini gizle",
        "隐藏指纹图标与识别动画" to "Parmak izi simgesini ve tanıma animasyonunu gizle", "隐藏识别动画" to "Tanıma animasyonunu gizle",
        "電量" to "Pil", "需要" to "Gereken", "預計" to "Tahmini", "预计" to "Tahmini", "高度" to "Yükseklik",
        "點下區域\n切換動畫\n(共8種)" to "Animasyonu değiştirmek için\naşağıya dokunun\n(8 seçenek)", "點擊獲取數據" to "Bilgileri almak için dokunun",
    ) + (1..31).associate { number ->
        val digits = listOf("", "一", "二", "三", "四", "五", "六", "七", "八", "九")
        val chinese = if (number < 10) digits[number] else (if (number < 20) "" else digits[number / 10]) + "十" + digits[number % 10]
        chinese to number.toString()
    }

    private val CHINESE_PIVOT_TERMS = linkedMapOf(
        "息屏空间" to "always-on display area",
        "萌宠动画" to "animated pet",
        "运营商标" to "carrier logo",
        "商标符号" to "carrier logo",
        "雨量图" to "precipitation chart",
        "悬浮球" to "floating button",
        "唱片机" to "turntable player",
        "报时" to "spoken time",
        "横屏" to "landscape mode",
        "光栅" to "lenticular",
        "小米" to "Xiaomi",
        "高斯模糊" to "Gaussian blur",
        "显示方式" to "display mode",
        "位置调整" to "position adjustment",
        "大小调整" to "size adjustment",
        "默认开启" to "enabled by default",
        "默认关闭" to "disabled by default",
        "开关" to "toggle",
        "指纹" to "fingerprint",
        "百变刘海" to "customizable notch",
        "景深壁纸" to "depth-effect wallpaper",
        "重力壁纸" to "motion-responsive wallpaper",
        "壁纸重力感应" to "wallpaper motion sensing",
        "澎湃系统" to "HyperOS",
        "超级岛" to "Super Island",
        "灵动岛" to "Dynamic Island",
        "超级胶囊" to "Super Capsule",
        "小白条" to "gesture bar",
        "控制中心" to "Control Center",
        "状态栏" to "status bar",
        "锁屏" to "lock screen",
        "小组件" to "widget",
        "刘海" to "notch",
        "胶囊" to "capsule",
        "景深" to "depth effect",
        "壁纸" to "wallpaper",
    )

    private val TURKISH_DICTIONARY: Map<String, String> = mapOf(
        "正在快充" to "Hızlı şarj ediliyor",
        "正在超级快充" to "Süper hızlı şarj ediliyor",
        "正在极速快充" to "Ultra hızlı şarj ediliyor",
        "农历" to "Çin takvimi",
        "优" to "Çok iyi", "良" to "İyi", "轻度" to "Hafif kirlilik",
        "中度" to "Orta kirlilik", "重度" to "Yüksek kirlilik", "严重" to "Ciddi kirlilik",
        "优秀" to "Çok iyi",
        "壁纸压暗" to "Duvar kâğıdını karart",
        "壁纸模糊" to "Duvar kâğıdını bulanıklaştır",
        "小白条" to "Hareket çubuğu",
        "小白条颜色" to "Hareket çubuğu rengi",
        "右上角小白条" to "Sağ üst hareket çubuğu",
        "右上角控制中心" to "Sağ üst kontrol merkezi",
        "全屏音乐时关闭指纹" to "Müzik tam ekrandayken parmak izini gizle",
        "全屏音乐样式" to "Tam ekran müzik görünümü",
        "双击自定义图" to "Özel görseli çift dokunarak değiştir",
        "内容选择" to "İçerik seçimi",
        "个性壁纸" to "Özel duvar kâğıdı",
        "人脸识别" to "Yüz tanıma",
        "充电动画" to "Şarj animasyonu",
        "充电样式" to "Şarj görünümü",
        "内置壁纸" to "Hazır duvar kâğıtları",
        "小组件" to "Widget'lar",
        "小组件显示位置" to "Widget konumu",
        "底部按钮" to "Alt düğmeler",
        "开启后展开锁屏岛时隐藏指纹功能" to "Kilit ekranı adası açıldığında parmak izi simgesini gizler.",
        "开启后时间与日期同行显示，无法调节字体与颜色" to "Saat ve tarih aynı satırda gösterilir. Yazı tipi ve renk değiştirilemez.",
        "开启后桌面时间与日期同步调节样式" to "Ana ekrandaki saat ve tarih görünümü birlikte değişir.",
        "开启后点击小白条直接打开自定义" to "Hareket çubuğuna dokunulduğunda özelleştirme açılır.",
        "开启后调节时间样式，日期样式同步修改" to "Saat görünümü değiştiğinde tarih görünümü de değişir.",
        "开屏收纳为锁屏岛" to "Ekran açıldığında kilit ekranı adasına küçült",
        "快捷打开自定义" to "Özelleştirmeyi hızlı aç",
        "指纹样式" to "Parmak izi simgesi",
        "按钮背景透明度" to "Düğme arka planı saydamlığı",
        "按钮颜色" to "Düğme rengi",
        "日期样式跟随时间样式" to "Tarih görünümünü saatle eşleştir",
        "日期透明度" to "Tarih saydamlığı",
        "时间与日期同行显示" to "Saat ve tarihi aynı satırda göster",
        "时间联动" to "Saat ve tarihi birlikte değiştir",
        "时间透明度" to "Saat saydamlığı",
        "景深效果" to "Derinlik efekti",
        "查看更多" to "Daha fazla göster",
        "深色遮罩" to "Koyu kaplama",
        "点击白条打开自定义" to "Hareket çubuğuyla özelleştirmeyi aç",
        "空" to "Boş",
        "自动堆叠通知" to "Bildirimleri otomatik grupla",
        "话费" to "Hat bakiyesi",
        "超级时间样式" to "Gelişmiş saat görünümü",
        "跟随音乐收纳为锁屏岛" to "Müzikle birlikte kilit ekranı adasına küçült",
        "通知文字颜色" to "Bildirim metni rengi",
        "通知背景透明度" to "Bildirim arka planı saydamlığı",
        "通知背景颜色" to "Bildirim arka planı rengi",
        "重力壁纸" to "Harekete duyarlı duvar kâğıdı",
        "锁屏AI指纹" to "Kilit ekranı akıllı parmak izi",
        "锁屏人脸识别动画" to "Yüz tanıma animasyonu",
        "锁屏使用可调节高度时间字体样式" to "Kilit ekranı saatinin yüksekliğini ayarla",
        "锁屏底部小白条" to "Kilit ekranı hareket çubuğu",
        "锁屏底部按钮" to "Kilit ekranı alt düğmeleri",
        "锁屏通知" to "Kilit ekranı bildirimleri",
        "锁屏音乐" to "Kilit ekranı müzik çaları",
        "锁屏音乐切歌优化" to "Kilit ekranında parça geçişini iyileştir",
        "日出" to "Gün doğumu", "日落" to "Gün batımı",
        "帧率" to "Kare hızı", "秒" to "saniye",
        "最高:" to "En yüksek: ", "最低:" to "En düşük: ",
        "无闹钟" to "Alarm yok",

        // Weather
        "晴" to "Güneşli",
        "晴天" to "Güneşli",
        "多云" to "Parçalı Bulutlu",
        "少云" to "Az Bulutlu",
        "阴" to "Bulutlu",
        "阴天" to "Kapalı",
        "阵雨" to "Sağanak Yağış",
        "雷阵雨" to "Gök Gürültülü Sağanak",
        "雷雨" to "Gök Gürültülü Yağmur",
        "小雨" to "Hafif Yağmur",
        "中雨" to "Orta Şiddette Yağmur",
        "大雨" to "Kuvvetli Yağmur",
        "暴雨" to "Şiddetli Yağmur",
        "大暴雨" to "Çok Şiddetli Yağmur",
        "特大暴雨" to "Aşırı Şiddetli Yağmur",
        "冻雨" to "Dondurucu Yağmur",
        "雨夹雪" to "Karla Karışık Yağmur",
        "小雪" to "Hafif Kar",
        "中雪" to "Orta Şiddette Kar",
        "大雪" to "Yoğun Kar",
        "暴雪" to "Kar Fırtınası",
        "阵雪" to "Kısa Süreli Kar",
        "雾" to "Sisli",
        "浓雾" to "Yoğun Sis",
        "强浓雾" to "Yoğun Sis",
        "轻雾" to "Hafif Sis",
        "霾" to "Puslu",
        "中度霾" to "Orta Puslu",
        "重度霾" to "Yoğun Puslu",
        "浮尘" to "Tozlu",
        "扬沙" to "Kum Fırtınası",
        "沙尘暴" to "Kum Fırtınası",
        "强沙尘暴" to "Şiddetli Kum Fırtınası",
        "大风" to "Rüzgarlı",
        "微风" to "Hafif Esinti",
        "台风" to "Tayfun",
        "龙卷风" to "Hortum",
        "冰雹" to "Dolu",
        "空气优" to "Hava Kalitesi Mükemmel",
        "空气良" to "Hava Kalitesi İyi",
        "轻度污染" to "Hafif Kirli",
        "中度污染" to "Orta Kirli",
        "重度污染" to "Ağır Kirli",
        "严重污染" to "Aşırı Kirli",
        "湿度" to "Nem",
        "风向" to "Rüzgar Yönü",
        "风力" to "Rüzgar Hızı",
        "紫外线" to "UV İndeksi",
        "气压" to "Basınç",
        "能见度" to "Görüş Mesafesi",
        "体感温度" to "Hissedilen",
        "天气" to "Hava Durumu",

        // Battery & Power
        "充电中" to "Şarj Ediliyor",
        "正在充电" to "Şarj ediliyor",
        "未充电" to "Şarj Olmuyor",
        "未在充电" to "Şarj Olmuyor",
        "已充满" to "Tam şarj oldu",
        "充满" to "Tam Şarj Oldu",
        "电量" to "Pil",
        "剩余电量" to "Kalan Pil",
        "当前电量" to "Mevcut Şarj",
        "电池" to "Pil",
        "电池电量" to "Pil Seviyesi",
        "快充" to "Hızlı Şarj",
        "快速充电" to "Hızlı Şarj",
        "超级快充" to "Süper Hızlı Şarj",
        "超快充" to "Süper Hızlı Şarj",
        "超级闪充" to "Süper Hızlı Şarj",
        "闪充" to "Hızlı Şarj",
        "极速快充" to "Ultra Hızlı Şarj",
        "无线充电" to "Kablosuz Şarj",
        "无线快充" to "Kablosuz Hızlı Şarj",
        "低电量" to "Düşük Pil",
        "电量过低" to "Düşük Pil",
        "请充电" to "Lütfen Şarj Edin",
        "省电模式" to "Güç Tasarrufu",
        "超级省电" to "Süper Güç Tasarrufu",

        // Calendar, Days & Time
        "星期一" to "Pazartesi",
        "星期二" to "Salı",
        "星期三" to "Çarşamba",
        "星期四" to "Perşembe",
        "星期五" to "Cuma",
        "星期六" to "Cumartesi",
        "星期日" to "Pazar",
        "星期天" to "Pazar",
        "周一" to "Pazartesi",
        "周二" to "Salı",
        "周三" to "Çarşamba",
        "周四" to "Perşembe",
        "周五" to "Cuma",
        "周六" to "Cumartesi",
        "周日" to "Pazar",
        "周天" to "Pazar",
        "礼拜一" to "Pazartesi",
        "礼拜二" to "Salı",
        "礼拜三" to "Çarşamba",
        "礼拜四" to "Perşembe",
        "礼拜五" to "Cuma",
        "礼拜六" to "Cumartesi",
        "礼拜日" to "Pazar",
        "礼拜天" to "Pazar",
        "今天" to "Bugün",
        "明天" to "Yarın",
        "后天" to "Ertesi Gün",
        "昨天" to "Dün",
        "前天" to "Önceki Gün",
        "上午" to "ÖÖ",
        "下午" to "ÖS",
        "凌晨" to "Gece",
        "早上" to "Sabah",
        "清晨" to "Sabah",
        "中午" to "Öğle",
        "傍晚" to "Akşamüstü",
        "晚上" to "Akşam",
        "半夜" to "Gece Yarısı",
        "农历" to "Çin takvimi",
        "年" to "Yıl",
        "月" to "Ay",
        "日" to "Gün",
        "时" to "Saat",
        "点" to "Saat",
        "分" to "Dakika",
        "秒" to "Saniye",

        // Health & Fitness
        "步" to "Adım",
        "步数" to "Adım Sayısı",
        "今日步数" to "Bugünkü Adım",
        "目标步数" to "Hedef Adım",
        "步数目标" to "Adım Hedefi",
        "卡路里" to "Kalori",
        "千卡" to "kcal",
        "距离" to "Mesafe",
        "公里" to "km",
        "千米" to "km",
        "米" to "m",
        "心率" to "Nabız",
        "次/分" to "bpm",
        "次/分钟" to "atım/dk",
        "站立" to "Ayakta Kalma",
        "运动" to "Egzersiz",
        "活动" to "Hareket",
        "睡眠" to "Uyku",
        "深睡" to "Derin Uyku",
        "浅睡" to "Hafif Uyku",

        // Lockscreen Gestures & Navigation
        "双击锁屏" to "Ekranı kilitlemek için çift dokunun",
        "双击熄屏" to "Ekranı kapatmak için çift dokunun",
        "双击唤醒" to "Ekranı uyandırmak için çift dokunun",
        "双击切换" to "Değiştirmek için çift dokunun",
        "双击更换" to "Değiştirmek için çift dokunun",
        "双击" to "Çift Dokun",
        "单击" to "Dokun",
        "点击" to "Dokun",
        "长按" to "Basılı Tut",
        "滑动解锁" to "Kilidi açmak için kaydırın",
        "上滑解锁" to "Kilidi açmak için yukarı kaydırın",
        "下滑解锁" to "Kilidi açmak için aşağı kaydırın",
        "右滑解锁" to "Kilidi açmak için sağa kaydırın",
        "左滑解锁" to "Kilidi açmak için sola kaydırın",
        "上滑" to "Yukarı Kaydır",
        "下滑" to "Aşağı Kaydır",
        "左滑" to "Sola Kaydır",
        "右滑" to "Sağa Kaydır",
        "左滑进入" to "Sola kaydırın",
        "右滑进入" to "Sağa kaydırın",
        "左滑负一屏" to "Asistan için sola kaydırın",
        "右滑相机" to "Kamera için sağa kaydırın",
        "上滑相机" to "Kamera için yukarı kaydırın",
        "长按自定义" to "Özelleştirmek için basılı tutun",
        "长按编辑" to "Düzenlemek için basılı tutun",
        "长按进入自定义" to "Özelleştirmek için basılı tutun",
        "长按进入设置" to "Ayarları açmak için basılı tutun",
        "点击进入" to "Açmak için dokunun",
        "点击进入设置" to "Ayarları açmak için dokunun",
        "点击进入自定义" to "Özelleştirmek için dokunun",
        "点击更换" to "Değiştirmek için dokunun",
        "点击切换" to "Geçiş yapmak için dokunun",
        "点击刷新" to "Yenilemek için dokunun",

        // Lockscreen & System Settings
        "锁屏设置" to "Kilit Ekranı Ayarları",
        "锁屏自定义" to "Kilit Ekranı Özelleştirme",
        "锁屏样式" to "Kilit Ekranı Stili",
        "锁屏" to "Kilit Ekranı",
        "桌面设置" to "Ana Ekran Ayarları",
        "桌面" to "Ana Ekran",
        "壁纸设置" to "Duvar Kâğıdı Ayarları",
        "壁纸" to "Duvar Kâğıdı",
        "时钟样式" to "Saat Stili",
        "时钟" to "Saat",
        "时间样式" to "Saat Biçimi",
        "时间" to "Zaman",
        "日期样式" to "Tarih Stili",
        "日期" to "Tarih",
        "字体样式" to "Yazı Tipi Stili",
        "字体" to "Yazı Tipi",
        "颜色设置" to "Renk Ayarları",
        "快捷方式" to "Kısayollar",
        "小部件" to "Araçlar",
        "负一屏" to "Akıllı Asistan",
        "控制中心" to "Kontrol Merkezi",
        "通知中心" to "Bildirim Paneli",
        "通知" to "Bildirim",
        "状态栏" to "Durum Çubuğu",
        "暂无通知" to "Bildirim yok",
        "无通知" to "Bildirim yok",
        "暂无日程" to "Yaklaşan etkinlik yok",
        "无日程" to "Etkinlik yok",
        "日程" to "Etkinlikler",
        "未读消息" to "Okunmamış mesaj",
        "紧急呼叫" to "Acil Arama",
        "无SIM卡" to "SIM kart yok",
        "请插入SIM卡" to "Lütfen SIM kart takın",
        "未连接" to "Bağlı değil",
        "已连接" to "Bağlandı",
        "正在播放" to "Çalıyor",
        "播放" to "Çal",
        "暂停" to "Duraklatıldı",
        "上一首" to "Önceki Parça",
        "下一首" to "Sonraki Parça",
        "音量" to "Ses Düzeyi",
        "亮度" to "Parlaklık",
        "勿扰模式" to "Rahatsız Etmeyin",
        "勿扰" to "Rahatsız Etmeyin",
        "静音模式" to "Sessiz Mod",
        "静音" to "Sessiz",
        "振动模式" to "Titreşim Modu",
        "振动" to "Titreşim",
        "飞行模式" to "Uçak Modu",
        "蓝牙" to "Bluetooth",
        "开启" to "Açık",
        "打开" to "Açık",
        "关闭" to "Kapalı",
        "显示" to "Göster",
        "隐藏" to "Gizle",
        "默认" to "Varsayılan",
        "自定义" to "Özel",
        "样式" to "Stil",
        "颜色" to "Renk",
        "大小" to "Boyut",
        "位置" to "Konum",
        "透明度" to "Opaklık",
        "模糊" to "Bulanıklık",
        "高斯模糊" to "Bulanıklık",
        "居中" to "Ortala",
        "居左" to "Sola Hizala",
        "居右" to "Sağa Hizala",
        "顶部" to "Üst",
        "底部" to "Alt",
        "黑色" to "Siyah",
        "白色" to "Beyaz",
        "红色" to "Kırmızı",
        "蓝色" to "Mavi",
        "绿色" to "Yeşil",
        "黄色" to "Sarı",
        "紫色" to "Mor",
        "橙色" to "Turuncu",
        "浅色" to "Açık",
        "深色" to "Koyu",
        "跟随系统" to "Sistemi Takip Et",
        "重置" to "Sıfırla",
        "保存" to "Kaydet",
        "取消" to "İptal",
        "确认" to "Onayla",
        "完成" to "Bitti",
        "返回" to "Geri",
        "更多" to "Daha Fazla",
        "是" to "Evet",
        "否" to "Hayır",

        // Apps & System
        "相机" to "Kamera",
        "手电筒" to "El Feneri",
        "计算器" to "Hesap Makinesi",
        "相册" to "Galeri",
        "音乐" to "Müzik",
        "视频" to "Video",
        "日历" to "Takvim",
        "闹钟" to "Alarm",
        "设置" to "Ayarlar",
        "浏览器" to "Tarayıcı",
        "电话" to "Telefon",
        "短信" to "Mesajlar",
        "通讯录" to "Kişiler",
        "联系人" to "Kişiler",
        "录音机" to "Ses Kaydedici",
        "便签" to "Notlar",
        "笔记" to "Notlar",
        "文件管理" to "Dosya Yöneticisi",
        "主题壁纸" to "Temalar",
        "应用商店" to "Uygulama Mağazası",
        "主题" to "Tema",
        "作者" to "Yazar",
        "设计" to "Tasarım",
        "设计师" to "Tasarımcı",
        "版本" to "Sürüm",
    )

    private val ENGLISH_DICTIONARY: Map<String, String> = mapOf(
        "晴" to "Sunny",
        "晴天" to "Sunny",
        "多云" to "Partly Cloudy",
        "少云" to "Mostly Sunny",
        "阴" to "Overcast",
        "阴天" to "Overcast",
        "阵雨" to "Showers",
        "雷阵雨" to "Thunderstorms",
        "雷雨" to "Thunderstorms",
        "小雨" to "Light Rain",
        "中雨" to "Moderate Rain",
        "大雨" to "Heavy Rain",
        "暴雨" to "Downpour",
        "雨夹雪" to "Sleet",
        "小雪" to "Light Snow",
        "大雪" to "Heavy Snow",
        "雾" to "Foggy",
        "霾" to "Hazy",
        "充电中" to "Charging",
        "正在充电" to "Charging",
        "未充电" to "Not Charging",
        "已充满" to "Fully charged",
        "充满" to "Fully Charged",
        "电量" to "Battery",
        "剩余电量" to "Remaining Battery",
        "快充" to "Fast Charging",
        "超级快充" to "Super Fast Charging",
        "星期一" to "Monday",
        "星期二" to "Tuesday",
        "星期三" to "Wednesday",
        "星期四" to "Thursday",
        "星期五" to "Friday",
        "星期六" to "Saturday",
        "星期日" to "Sunday",
        "周一" to "Monday",
        "周二" to "Tuesday",
        "周三" to "Wednesday",
        "周四" to "Thursday",
        "周五" to "Friday",
        "周六" to "Saturday",
        "周日" to "Sunday",
        "今天" to "Today",
        "明天" to "Tomorrow",
        "昨天" to "Yesterday",
        "上午" to "AM",
        "下午" to "PM",
        "步" to "Steps",
        "步数" to "Step Count",
        "滑动解锁" to "Swipe to unlock",
        "上滑解锁" to "Swipe up to unlock",
        "双击锁屏" to "Double tap to lock",
        "长按自定义" to "Long press to customize",
        "锁屏设置" to "Lock Screen Settings",
        "时钟样式" to "Clock Style",
        "相机" to "Camera",
        "手电筒" to "Flashlight",
        "设置" to "Settings",
        "是" to "Yes",
        "否" to "No",
    )
}
