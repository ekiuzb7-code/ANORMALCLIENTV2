<div align="center">

# 👻 Anormal Client

**Minecraft 1.21.11 uchun zamonaviy va to'liq sozlanuvchi Fabric client mod**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-44A833?style=for-the-badge&logo=minecraft&logoColor=white)](https://minecraft.net)
[![Fabric](https://img.shields.io/badge/Fabric_Loader_0.18.1-924bcf?style=for-the-badge&logo=fabricmc&logoColor=white)](https://fabricmc.net)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net)
[![CI Build](https://img.shields.io/github/actions/workflow/status/YOUR_USERNAME/AnormalClient/build.yml?branch=main&style=for-the-badge&label=CI%20Build)](.github/workflows/build.yml)
[![Modullar](https://img.shields.io/badge/modullar-457%2B-8A2BE2?style=for-the-badge)](#-modullar)

<p align="center">
  <b>2 ta tema • 12 ta kategoriya • Spectre va Kuro portlari • GitHub Actions CI</b><br>
  <sub>O'zbek tilidagi GUI • ClickGUI • HUD Editor • Keybinds paneli</sub>
</p>

</div>

---

## ✨ Asosiy Xususiyatlari

| | Xususiyat | Tavsif |
|---|-----------|--------|
| 🎨 | **2 ta almashinadigan tema** | `Vape V4` (qora panel + neon aksentlar) va `Glassmorphism` (shisha effekti) |
| 👻 | **Spectre portlari (114 ta)** | Spectre (26.2) manbasidan port qilingan modullar: ESP, chunk finder'lar, Region Map, HUD |
| 🌑 | **Kuro portlari (112 ta)** | Kuro manbasidan port: aura, movement, inventory va builder modullari |
| ⌨️ | **Keybinds paneli** | Barcha keybind'lar bir panelda — korish, tozalash va "Unbind All" tugmasi bilan |
| 🧩 | **HUD Editor** | Ekrandagi elementlarni sichqoncha bilan ko'chirish va hajmini o'zgartirish |
| 🌐 | **O'zbekcha GUI** | Barcha menyu va sozlamalar o'zbek tilida |
| ⚙️ | **Client Settings** | Tema, GUI scale, ranglar va boshqa sozlamalar bir joydan |

> 💡 **Temani almashtirish**: ClickGUI → `Client` kategoriya → `Theme: Vape V4` yoki `Theme: Glassmorphism`.

---

## 🧭 Navigatsiya (ClickGUI)

- **Ochish**: `Options → Controls → Key Binds → Anormal Client → Open ClickGUI Menu` — sukut bo'yicha klavisha **Right Shift (`RSHIFT`)**.
- Har bir modulga ClickGUI ichidan istalgan klavishadan keybind bog'lash mumkin.
- Kategoriya panelining pastida **▼** tugmasi bor — u Spectre / Kuro port kategoriya ko'rsatadi yoki yashiradi.
- `Keybinds` tab'i — hangi modulga qaysi tugma bog'langanini ko'rish va tozalash uchun.

---

## 🚀 Modullar kategoriya bo'yicha (457+ ta)

| Kategoriya | Soni | Misollar |
|------------|------|----------|
| ⚔️ **Combat** | 30 | `KillAura`, `AutoCrystal`, `AimAssist`, `Triggerbot`, `WTap`, `Reach`, `SilentAura`, `Criticals` |
| ⚡ **Movement** | 17 | `Sprint`, `Flight`, `ElytraFly`, `NoFall`, `Spider`, `Step`, `InvMove`, `NoJumpDelay` |
| 👁️ **Render** | 67 | `ESP`, `ItemESP`, `BaseFinder`, `ChunkFinder`, `AncientDebrisFinder`, `Fullbright`, `NameTags`, `Tracers` |
| 👤 **Player** | 20 | `AutoTool`, `ChestStealer`, `FastPlace`, `AutoEat`, `AutoLog`, `StorageManager` |
| 🌍 **World** | 32 | Base hunt, stash finder, logout spots, chunk analiz modullari |
| 🎒 **Inventory** | 11 | `AutoTotem`, `InvCleaner`, `InventoryManager`, `AutoArmor`, `Refill` |
| 🚀 **Optimization** | 1+ | Render va chunk optimizatsiyasi |
| 🛡️ **Legit HUD** | 37 | `Coords`, `FPS`, `Clock`, `PotionStatus`, `ArmorStatus`, `Radar`, `Watermark` |
| ⚙️ **Client** | 14 | `TextGUI`, `ClientSettings`, `Friends`, `Macros`, `Profiles`, `Notifications` |
| ✦ **Misc** | 2 | Qo'shimcha utility modullar |
| 👻 **Spectre** | 114 | `SpectreEsp`, `SpectreStorageEsp`, `SpectreItemEsp`, `SpectreBlockEsp`, `SpectreChunkScanner`, `SpectreSusChunkFinder`, `SpectrePrimeChunkFinder`, `SpectreLightEsp`, `SpectreHoleEsp`, `SpectreRegionMap`, `SpectreHud`, `SpectreRadar` va hokazo |
| 🌑 **Kuro** | 112 | `KuroKillAura`, `KuroCrystalAura`, `KuroScaffold`, `KuroAutoBuild`, `KuroAutoMiner`, `KuroFreecam`, `KuroStashFinder` va hokazo |

---

## 📦 O'rnatish

### 1. Tayyor JAR (Releases / Actions)
1. [Releases](../../releases) sahifasidan (yoki Actions tab idagi artifact dan) `AnormalClient-1.0.0.jar` faylini yuklang.
2. [Fabric Loader](https://fabricmc.net/use/) o'rnatilganiga amin bo'ling (Loader 0.18.1+, MC 1.21.11).
3. JAR'ni `.minecraft/mods/` papkasiga tashlang:
   - **Windows**: `%appdata%\.minecraft\mods\`
   - **macOS / Linux**: `~/.minecraft/mods/`
4. Minecraft'ni `Fabric` profili bilan ishga tushiring.

### 2. Manbadan build (dasturchilar uchun)

```bash
git clone https://github.com/YOUR_USERNAME/AnormalClient.git
cd AnormalClient
./gradlew build -x test
```

Tayyor JAR: `build/libs/AnormalClient-1.0.0.jar`

**Talablar**: JDK 21, Gradle wrapper (repozitoriyada bor), internet (Loom va Minecraft bog'lamalari yuklash uchun).

---

## 🤖 GitHub Actions CI

[`.github/workflows/build.yml`](.github/workflows/build.yml) har bir push'da avtomatik build qiladi:

1. **JDK 21** (Temurin) o'rnatiladi.
2. `./gradlew build --no-daemon` bajariladi.
3. Tayyor `AnormalClient-*.jar` **Actions → Artifacts** bo'limiga yuklanadi (30 kun saqlanadi).
4. `v*` tag push qilinganda — JAR avtomatik **GitHub Releases** sahifasiga qo'shiladi.

> Badge ishlashi uchun tepadagi `YOUR_USERNAME` ni o'z GitHub username'ingiz bilan almashtiring.

---

## 💾 Sozlamalar qayerda saqlanadi?

Barcha sozlamalar (modullar, keybind'lar, HUD pozitsiyalari, tema) o'yin papkasidagi
`config/anormal/` katalogida (masalan, `hud.txt`, `config.json`) saqlanadi.

---

## 🗂️ Loyiha strukturasi

```
AnormalClient/
├── .github/workflows/build.yml    # CI build
├── src/main/java/com/anormal/client/
│   ├── module/impl/
│   │   ├── combat/ movement/ render/ player/ world/   # o'zimizniki modullar
│   │   ├── legit/ client/ inventory/ misc/ optimization/
│   │   ├── spectre/               # 👻 Spectre manbasidan portlar
│   │   └── kuro/                  # 🌑 Kuro manbasidan portlar
│   ├── gui/                       # ClickGUI, HUD Editor, panellar
│   └── ...
└── build.gradle / gradle.properties
```

---

## 📜 Litsenziya

Bu loyiha — o'zingiz uchun 😄

---

<div align="center">
<sub>⚠️ Faqat ta'limiy maksadlar uchun. Server qoidalarini buzish — account'ingiz uchun.</sub>
</div>
