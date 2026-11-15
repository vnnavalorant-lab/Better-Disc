# Throwing Discs (Fabric 1.20.1)

Itens: iron_/gold_/diamond_/netherite_throwing_disc (botão direito para arremessar).

- Ricocheteiam (reflexão) em cada bloco; partículas de giro constantes no ar; partículas + som ao colidir.
- Após 30s sem parar, o disco refaz os ricochetes ao contrário e volta ao jogador (item devolvido; no criativo não consome).
- Animação de arremesso em 1ª pessoa, 3ª pessoa (frente/trás) e para outros jogadores (via pacote S2C).
- Receitas: 4 lingotes em forma de losango -> 2 discos.

## Compilar
Requer JDK 17. Com Gradle 8.6+ instalado: `gradle build` (ou `gradle wrapper --gradle-version 8.6` e depois `./gradlew build`).
O .jar sai em build/libs/throwing-discs-1.0.0.jar. Requer Fabric API.

## Ajustes rápidos
- Pose da animação: `ThrowAnimClient.KEYS` (keyframes), duração `DURATION`, soltura `ThrowAnimNetworking.RELEASE_DELAY`.
- Dano/velocidade/cor por disco: `DiscTier`. Tempo de ida: `ThrowingDiscEntity.OUTBOUND_TICKS` (600 = 30s).

## Defesa com espada
Quando há um disco de outro jogador a até 16 blocos, qualquer espada pode "defender" com botão direito
(pose estilo 1.8 em 1ª e 3ª pessoa; reduz a velocidade ao defender). Sem disco por perto a espada é vanilla,
e a defesa cessa sozinha quando a ameaça some.
Se o disco acerta alguém defendendo: prende na lâmina por 3s soltando faíscas; depois
- espada de madeira ou com "copper" no id (de outro mod): quebra, o defensor toma o dano e o disco volta ao dono sem machucá-lo;
- outras espadas: o disco é rebatido, volta refazendo os ricochetes e causa dano em quem o lançou.

## Arremesso
Segure o botão direito para carregar (tempo do arco, 20 ticks = força total; mínimo como o arco) e solte para lançar.
Carga parcial lança mais devagar. O disco voando tem o mesmo tamanho do item em 1ª pessoa.
O dono também pode se defender com espada do próprio disco rebatido.

## Quebra de blocos
O disco em ida quebra vidros (blocos e painéis), vasos de planta e vasos decorados e segue em frente sem ricochetear
(respeita proteção de spawn; não quebra inquebráveis nem grades de ferro).

## Disco na mão (3ª pessoa)
A pose do disco na mão fica em `assets/throwingdiscs/models/item/*_throwing_disc.json` (`display.thirdperson_*`):
`rotation` [90,0,0] deixa o disco deitado na palma; ajuste `translation`/`rotation` se precisar.

## Partículas do vento
Enquanto o disco voa: riscos brancos translúcidos (`wind_streak`) nascem na borda e ficam para trás formando hélices,
e arcos de vento (`wind_ring`) giram no plano do disco. Texturas em `assets/throwingdiscs/textures/particle/` (podem ser trocadas).
