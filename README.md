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
Se o disco acerta alguém defendendo: prende na lâmina por 5s (animação: espada erguida, disco girando deitado e balançando ao redor da lâmina, como no vídeo de referência); depois
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

## Animação de defesa (5s)
Durante os 5s dá para saber o desfecho: espada que vai quebrar (madeira / "copper") treme cada vez mais forte, solta lascas do
próprio item e estala em intervalos cada vez menores; espada boa fica firme, só com faíscas e um "tim" ocasional.
Ajustes em `ClashState` (posição do disco na lâmina em 1ª/3ª pessoa, velocidade do giro `SPIN_DEG_PER_TICK`),
`ClashRender.wobble` (amplitude do balanço), `ThrowingDiscEntity.CLASH_TICKS` (duração) e `clashTells` (estalos).

## Disco na mão em 1ª pessoa (arremesso)
O disco é desenhado preso à ponta do braço (`PlayerEntityRendererMixin`: `DISC_Y`, `DISC_Z`, `DISC_TILT`) e o braço fica mais
perto da câmera (`HeldItemRendererMixin.THROW_PULL_Z`).

## Plantações, mel e ejetores
- O disco quebra plantações por onde passa (trigo, cenoura, batata, beterraba, caules de melão/abóbora, verruga do Nether,
  arbusto de frutas, tochaflor/pitcher), com drops; respeita proteção de spawn. Vale na ida e na volta.
- Bloco de mel: o disco gruda na face do bloco (sem ricochete, sem dano) e só sai quando um jogador chega a ~1,6 bloco
  (vai para o inventário de quem chegou). Se o mel for quebrado, o disco cai como item.
- Ejetor (e dispensador, mesmo comportamento padrão): lança o disco em linha reta com velocidade total. O ejetor não pega o disco de
  volta: ao retornar, o disco é largado no chão à frente dele. Se houver inventário à frente do ejetor, ele transfere normalmente (vanilla).

## Lobos domados
Lobos domados (em pé) notam o disco do dono a até 16 blocos: 60% de chance de correr atrás dele; ao passar pela boca há 45% de chance
de agarrá-lo (sem dano — lobos domados nunca são atingidos pelo disco). Com disco acima da boca, o lobo salta até ~3 blocos (sem dano de
queda). Com o disco na boca (desenhado como a raposa), ele volta ao dono e entrega o item no inventário (ou no chão). Se o lobo morrer, o
disco cai. Ajustes em `WolfDiscGoal` (`CHASE_CHANCE`, `CATCH_CHANCE`, `CATCH_REACH`, `MAX_JUMP`) e posição na boca em `WolfDiscFeatureRenderer`.
