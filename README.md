# Mosaicary

A skin wardrobe for Minecraft. Browse the skin catalogue of [mosaicary.com](https://mosaicary.com) in the game, see each skin on the player model, and wear it in one click.

For Minecraft 26.3, 1.21.11 and 1.21.1 on **Fabric** and **NeoForge**, and 1.20.1, 1.19.2, 1.18.2 and 1.16.5 on **Fabric** and **Forge**. [Русский](#русский)

**Download:** [CurseForge](https://www.curseforge.com/minecraft/mc-mods/mosaicary)

![The skin catalogue in the game](docs/images/catalogue.png)

> The code is free under the LGPL 3.0: you may change it and publish your version, with its source and under the same licence. **The name Mosaicary and the icon are not part of that:** a version of your own needs a name and an icon of its own. See [LICENSE](LICENSE).

## What it does

- **Catalogue.** Search by name, tag or player, and filter by arms, category, live eyes, order and tags. Open it with the M key or the button on the pause menu.
- **Preview.** Every skin stands on the game's own player model: the left mouse button turns it, the right one moves it, the wheel zooms.
- **Wear.** One click puts the skin on your Minecraft account. You see it at once; other players see it when you join again, or at once on a server with Mosaicary.
- **My skins.** The skins you wore or saved, and your own PNG files. Sign in to an account on mosaicary.com, and they come back in any game with the mod.
- **No licence?** Mojang will not take your skin, but with an account on mosaicary.com everybody with the mod sees it.
- **Sharing.** Players with Mosaicary and sharing on see each other's new skins at once, even on servers without the mod.
- **Beside the menus.** Your skin stands next to the main menu and the pause menu. Its place and size are in the settings, and it can be switched off.
- **See-through skins.** They show as drawn, on you and on other players. The mod warns you about them: players without it see those places solid, the clear ones usually black.
- **Live eyes.** Skins marked with an eye blink and look around with the [Fresh Moves+](https://modrinth.com/resourcepack/freshmoves) resource pack, [Entity Model Features](https://modrinth.com/mod/entity-model-features) and [Entity Texture Features](https://modrinth.com/mod/entitytexturefeatures). The wardrobe says if something is missing. Live eyes need 1.18.2 or newer: Entity Model Features goes no further back.
- **3D Skin Layers.** With [3D Skin Layers](https://modrinth.com/mod/3dskinlayers), the second layer of a skin stands out in 3D on every figure the mod draws: the preview, the wardrobe, the figures beside the menus and the catalogue pictures. Not on Forge 1.16.5, where 3D Skin Layers has only an old line.
- **Female Gender Mod.** With [Female Gender Mod](https://modrinth.com/mod/female-gender) (1.18.2 and newer), each skin in My skins remembers its body and brings it back when you wear it; the card of a skin has a body menu, and girls' skins show breasts on the figures and pictures. With 3D Skin Layers as well, the breasts get the 3D layer too. Not on Forge 1.19.2 and NeoForge 1.21.11, which have no working Female Gender Mod.

![Your skin beside the main menu](docs/images/menu.png)

![A skin open in the wardrobe](docs/images/skin.png)

![My skins](docs/images/my-skins.png)

## Requirements

- Minecraft 26.3, 1.21.11, 1.21.1, 1.20.1, 1.19.2, 1.18.2 or 1.16.5
- Java 25 for 26.3, Java 21 for 1.21.11 and 1.21.1, Java 17 for 1.20.1, 1.19.2 and 1.18.2, Java 8 for 1.16.5
- On Fabric: Fabric Loader 0.19.5 or newer and Fabric API
- On NeoForge (26.3, 1.21.11, 1.21.1): NeoForge for your version of the game
- On Forge (1.20.1, 1.19.2, 1.18.2, 1.16.5): Forge for your version of the game

Each version of the game and each loader has its own file; take the one for yours.

Optional: on Fabric, [Mod Menu](https://modrinth.com/mod/modmenu), for the settings button in its list (on NeoForge and Forge the settings open from the Config button in the mod list), the three projects under Live eyes above, 3D Skin Layers and Female Gender Mod. The mod works without them, in single player and on any server. The server does not need it.

The mod's interface is translated into English, Russian, German, Spanish, French, Italian, Japanese, Korean, Dutch, Polish, Brazilian Portuguese, Turkish, Ukrainian and Simplified Chinese.

## What goes to mosaicary.com

The catalogue comes from the site. With sharing on, which the mod asks about on your first Wear, the site keeps the skin you wear and your player id, so that other players with the mod can see it. If you turn on My skins on the site or sign in to an account there, it also keeps the list of your skins and your own skin files. "Delete my data" in the mod's settings removes all of it. More at [mosaicary.com/data](https://mosaicary.com/data).

## Building from source

You need JDK 25, JDK 21 (for 1.21.11 and 1.21.1) and JDK 17 (for 1.20.1 and older); Gradle finds them by itself once they are installed.

```
./gradlew build
```

The jars land in `versions/<game version>-<loader>/build/libs/`, for example `versions/1.20.1-forge/build/libs/`. All of them are built from the one source in `src/`: lines marked `//#if FABRIC`, `//#if NEOFORGE`, `//#if FORGE` or `//#if MC>=…` are kept only for that loader or those versions.

1.16.5 runs on Java 8: the mod is compiled with JDK 17 and its jar is then brought down to Java 8 by [JvmDowngrader](https://github.com/unimined/JvmDowngrader), which the build fetches by itself.

```
./gradlew test
./gradlew :26.3-fabric:runClientGameTest
```

The first runs the tests that need no game. The second starts the game and drives it with real input, and it needs a connection to mosaicary.com for the catalogue. It runs on 26.3 and 1.21.11; for the older versions Fabric API has no such tests.

The source also holds parts this release keeps hidden: worn 3D models and a quick ring. They may change or go away.

## Licence

[GNU LGPL 3.0](COPYING.LESSER) (SPDX: LGPL-3.0-only), with the [GNU GPL 3.0](COPYING) it builds on. You may use, change and publish the code, as long as what you publish keeps the source open under the same licence; other mods and launchers may use it and ship it without opening their own code. The name Mosaicary and the icon are not licensed: a changed version published by someone else needs its own. Versions 1.0.0 to 1.0.3 were released as "all rights reserved". The details, and the parts under other licences, are in [LICENSE](LICENSE). Questions: contact@mosaicary.com.

The hat on the mod's icon is "[Fancy Hat [ minecraft style ]](https://sketchfab.com/3d-models/fancy-hat-minecraft-style-b0f3fade197b44119939bd324c944a7a)" by [LepikGem](https://sketchfab.com/LepikGem), under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/), put on the author's own model and rendered as the icon.

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.

---

# Русский

Гардероб скинов для Minecraft. Каталог скинов с [mosaicary.com](https://mosaicary.com) прямо в игре: посмотреть скин на модели игрока и надеть его одним нажатием.

Для Minecraft 26.3, 1.21.11 и 1.21.1 на **Fabric** и **NeoForge** и для 1.20.1, 1.19.2, 1.18.2 и 1.16.5 на **Fabric** и **Forge**.

**Скачать:** [CurseForge](https://www.curseforge.com/minecraft/mc-mods/mosaicary)

> Код свободный, под LGPL 3.0: его можно менять и выкладывать свою версию — с исходным кодом и под той же лицензией. **Имя Mosaicary и иконка в это не входят:** у своей версии должны быть своё имя и своя иконка. См. [LICENSE](LICENSE).

## Что умеет

- **Каталог.** Поиск по названию, тегу и игроку, фильтры по рукам, категории, живым глазам, порядку и тегам. Открывается клавишей M или кнопкой в меню паузы.
- **Просмотр.** Каждый скин стоит на настоящей модели игрока: левая кнопка мыши вращает, правая двигает, колесо приближает.
- **Надеть.** Одно нажатие меняет скин вашего аккаунта Minecraft. Вы видите его сразу, другие игроки после перезахода, а на сервере с Mosaicary сразу.
- **Мои скины.** Скины, которые вы надевали или сохранили, и ваши PNG-файлы. Войдите в аккаунт на mosaicary.com, и они появятся в любой игре с этим модом.
- **Нет лицензии?** Mojang не примет ваш скин, но с аккаунтом на mosaicary.com его увидят все игроки с модом.
- **Обмен.** Игроки с Mosaicary и включённым обменом сразу видят новые скины друг друга, даже на серверах без мода.
- **Рядом с меню.** Ваш скин стоит рядом с главным меню и меню паузы. Положение и размер меняются в настройках, показ можно выключить.
- **Полупрозрачные скины.** Выглядят так, как нарисованы, и на вас, и на других игроках. Мод предупреждает о них: игроки без мода видят такие места сплошными, а прозрачные обычно чёрными.
- **Живые глаза.** Скины со значком глаза моргают и смотрят по сторонам с ресурспаком [Fresh Moves+](https://modrinth.com/resourcepack/freshmoves) и модами [Entity Model Features](https://modrinth.com/mod/entity-model-features) и [Entity Texture Features](https://modrinth.com/mod/entitytexturefeatures). Если чего-то не хватает, гардероб об этом скажет. Живые глаза — с 1.18.2: раньше Entity Model Features нет.
- **3D Skin Layers.** С модом [3D Skin Layers](https://modrinth.com/mod/3dskinlayers) второй слой скина становится объёмным на всех фигурах мода: в просмотре, в гардеробе, у меню и на картинках каталога. Кроме Forge 1.16.5: там у 3D Skin Layers только старая линия.
- **Female Gender Mod.** С модом [Female Gender Mod](https://modrinth.com/mod/female-gender) (1.18.2 и новее) каждый скин в «Моих скинах» помнит своё тело и возвращает его, когда вы его надеваете; в карточке скина есть меню тела, а у скинов девушек на фигурах и картинках видна грудь. Вместе с 3D Skin Layers объёмный слой ложится и на грудь. Кроме Forge 1.19.2 и NeoForge 1.21.11: там нет рабочего Female Gender Mod.

## Что нужно

- Minecraft 26.3, 1.21.11, 1.21.1, 1.20.1, 1.19.2, 1.18.2 или 1.16.5
- Java 25 для 26.3, Java 21 для 1.21.11 и 1.21.1, Java 17 для 1.20.1, 1.19.2 и 1.18.2, Java 8 для 1.16.5
- На Fabric: Fabric Loader 0.19.5 или новее и Fabric API
- На NeoForge (26.3, 1.21.11, 1.21.1): NeoForge для вашей версии игры
- На Forge (1.20.1, 1.19.2, 1.18.2, 1.16.5): Forge для вашей версии игры

У каждой версии игры и каждого загрузчика свой файл, берите тот, что для вашего.

Необязательно: на Fabric — [Mod Menu](https://modrinth.com/mod/modmenu), чтобы открывать настройки из его списка (на NeoForge и Forge они открываются кнопкой Config в списке модов), три проекта из пункта про живые глаза, 3D Skin Layers и Female Gender Mod. Мод работает и без них, в одиночной игре и на любом сервере. Серверу мод не нужен.

Интерфейс мода переведён на английский, русский, немецкий, испанский, французский, итальянский, японский, корейский, нидерландский, польский, бразильский португальский, турецкий, украинский и упрощённый китайский.

## Что уходит на mosaicary.com

Каталог приходит с сайта. При включённом обмене, о котором мод спросит при первом «Надеть», сайт хранит надетый скин и идентификатор игрока, чтобы его видели другие игроки с модом. Если включить «Мои скины на сайте» или войти там в аккаунт, сайт хранит ещё список ваших скинов и ваши файлы скинов. Кнопка «Удалить мои данные» в настройках мода стирает всё это. Подробнее: [mosaicary.com/data](https://mosaicary.com/data).

## Сборка

Нужны JDK 25, JDK 21 (для 1.21.11 и 1.21.1) и JDK 17 (для 1.20.1 и старше); Gradle находит их сам, когда они установлены.

```
./gradlew build
```

Готовые файлы появятся в `versions/<версия игры>-<загрузчик>/build/libs/`, например `versions/1.20.1-forge/build/libs/`. Все собираются из одного кода в `src/`: строки с пометкой `//#if FABRIC`, `//#if NEOFORGE`, `//#if FORGE` или `//#if MC>=…` остаются только для своего загрузчика или своих версий.

1.16.5 работает на Java 8: мод компилируется JDK 17, а затем его jar переводится на Java 8 с помощью [JvmDowngrader](https://github.com/unimined/JvmDowngrader), сборка скачивает его сама.

## Лицензия

[GNU LGPL 3.0](COPYING.LESSER) (SPDX: LGPL-3.0-only) вместе с [GNU GPL 3.0](COPYING), на которой она построена. Код можно использовать, менять и выкладывать, если выложенное остаётся открытым под той же лицензией; другие моды и лаунчеры могут пользоваться им и класть его в свои сборки, не открывая собственный код. Имя Mosaicary и иконка не лицензируются: изменённой версии от другого автора нужны свои. Версии 1.0.0–1.0.3 вышли с «все права защищены». Подробности и части под другими лицензиями — в [LICENSE](LICENSE). Вопросы: contact@mosaicary.com.

Шляпа на иконке мода — «[Fancy Hat [ minecraft style ]](https://sketchfab.com/3d-models/fancy-hat-minecraft-style-b0f3fade197b44119939bd324c944a7a)» от [LepikGem](https://sketchfab.com/LepikGem), лицензия [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/); она надета на собственную модель автора мода и отрисована как иконка.

Это не официальный продукт Minecraft. Проект не одобрен Mojang или Microsoft и не связан с ними.
