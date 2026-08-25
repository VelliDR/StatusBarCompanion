# Durum Çubuğu Yoldaşı (StatusBar Companion)

Bu uygulama, Android durum çubuğunuzun (status bar) üzerinde veya etrafında tamamen kişiselleştirilebilir GIF'ler ve resimler göstermenizi sağlayan bir **Erişilebilirlik Servisi (Accessibility Service)** uygulamasıdır.

Kendi tasarımlarınızı, karakterlerinizi ve animasyonlarınızı durum çubuğunuza ekleyerek telefonunuzu özelleştirebilirsiniz.

## Özellikler

- **Tamamen Çevrimdışı ve Güvenli:** Uygulama, `android.permission.INTERNET` izni içermez. Hiçbir veriniz dışarı aktarılmaz, uygulama dış dünya ile tamamen yalıtılmıştır.
- **Olay Güdümlü (Event-Driven) Pil Tasarrufu:** Arka planda sürekli çalışan döngüler (while, timer vb.) yoktur. Yalnızca ekranın döndürülmesi veya şarj yüzdesinin değişmesi gibi sistem olaylarında tetiklenerek minimum pil tüketimi sağlar.
- **Karakter Stüdyosu:** 
  - Çok katmanlı GIF ve resim ekleme desteği.
  - Sürükle ve Bırak (Drag & Drop) özelliği ile eklediğiniz öğeleri ekranda istediğiniz yere kolayca yerleştirebilirsiniz.
  - Canlı Önizleme: Canlı duvar kağıdınızın (veya ekran görüntünüzün) üzerine öğeleri ekleyerek gerçek zamanlı olarak nasıl görüneceğini test edebilirsiniz.
- **Duruma Göre Animasyonlar:**
  - **Boşta (Idle):** Normal kullanım sırasında gösterilecek animasyon.
  - **Şarj Oluyor (Charging):** Cihaz şarja takıldığında gösterilecek animasyon.
  - **Düşük Pil (Low Battery):** Şarj seviyesi kritik düzeye indiğinde gösterilecek animasyon.

## Nasıl Kullanılır?

1. Uygulamayı açın ve **Karakter Stüdyosu**'na (Character Studio) girin.
2. Yeni bir tema oluşturun ve katmanlara (layers) istediğiniz GIF veya resimleri (Idle, Charging, Low Battery durumları için) ekleyin.
3. Ekran görüntüsü (Screenshot) alarak veya canlı önizleme üzerinde, öğelerinizi parmağınızla sürükleyerek istediğiniz konuma (X, Y koordinatları) yerleştirin.
4. Boyutlandırma (Scale) ayarlarını kaydırıcı (slider) ile yapın.
5. Değişiklikleri kaydedin ve ana ekranda servisi başlatmak için **Erişilebilirlik İzni** verin.

## Teknik Detaylar

- **Dil:** Kotlin
- **Arayüz:** XML tabanlı standart Android görünümleri
- **Medya Yükleme:** Coil (GIF ve SVG destekli)
- **Mimari:** Olay güdümlü Erişilebilirlik Servisi (AccessibilityService)

## Gereksinimler

- Android 9 (API 28) veya üzeri bir cihaz.
- Görüntülerin diğer uygulamaların üzerinde gösterilebilmesi için "Erişilebilirlik" (Accessibility) izni.

## Yasal Uyarı (Disclaimer)

Bu uygulama ("StatusBar Companion") tamamen olduğu gibi ("as is") sağlanmaktadır. Geliştirici, uygulamanın kullanımı, yanlış kullanımı, cihaz performansına etkileri, diğer uygulamalarla olası çakışmaları veya donanımsal/yazılımsal hatalar nedeniyle doğabilecek doğrudan, dolaylı veya tesadüfi hiçbir zarardan sorumlu tutulamaz. Uygulamayı kurarak ve kullanarak tüm sorumluluğu kendi üzerinize aldığınızı kabul etmiş olursunuz.

## Lisans

Bu proje **GNU Genel Kamu Lisansı sürüm 3 (GPL-3.0)** altında lisanslanmıştır. 
Herkes kodu incelemekte, değiştirmekte ve dağıtmakta özgürdür; ancak değiştirilmiş veya türetilmiş çalışmaların da aynı açık kaynak lisansı (GPL-3.0) ile dağıtılması zorunludur. Daha fazla bilgi için [LICENSE](LICENSE) dosyasına veya [GNU lisans sayfasına](https://www.gnu.org/licenses/gpl-3.0.html) bakabilirsiniz.
