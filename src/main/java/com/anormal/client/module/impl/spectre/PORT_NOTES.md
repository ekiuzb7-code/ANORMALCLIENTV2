# Spectre→Anormal port notes (1.21.11, Yarn b6)

Class base: `com.anormal.client.module.Module`
- ctor: `super("SpectreXxx", "Description string", Category.SPECTRE)` (+ optional default key).
- settings: `addSetting(new BooleanSetting("X", "y", def))` — NumberSetting(name, desc, def, min, max, step),
  ModeSetting(name, desc, def, ...modes), lucid StringSetting (name, desc, def) for free text,
  KeybindSetting(name, desc, key) (+ MOUSE_BASE mouse codes), ColorSetting(name, desc, hex).
- hooks: onEnable/onDisable, onTick (client tick POST), onPreTick (tick before input, Kuro style),
  onTickAlways (runs even when off), onRender2D(DrawContext, tickDelta) for 2D/2.5D overlays
  (use ProjectionUtil.eye/project + RenderUtils.drawBlockOutline/Fill for 3D boxes).
- notifications: NotificationManager.push(title, subtitle, accentColor); info(title, subtitle).
- actionbar: mc.player.sendMessage(Text.of("..."), true).

Source→Yarn API map (1.21.11):
- PlayerMoveEvent / packet rewrite → mixin hooks on ClientCommonNetworkHandler.sendPacket (NetworkMixin),
  via SpectrePackets dispatch; PlayerMoveC2SPacket{nested Full, PositionAndOnGround, LookAndOnGround, OnGroundOnly};
  getters getX(getY,getZ(double default)), getYaw(getPitch(float default)), isOnGround(), changesPosition/changesLook().
- ClientTickEndC2SPacket = ServerboundClientTickEndPacket.
- mc.getToastManager / ChallengeToast → NotificationManager.push.
- ServerData → net.minecraft.client.network.ServerInfo (getCurrentServerEntry()).
- JoinMultiplayerScreen → MultiplayerScreen; LevelLoadingScreen → mc.world == null / ReconfiguringScreen check.
- ConnectScreen.connect(Screen parent, MinecraftClient mc, ServerAddress, ServerInfo, boolean, Duration NULL)
  Yarn mapping method_36877 → in 1.21.11 b6 the map name is "connect" — compile confirms.
- FreecamModule → Anormal world.Freecam (camera active checks) or mc.cameraEntity.
- level.clip → world.raycast(RaycastContext); BlockPos.containing → BlockPos.ofFloored.
- ScreenEvents AFTER_INIT buttons → only in own screens; DisconnectedScreen hooking is via onTickAlways polling.
- Minecraft.execute → mc.execute; mc.schedule → mc.execute.
- Attributes/Modifier container → 1.21 reuse: attack speed / movement via player.getAttributeValue(...).
- Command list / staff list → chat packet listen via SpectrePackets.received dispatch on receivePacket
  (see SpectreReceiveHookMixin) + Text handling with Text.literal/StringUtil via chat visibility checks.
