package dev.glorioustr.mtzstudio.core

/** Curated Turkish copy for the reusable UI phrases found in the “光栅” theme family. */
internal object RasterThemeGlossary {
    fun resolve(text: String): String? = TURKISH[text]

    private val TURKISH = mapOf(
        "光栅" to "Lentiküler",
        """【锁屏】
光栅壁纸（晃动手机切换三张壁纸，实现光栅效果，壁纸支持自定义）；
如果锁屏卡顿，请在锁屏右上角更多设置中关闭局部高斯模糊，自定义壁纸请勿超过手机分辨率；
动态星光充电动画，支持自定义充电动画；
居中动态唱片机音乐播放器，锁屏右上角开关随时显示；
锁屏显示日出日落、雨量图等多种小组件（双击可切换）；
锁屏显示指南针，跟随指纹图标区域显示，充分利用空间；
锁屏全局显示的底部动态音乐频谱；
锁屏全局显示的双侧边悬浮球，支持20+快捷功能，任意边缘点击或内滑均可呼出，跟手操作，方便快捷；
仿默认右下角滑动相机；
底部支持音乐播放器、计步、快捷开关（点击上方切换显示）；
双击底部小横条切换到横屏模式，再次双击同一位置退出。横屏模式支持报时，可以侧边菜单开启，横屏界面自动常亮。

【控制中心与状态栏】
超椭圆圆润矩形开关；
纯黑状态栏图标；
状态栏左侧时间大小（稍大）；
状态栏电池内数字大小（稍大）；
状态栏显示星期；
通知中心与控制中心左上角日期大小（稍大）。

【时钟】
锁屏和桌面时钟采用大粗体轨迹效果设计；
中间显示小秒钟；
桌面时钟有音乐播放时显示歌词；
桌面时钟下方节日提醒；
桌面时钟点击中部显示设置菜单，支持颜色切换、时钟字体切换、秒钟开关。

【反馈与交流】
QQ交流群号：204879473
微信公众号：墨飞主题
作者微信号：Mofy_123""" to
            """[Kilit ekranı]
Lentiküler duvar kâğıdı: Telefonu hareket ettirerek üç duvar kâğıdı arasında geçiş yapar ve lentiküler bir etki oluşturur. Duvar kâğıtları özelleştirilebilir.
Kilit ekranı takılıyorsa sağ üst köşedeki Diğer ayarlardan bölgesel Gauss bulanıklığını kapatın. Özel duvar kâğıtları telefonun ekran çözünürlüğünü aşmamalıdır.
Dinamik yıldız ışığı şarj animasyonu ve özel şarj animasyonları desteklenir.
Ortalanmış, hareketli pikap görünümlü müzik çalar sağ üst köşedeki anahtarla her zaman gösterilebilir.
Gün doğumu/gün batımı, yağış grafiği ve başka widget'lar gösterilir; aralarında geçiş yapmak için çift dokunun.
Pusula, alanı verimli kullanmak için parmak izi simgesinin çevresinde gösterilir.
Alt bölümde ekran genelinde hareketli müzik spektrumu bulunur.
Her iki kenardaki yüzen düğmeler 20'den fazla kısayolu destekler. Herhangi bir kenara dokunarak veya içeri kaydırarak menüyü açabilirsiniz.
Sağ alttan kaydırılan, varsayılan görünüme benzer kamera kısayolu bulunur.
Alt bölümde müzik çalar, adım sayacı ve hızlı anahtarlar bulunur; üstlerine dokunarak görünümü değiştirebilirsiniz.
Yatay moda geçmek için alttaki hareket çubuğuna çift dokunun; çıkmak için aynı yere yeniden çift dokunun. Yatay modda sesli saat özelliği yan menüden açılabilir ve ekran sürekli açık kalır.

[Kontrol merkezi ve durum çubuğu]
Süper elips biçiminde, yuvarlatılmış dikdörtgen anahtarlar.
Tam siyah durum çubuğu simgeleri.
Durum çubuğunun solundaki saat biraz daha büyük gösterilir.
Pil simgesinin içindeki sayı biraz daha büyük gösterilir.
Durum çubuğunda haftanın günü gösterilir.
Bildirim paneli ile Kontrol merkezinin sol üstündeki tarih biraz daha büyük gösterilir.

[Saat]
Kilit ekranı ve ana ekran saatlerinde büyük, kalın ve iz efektli bir tasarım kullanılır.
Ortada küçük bir saniye göstergesi bulunur.
Müzik çalarken ana ekran saatinde şarkı sözleri gösterilir.
Ana ekran saatinin altında özel gün hatırlatmaları bulunur.
Ana ekran saatinin ortasına dokunarak renk, yazı tipi ve saniye göstergesi seçeneklerini içeren ayar menüsünü açabilirsiniz.

[Geri bildirim ve iletişim]
QQ grubu: 204879473
WeChat resmî hesabı: 墨飞主题
Yazarın WeChat hesabı: Mofy_123""",
        "设置索引：【1】锁屏壁纸丨【2】锁屏通知丨【3】锁屏时钟丨【4】锁屏控件丨【5】侧边悬浮菜单丨【6】屏幕指纹丨【7】虚拟指纹丨【8】充电动画丨【9】音乐播放器丨【10】息屏空间丨【11】萌宠动画丨【12】运营商标" to
            "Ayarlar: [1] Kilit ekranı duvar kâğıdı | [2] Bildirimler | [3] Saat | [4] Denetimler | [5] Yüzen kenar menüsü | [6] Ekran içi parmak izi | [7] Sanal parmak izi | [8] Şarj animasyonu | [9] Müzik çalar | [10] Her zaman açık ekran | [11] Animasyonlu evcil hayvan | [12] Operatör logosu",
        "注意：自定义完成后请在后台退出该界面，以免造成锁屏空白或异常" to
            "Not: Özelleştirmeyi bitirdikten sonra bu ekranı son uygulamalardan kapatın; aksi hâlde kilit ekranı boş kalabilir veya düzgün çalışmayabilir.",
        "【温馨提示】过年（除夕）时锁屏会飘落祝福和红包口令，双击可关闭" to
            "İpucu: Çin Yeni Yılı arifesinde kilit ekranında kutlama mesajları ve kırmızı zarf parolası gösterilir. Kapatmak için çift dokunun.",

        "【1】锁屏壁纸设置：" to "[1] Kilit ekranı duvar kâğıdı ayarları:",
        "【2】锁屏通知设置：" to "[2] Kilit ekranı bildirim ayarları:",
        "【3】锁屏时钟设置：" to "[3] Kilit ekranı saat ayarları:",
        "【4】锁屏控件设置：" to "[4] Kilit ekranı denetimleri:",
        "【5】锁屏侧边悬浮菜单功能设置" to "[5] Yüzen kenar menüsü:",
        "【6】屏幕指纹设置" to "[6] Ekran içi parmak izi ayarları:",
        "【7】虚拟指纹设置（非屏幕指纹手机用）" to "[7] Sanal parmak izi (ekran içi sensörü olmayan telefonlar için):",
        "【8】锁屏充电动画设置：" to "[8] Kilit ekranı şarj animasyonu:",
        "【9】锁屏音乐播放器：" to "[9] Kilit ekranı müzik çaları:",
        "【10】息屏空间：" to "[10] Her zaman açık ekran alanı:",
        "【11】萌宠动画设置" to "[11] Animasyonlu evcil hayvan:",
        "【12】自定义锁屏运营商：" to "[12] Özel kilit ekranı operatör logosu:",

        "壁纸显示方式" to "Duvar kâğıdı kaynağı",
        "系统壁纸" to "Sistem duvar kâğıdı",
        "内置壁纸" to "Hazır duvar kâğıdı",
        "自定义壁纸（在下方选择）" to "Özel duvar kâğıdı (aşağıdan seçin)",
        "自定义壁纸-左" to "Özel duvar kâğıdı – sol",
        "自定义壁纸-中" to "Özel duvar kâğıdı – orta",
        "自定义壁纸-右" to "Özel duvar kâğıdı – sağ",
        "左右晃动角度调节" to "Sağa-sola hareket hassasiyeti",
        "上下晃动角度调节" to "Yukarı-aşağı hareket hassasiyeti",
        "默认：100，越大越灵敏" to "Varsayılan: 100. Değer yükseldikçe hassasiyet artar.",
        "默认：0，上下禁止变化，需要上下晃动请填入建议60的数值，越大越灵敏。注意：上下晃动后拿起来手机或手机站立时不显示主壁纸。" to
            "Varsayılan: 0 (dikey hareket kapalı). Dikey hareket için önerilen değer 60'tır; değer yükseldikçe hassasiyet artar. Not: Dikey hareket açıkken telefon dik tutulduğunda ana duvar kâğıdı görünmeyebilir.",
        "左右光栅壁纸显示开关" to "Lentiküler duvar kâğıdı geçişi",
        "左右光栅效果显示开关" to "Lentiküler çizgi efekti",
        "局部高斯模糊显示开关" to "Bölgesel Gauss bulanıklığı",
        "锁屏卡片效果显示方式" to "Kilit ekranı kart görünümü",
        "通透模式" to "Saydam",
        "浅色模糊" to "Açık renk bulanıklık",
        "白色模式" to "Beyaz",
        "自定义景深壁纸-中" to "Özel derinlik efektli duvar kâğıdı – orta",
        "景深壁纸会覆盖到时钟，请先将主壁纸抠图后使用（相册打开主壁纸，长按，保存，即可抠图，使用抠图png格式即可实现景深效果）。使用景深壁纸请关闭左右光栅壁纸。" to
            "Derinlik efektli duvar kâğıdı saatin üzerine gelebilir. Önce ana duvar kâğıdının arka planını kaldırıp PNG olarak kaydedin; ardından bu görseli seçin. Derinlik efektini kullanırken lentiküler duvar kâğıdı geçişini kapatın.",
        "景深壁纸显示开关" to "Derinlik efektli duvar kâğıdını göster",
        "全屏动态天气显示方式" to "Tam ekran hareketli hava durumu",
        "根据天气变化显示（仅雨雪天气）" to "Hava durumuna göre göster (yalnızca yağmur ve karda)",
        "一直显示（下雨效果）" to "Her zaman göster (yağmur efekti)",
        "一直显示（下雪效果）" to "Her zaman göster (kar efekti)",
        "闪电显示方式" to "Şimşek efekti",
        "所有下雨天气都显示闪电效果" to "Tüm yağmurlu havalarda göster",
        "仅雷阵雨天气才显示闪电效果" to "Yalnızca gök gürültülü sağanakta göster",

        "向下偏移请填正数，向上偏移请填负数" to "Aşağı taşımak için pozitif, yukarı taşımak için negatif bir değer girin.",
        "加大间隔请填正数，减小间隔请填负数" to "Aralığı artırmak için pozitif, azaltmak için negatif bir değer girin.",
        "默认为210，数字越大，卡片越高" to "Varsayılan: 210. Değer yükseldikçe kartın yüksekliği artar.",
        "默认：0，如果字体大小调大后，建议将行间距也加大，否则会重叠" to "Varsayılan: 0. Yazı boyutunu artırırsanız metnin üst üste binmemesi için satır aralığını da artırın.",
        "锁屏通知上下位置调整" to "Kilit ekranı bildirimlerinin dikey konumu",
        "锁屏通知卡片大小调整" to "Bildirim kartı yüksekliği",
        "锁屏通知卡片间隔调整" to "Bildirim kartları arasındaki boşluk",
        "锁屏通知标题大小" to "Bildirim başlığı boyutu",
        "锁屏通知内容大小" to "Bildirim metni boyutu",
        "锁屏通知行间距" to "Bildirim satır aralığı",
        "第5条通知显示开关" to "Beşinci bildirimi göster",
        "底部一键清除通知显示开关" to "Alttaki “Tüm bildirimleri temizle” yazısını göster",

        "时钟位置调整" to "Saatin dikey konumu",
        "锁屏时钟样式" to "Kilit ekranı saat görünümü",
        "时钟1 轨迹时钟 纯色" to "Saat 1 – iz efektli, tek renk",
        "时钟2 轨迹时钟 混色" to "Saat 2 – iz efektli, çok renkli",
        "时钟3 圆润时钟 纯色" to "Saat 3 – yuvarlak, tek renk",
        "时钟4 圆润时钟 混色" to "Saat 4 – yuvarlak, çok renkli",
        "时钟5 点阵时钟" to "Saat 5 – nokta matris",
        "时钟6 系统字体秒钟" to "Saat 6 – sistem yazı tipi ve saniye",
        "时钟7 系统字体秒钟" to "Saat 7 – sistem yazı tipi ve saniye",
        "时钟普通模式颜色" to "Saatin normal mod rengi",
        "时钟反色模式颜色" to "Saatin ters renk modu",
        "锁屏时钟1中间秒钟显示开关" to "Saat 1'de ortadaki saniyeyi göster",
        "锁屏时钟下方自定义签名开关" to "Saatin altında özel metin göster",
        "自定义签名" to "Özel metin",
        "今日事今日毕" to "Bugünün işini bugün bitir",
        "锁屏时钟下方节日提醒开关" to "Saatin altında özel gün hatırlatmalarını göster",
        "节日提醒丨签名文字颜色选择" to "Özel gün / imza metni rengi",
        "节日提醒丨签名文字大小" to "Özel gün / imza metni boyutu",

        "右上角设置图标显示开\\关" to "Sağ üstteki ayarlar simgesini göster",
        "右上角设置打开方式" to "Sağ üst ayarları açma yöntemi",
        "单击（快速点击操作）" to "Tek dokunma (hızlı)",
        "双击（有效防误触）" to "Çift dokunma (yanlış dokunmayı önler)",
        "锁屏中上小部件" to "Üst-orta kilit ekranı widget'ı",
        "锁屏中下小部件" to "Alt-orta kilit ekranı widget'ı",
        "日出日落" to "Gün doğumu ve gün batımı",
        "五日天气" to "Beş günlük hava durumu",
        "数据进度条" to "Veri ilerleme çubuğu",
        "年月日进度环" to "Yıl / ay / gün ilerleme halkası",
        "流量信息" to "Mobil veri bilgisi",
        "雨量图" to "Yağış grafiği",
        "不显示" to "Gizle",

        "音乐已暂停" to "Müzik duraklatıldı",
        "暂无歌曲信息" to "Parça bilgisi yok",
        "暂无音乐播放" to "Müzik çalmıyor",
        "点击开始播放" to "Çalmak için dokunun",
        "欢迎使用唱片播放机" to "Pikap görünümlü oynatıcı",
        "清除通知" to "Bildirimleri temizle",
        "双击切换" to "Değiştirmek için çift dokunun",
        "空气质量" to "Hava kalitesi",
        "最高温" to "En yüksek",
        "最低温" to "En düşük",
        "今日使用" to "Bugünkü kullanım",
        "超出流量" to "Aşılan veri",
        "剩余流量" to "Kalan veri",
        "流量套餐" to "Veri paketi",
        "话费余额" to "Hat bakiyesi",
        "元" to "¥",
        "大" to "Yüksek",
        "中" to "Orta",
        "小" to "Düşük",
        "现在" to "Şimdi",
        "1小时后" to "1 saat sonra",
        "2小时后" to "2 saat sonra",
        "计步" to "Adım",
        "空白" to "Boş",
        "双击全息屏" to "Tam ekran için çift dokunun",
        "今日步数：" to "Bugünkü adım: ",
        "音乐播放器" to "Müzik çalar",
        "双击关闭" to "Kapatmak için çift dokunun",
        "双击打开" to "Açmak için çift dokunun",
        "滑动打开" to "Açmak için kaydırın",
        "锁屏已常亮" to "Kilit ekranı sürekli açık",
        "指纹样式16 澎湃OS logo" to "Parmak izi stili 16 – HyperOS logosu",

        "光栅 - 开" to "Lentiküler: Açık",
        "光栅 - 关" to "Lentiküler: Kapalı",
        "反色 - 开" to "Ters renk: Açık",
        "反色 - 关" to "Ters renk: Kapalı",
        "音乐 - 开" to "Müzik: Açık",
        "音乐 - 关" to "Müzik: Kapalı",
        "- 时钟1 +" to "Saat 1",
        "- 时钟1+" to "Saat 1",
        "- 时钟2 +" to "Saat 2",
        "- 时钟2+" to "Saat 2",
        "- 时钟3 +" to "Saat 3",
        "- 时钟3+" to "Saat 3",
        "- 时钟4 +" to "Saat 4",
        "- 时钟4+" to "Saat 4",
        "- 时钟5 +" to "Saat 5",
        "- 时钟5+" to "Saat 5",
        "- 时钟6 +" to "Saat 6",
        "- 时钟6+" to "Saat 6",
        "- 时钟7 +" to "Saat 7",
        "- 时钟7+" to "Saat 7",
        "指南针-开" to "Pusula: Açık",
        "指南针-关" to "Pusula: Kapalı",
        "常亮 - 开" to "Ekran açık: Açık",
        "常亮 - 关" to "Ekran açık: Kapalı",
        "更多设置" to "Diğer ayarlar",
        "- 唱片机 +" to "Pikap",
        "- 唱片机+" to "Pikap",
        "- 轨迹钟 +" to "İz efektli saat",
        "- 轨迹钟+" to "İz efektli saat",
        "秒钟-关" to "Saniye: Kapalı",
        "秒钟-开" to "Saniye: Açık",
        "旋转-关" to "Döndürme: Kapalı",
        "旋转-开" to "Döndürme: Açık",
        "按钮" to "Düğmeler",
        "专辑" to "Albüm",
        "日期天气" to "Tarih ve hava",
        "报时-关" to "Sesli saat: Kapalı",
        "报时-分钟" to "Sesli saat: Dakika",
        "报时-半点" to "Sesli saat: Buçuk",
        "报时-整点" to "Sesli saat: Tam saat",
        "双击此处退出全息屏" to "Tam ekrandan çıkmak için buraya çift dokunun",

        "开" to "Açık",
        "关" to "Kapalı",
        "反色" to "Ters renk",
        "常亮" to "Sürekli açık",
        "空气" to "Hava kalitesi",
        "东北" to "Kuzeydoğu",
        "东" to "Doğu",
        "东南" to "Güneydoğu",
        "南" to "Güney",
        "西南" to "Güneybatı",
        "西" to "Batı",
        "西北" to "Kuzeybatı",
        "北" to "Kuzey",
        "红包" to "Kırmızı zarf",
        "平安吉祥" to "Huzur ve mutluluk",

        "小米音乐" to "Xiaomi Müzik",
        "网易云音乐" to "NetEase Cloud Music",
        "酷狗音乐" to "Kugou Music",
        "酷我音乐" to "Kuwo Music",
        "哔哩哔哩" to "Bilibili",
        "指南针" to "Pusula",
        "应用" to "Uygulamalar",
        "支付" to "Ödeme",
        "工具" to "Araçlar",
    )
}
