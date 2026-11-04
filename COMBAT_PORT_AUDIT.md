# Combat port auditi (echo `kuro` → Anormal)

Sana: 2026-10-30. Tekshiruv usuli: manba kodi statik tahlili + `./gradlew build`.

## 1. Modul qamrovi

Echo manbasida `Category.COMBAT` bilan belgilangan **37 ta** modul bor (foydalanuvchi
aytgan 38 — ehtimol `automace/SpearDtapHandler` yoki `KuroAttackPackets` kabi yordamchi
ham modul deb sanalgan; ular modul emas, handler/util).

Barcha 37 tasi Anormal'da mavjud va `ModuleManager`da ro'yxatga olingan.
Echo'dagi tab nomi saqlanmagan: portlarning ko'pchiligi alohida **Kuro** tab'ida
(sidebar'dagi "source-view" toggle bilan ochiladi), faqat 4 tasi Combat tab'ida.

| # | Echo moduli | Anormal klassi | Tab | Settings |
|---|---|---|---|---|
| 1 | AimAssist | AimAssist | COMBAT | 13 |
| 2 | AnchorAura | KuroAnchorAura | COMBAT | 11 |
| 3 | AnchorBomb | KuroAnchorBomb | KURO | 4 |
| 4 | AntiBot | AntiBot | CLIENT | 3 |
| 5 | AttackAura | KuroAttackAura | KURO | 13 |
| 6 | AttackPackets | KuroAttackPackets | KURO | 2 |
| 7 | AutoAnchor | AutoAnchor | WORLD | 8 |
| 8 | AutoArmor | AutoArmor | INVENTORY | 14 |
| 9 | AutoBox | KuroAutoBox | KURO | 11 |
| 10 | AutoClicker | AutoClicker | COMBAT | 8 |
| 11 | AutoCobweb | KuroAutoCobweb | KURO | 4 |
| 12 | AutoDtap | KuroAutoDtap | KURO | 3 |
| 13 | AutoMace | AutoMace | WORLD | 24 |
| 14 | AutoShieldBreak | KuroAutoShieldBreak | KURO | 3 |
| 15 | AutoTotem | AutoTotem | INVENTORY | 36 |
| 16 | AutoWeapon | KuroAutoWeapon | KURO | 16 |
| 17 | Backtrack | KuroBacktrack | KURO | 4 |
| 18 | BreachSwap | KuroBreachSwap | KURO | 3 |
| 19 | Criticals | Criticals | COMBAT | 11 |
| 20 | CrystalAura | KuroCrystalAura | KURO | 10 |
| 21 | CrystalDtap | KuroCrystalDtap | KURO | 8 |
| 22 | FastCrystal | KuroFastCrystal | KURO | 7 |
| 23 | HitSounds | KuroHitSounds | COMBAT | 7 |
| 24 | HitboxExpand | KuroHitboxExpand | KURO | 5 |
| 25 | JumpReset | JumpReset | COMBAT | 4 |
| 26 | KillAura | KillAura | COMBAT | 52 |
| 27 | ProjectileAimBot | KuroProjectileAimBot | COMBAT | 8 |
| 28 | Reach | Reach | COMBAT | 3 |
| 29 | SafeAnchor | KuroSafeAnchor | KURO | 6 |
| 30 | SilentAura | SilentAura | COMBAT | 34 |
| 31 | SimpleCrystalAura | KuroSimpleCrystalAura | KURO | 7 |
| 32 | SpearSwap | KuroSpearSwap | KURO | 4 |
| 33 | Surround | Surround | WORLD | 8 |
| 34 | TotemPops | KuroTotemPops | KURO | 7 |
| 35 | TriggerBot | KuroTriggerBot | KURO | 18 |
| 36 | Velocity | Velocity | COMBAT | 10 |
| 37 | WTap | WTap | COMBAT | 8 |

## 2. Settings

Har bir modulda `new XSetting(...)` bilan e'lon qilingan barcha setting
`addSetting(...)` orqali ro'yxatga olingan — GUI'da ko'rinmay qoladigan setting yo'q
(37/37 modul, 0/… yetim setting).

Lekin "GUI'da bor" ≠ "ishlaydi". Quyidagi setting'lar **e'lon qilingan, lekin hech
qayerda o'qilmaydi** (ya'ni ularni o'zgartirish natija bermaydi):

| Modul | O'lik setting | Sabab |
|---|---|---|
| AutoBed | `ignoreOwn` | eski qarz (port emas) |
| BowAimbot | `restore` | eski qarz |
| KeepSprint | `motionWhenHurt`, `hurtTime` | eski qarz |
| SilentAura | `showTarget`, `targetColor`, `attackColor` | eski qarz |

**Tuzatildi:** `KuroAnchorAura` ning `silentAim` / `aimSpeed` endi ishlaydi — `aimAt()`
qo'shildi: har tick `aimSpeed` gradusgacha buriladi, `silentAim` yoqilganda burchak faqat
serverga yuboriladi (`KuroRotations` + LookAndOnGround), aks holda kamera vanilla
`changeLookDirection` yo'li bilan buriladi. Aim anchor qo'yish, safety-blok, zaryadlash va
portlatish qadamlarining har birida ishlatiladi va burchak yetmaguncha interaksiya
qilinmaydi.

## 3. Hook / wiring holati

Modul `onTick()`ga tayanmasa, tashqi hook kerak. Tekshirilgan hook'lar:

- **KuroHitSounds** — `onAttack(target)` ni hech kim chaqirmasdi (modul jim edi).
  Tuzatildi: `InteractionManagerMixin.anormal$afterAttack` dan chaqiriladi.
- **KuroFastCrystal** — `onSpawn` `ClientEntityEvents.ENTITY_LOAD` orqali o'zi
  ulanadi (OK).
- **KuroBacktrack** — `KuroBacktrackPacketMixin`, `KuroBacktrackGhostBoxMixin` (OK).
- **KuroTotemPops** — `KuroTotemPopsMixin` (`onEntityStatus`, OK).
- **KuroHitboxExpand** — `KuroHitboxExpandMixin` (OK).
- **KuroAttackPackets.flush()** — `ModuleManager` tick boshida (OK).

Ishlatilmay qolgan yordamchi (echo'da hook bo'lgan, bu yerda chaqiruv yo'q):

| Modul | Chaqirilmayotgan API | Natija |
|---|---|---|
| KuroAnchorBomb | `beforeUse()`, `afterUse()` | "use" tugmasi orqali anchor qo'yish yo'li yo'q; faqat tick state machine ishlaydi |
| KuroAutoWeapon | `wantsStep`, `followUpExpected`, `willMaceSmash`, `shieldBreakActive`, `wouldBlockHit` | mace/spear follow-up mantiqining bir qismi soddalashtirilgan |
| KuroAutoAnchor | `anchorDamage()` | tashqi modullar uchun API yo'q |
| KuroAutoMace | `isAutoAttacking()` | tashqi modullar uchun API yo'q |
| KuroReach | `entityValue()`, `block()` | reach qiymatlari boshqa modullarga ko'rinmaydi |
| KuroAntiBot | `isBotPlayer()` | portlangan modullar eski `impl.client.AntiBot`ni ishlatadi |
| KuroSwapStateManager | `hasActiveSwaps()`, `updateSlot()` | ishlatilmaydi |

## 4. Kompilyatsiya

Audit boshida loyiha **kompilyatsiya bo'lmasdi**: 72 xato (echo 1.21.4/1.21.8 API'si
1.21.11 mapping'iga to'g'ri kelmasligi). `Anormal/tools/compile-check.sh` qo'lda
yozilgan classpath bilan buni ko'rsatmagan — haqiqiy `./gradlew compileJava` ko'rsatdi.

Tuzatilgan API farqlari:

- `Entity#getPos()` → `getEntityPos()`
- `LivingEntity#isDeadOrDying()` → `!isAlive()`
- `Player#hasLineOfSight(Entity)` → `canSee(Entity)`
- `GameProfile#getName()` → `name()` (authlib 7 record)
- `PlayerInventory#getMainHandStack()` → `getSelectedStack()`
- `ClientPlayerInteractionManager#attack(...)` → `attackEntity(PlayerEntity, Entity)`
- `SimpleSoundInstance` yo'q → `PositionedSoundInstance.ui(event, pitch, volume)`
- `PositionedSoundInstance.master(...)` → `PositionedSoundInstance.ui(...)`
- `InputUtil.Type#fromKeyCode(code, 0)` → `createFromCode(code)`
- `Box#squaredDistanceTo(Vec3d)` 1.21.11'da yo'q → qo'lda hisoblash
- `Block.BlockEntityProviderLike` (mavjud emas) → `BlockState#hasBlockEntity()`
- `ClientPlayerEntity#turn(a, b)` → `changeLookDirection(a, b)` (0.15 koeffitsiyent bir xil)
- `NumberSetting.getValue()` `Double` qaytaradi → yangi `getAsDouble()` qo'shildi
- `BooleanSetting`: `setEnabled(boolean)` va 2 argumentli konstruktor qo'shildi

Natija: `./gradlew build` muvaffaqiyatli, `build/libs/AnormalClient-1.0.0.jar`.

## 5. Tekshirilmagan

O'yin ichida (runtime) sinov o'tkazilmagan: modul mantiqi, rotation convergence,
packet pacing va anti-cheat xatti-harakati faqat kod bo'yicha baholangan.
