<div align="center">
  <h1>✨ StatusBar Companion ✨</h1>
  <p><strong>Durum çubuğunuza hayat katın! Animasyonlar, karakterler ve GIF'lerle telefonunuzu özelleştirin.</strong></p>

  <!-- Badges -->
  <p>
    <a href="https://github.com/VelliDR/StatusBarCompanion/releases/tag/v2.0"><img src="https://img.shields.io/github/v/release/VelliDR/StatusBarCompanion?label=S%C3%BCr%C3%BCm&color=brightgreen&style=for-the-badge" alt="Release Version"></a>
    <img src="https://img.shields.io/badge/Android-14+-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android Min SDK 34">
    <img src="https://img.shields.io/badge/Lisans-GPL_3.0-blue?style=for-the-badge" alt="License GPL 3.0">
    <img src="https://img.shields.io/badge/Ba%C4%9Flant%C4%B1-100%25_%C3%87evrimd%C4%B1%C5%9F%C4%B1-red?style=for-the-badge" alt="Offline & Secure">
    <img src="https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin">
  </p>
</div>

---

## 🎯 Proje Hakkında
Bu uygulama, Android durum çubuğunuzun (status bar) üzerinde veya etrafında tamamen kişiselleştirilebilir **GIF'ler ve resimler** göstermenizi sağlayan güçlü bir **Erişilebilirlik Servisi (Accessibility Service)** uygulamasıdır.

Kendi tasarımlarınızı, karakterlerinizi ve animasyonlarınızı durum çubuğunuza ekleyerek telefonunuzu özelleştirebilir; şarja taktığınızda, şarjınız azaldığında veya boşta dururken farklı tepkiler verdirebilirsiniz! 👾

---

## 🚀 Öne Çıkan Özellikler

- 🛡️ **%100 Çevrimdışı ve Güvenli:** Uygulama, `android.permission.INTERNET` izni içermez. Hiçbir veriniz dışarı aktarılmaz, uygulama dış dünya ile tamamen yalıtılmış bir kum havuzunda (sandbox) çalışır.
- 🔋 **Pil Dostu (Event-Driven):** Arka planda sürekli çalışan döngüler (while, timer vb.) yoktur. Yalnızca ekranın döndürülmesi veya şarj yüzdesinin değişmesi gibi sistem olaylarında tetiklenerek **minimum pil tüketimi** sağlar.
- 🎨 **Çok Katmanlı Karakter Stüdyosu:** 
  - Çok katmanlı (layer) GIF ve resim ekleme desteği.
  - Sürükle ve Bırak (Drag & Drop) ile eklediğiniz öğeleri ekranda hassas şekilde konumlandırın.
  - **Canlı Önizleme:** Kendi duvar kağıdınız üzerinde karakterlerin gerçekte nasıl durduğunu anında test edin.
- 📤 **İçe ve Dışa Aktarma (Export/Import):** Yarattığınız temaları `zip` formatında dışarı aktarın, arkadaşlarınızla paylaşın veya yedekleyin!
- 🌙 **Akıllı Gece Modu:** Sisteminizin kendi karanlık temasına (Dark Mode) senkronize olarak çalışır, GPS veya zaman hesaplamasına ihtiyaç duymadan göz yormaz.
- 🎬 **Duruma Göre Animasyonlar:**
  - 🧍 **Boşta (Idle):** Normal kullanım sırasında gösterilecek animasyon.
  - ⚡ **Şarj Oluyor (Charging):** Cihaz şarja takıldığında gösterilecek animasyon.
  - 🪫 **Düşük Pil (Low Battery):** Şarj seviyesi kritik düzeye indiğinde gösterilecek animasyon.

---

## 🛠️ Nasıl Kullanılır?

1. Uygulamayı açın ve **Karakter Stüdyosu**'na (Character Studio) girin.
2. Yeni bir tema oluşturun ve katmanlara istediğiniz GIF veya resimleri (Idle, Charging, Low Battery durumları için) ekleyin.
3. Ekran görüntüsü (Screenshot) alarak veya canlı önizleme üzerinde, öğelerinizi parmağınızla sürükleyerek istediğiniz konuma (X, Y koordinatları) yerleştirin.
4. Boyutlandırma (Scale) ayarlarını kaydırıcı (slider) ile yapın.
5. Değişiklikleri kaydedin ve ana ekranda servisi başlatmak için **Erişilebilirlik İzni** verin.

---

## ⚙️ Teknik Detaylar

- **Dil:** Kotlin
- **Arayüz:** XML tabanlı standart Android görünümleri (Material Design)
- **Medya Yükleme:** Coil (GIF ve SVG destekli)
- **Mimari:** Olay güdümlü Erişilebilirlik Servisi (AccessibilityService) & Broadcast Receivers
- **Güvenlik:** Android 14+ tam uyumlu, şifrelenmiş dahili Intent yapısı (`RECEIVER_NOT_EXPORTED`).
- **Gereksinimler:** Android 14 (API 34) veya üzeri bir cihaz. Görüntülerin çizilebilmesi için "Erişilebilirlik" (Accessibility) izni.

---

## ⚖️ Yasal Uyarı (Disclaimer)

Bu uygulama ("StatusBar Companion") tamamen olduğu gibi ("as is") sağlanmaktadır. Geliştirici, uygulamanın kullanımı, yanlış kullanımı, cihaz performansına etkileri, diğer uygulamalarla olası çakışmaları veya donanımsal/yazılımsal hatalar nedeniyle doğabilecek doğrudan, dolaylı veya tesadüfi hiçbir zarardan sorumlu tutulamaz. Uygulamayı kurarak ve kullanarak tüm sorumluluğu kendi üzerinize aldığınızı kabul etmiş olursunuz.

---

## 📄 Lisans

Bu proje **GNU Genel Kamu Lisansı sürüm 3 (GPL-3.0)** altında lisanslanmıştır. 

Herkes kodu incelemekte, değiştirmekte ve dağıtmakta özgürdür; ancak değiştirilmiş veya türetilmiş çalışmaların da aynı açık kaynak lisansı (GPL-3.0) ile dağıtılması zorunludur. Daha fazla bilgi için [LICENSE](LICENSE) dosyasına veya [GNU lisans sayfasına](https://www.gnu.org/licenses/gpl-3.0.html) bakabilirsiniz.
