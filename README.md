# Throwing Discs (Fabric 1.20.1)

Itens: iron_/gold_/diamond_/netherite_throwing_disc (botão direito para arremessar).

- Ricocheteiam (reflexão) em cada bloco; partículas de giro constantes no ar; partículas + som ao colidir.
- Após 30s sem parar, o disco refaz os ricochetes ao contrário e volta ao jogador (item devolvido; no criativo não consome).
- Animação de arremesso em 1ª pessoa, 3ª pessoa (frente/trás) e para outros jogadores (via pacote S2C).
- Receitas: 4 lingotes em forma de losango -> 2 discos.

## Compilar
Requer JDK 17. Com Gradle 8.6+ instalado: `gradle build` (ou `gradle wrapper --gradle-version 8.6` e depois `./gradlew build`).
O .jar sai em build/libs/throwing-discs-1.0.3.jar. Requer Fabric API.

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
  Como o disco voa na altura do peito, a varredura alcança também ~1,7 bloco abaixo dele (ajuste em
  `ThrowingDiscEntity`: `CROP_RADIUS`, `CROP_REACH_BELOW`, `CROP_REACH_ABOVE`).
- Bloco de mel: o disco gruda na face do bloco (sem ricochete, sem dano) e só sai quando um jogador chega a ~1,6 bloco
  (vai para o inventário de quem chegou). Se o mel for quebrado, o disco cai como item.
  Enquanto está grudado o disco fica "melado" (partículas próprias do mod, só do lado do cliente): gotas de mel que se formam na
  borda, esticam e caem deixando uma poça (`honey_drip` / `honey_splat`) e brilhos dourados cintilando (`honey_glint`).
  Ao ser pego o disco volta ao inventário normal (sem efeito). Texturas em `textures/particle/honey_*.png`; frequência em
  `ThrowingDiscEntity.spawnHoneyEffects`.
- Ejetor (e dispensador, mesmo comportamento padrão): lança o disco em linha reta com velocidade total. Disco de ejetor rebate
  só 1 vez: no 2º impacto contra um bloco ele é largado como item (`MAX_DISPENSED_BOUNCES`), evitando ricochetes quase infinitos. O ejetor não pega o disco de
  volta: ao retornar, o disco é largado no chão à frente dele. Se houver inventário à frente do ejetor, ele transfere normalmente (vanilla).

## Lobos domados
Lobos domados (em pé) notam o disco do dono a até 16 blocos: 60% de chance de correr atrás dele; ao passar pela boca há 45% de chance
de agarrá-lo (sem dano — lobos domados nunca são atingidos pelo disco). Com disco acima da boca, o lobo salta até ~3 blocos (sem dano de
queda). Com o disco na boca (desenhado como a raposa), ele volta ao dono e entrega o item no inventário (ou no chão). Se o lobo morrer, o
disco cai. Ajustes em `WolfDiscGoal` (`CHASE_CHANCE`, `CATCH_CHANCE`, `CATCH_REACH`, `MAX_JUMP`) e posição na boca em `WolfDiscFeatureRenderer`.

Enquanto há um disco do dono por perto (ou o lobo carrega um), o lobo não segue/teletransporta até o dono (`FollowOwnerGoalMixin` +
`WolfDiscGoal.isBusy`): o teleporte vanilla (12+ blocos) acontecia assim que o lobo errava a mordida ou desistia do disco. Acima de
32 blocos do dono o comportamento vanilla volta (teleporte normal).

## Baba do lobo
Com o disco na boca, o lobo baba (partículas do mod, só no cliente; `WolfSaliva`, chamado pelo `WolfEntityMixin`): parado, fios de
saliva pendem do canto da boca, esticam, afinam e pingam (`saliva_drip`), virando uma ondinha no chão (`saliva_splat`); correndo, gotículas
são lançadas para trás. A posição da boca segue a mesma geometria do `WolfDiscFeatureRenderer`. Frequência em `WolfSaliva.tick`.

## Choque entre discos
Dois discos em ida que se cruzam no ar batem e mudam de rota (colisão elástica entre massas iguais + um empurrão, para o desvio ser
sempre visível). O ponto da batida entra no caminho dos dois, então a volta continua refazendo o trajeto. Efeitos: clarão em estrela
(`disc_flash`), 18 faíscas que saem pelo plano do choque, esfriando de branco para laranja e vermelho, caindo e quicando
(`disc_spark`), e som metálico. Ajustes em `ThrowingDiscEntity`: `DISC_CLASH_DIST` (distância do choque), `DISC_CLASH_COOLDOWN`,
e a quantidade/velocidade das faíscas em `discSparks`. Discos voltando, presos no mel ou numa espada não colidem.

## Bambu e cana-de-açúcar
O disco quebra bambu (tem colisão: o disco corta e segue em frente, sem ricochetear) e cana-de-açúcar (e broto de bambu), com drops.
Pela varredura de plantações ele corta acima da base, que rebrota; se o disco passar pela própria célula do bloco, o bloco também quebra.
Ao quebrar um segmento, o resto do talo acima cai sozinho (física vanilla), com drops.

## Durabilidade
Cada lançamento (jogador ou ejetor) gasta 1 de durabilidade do disco (`DiscTier.durability`: ferro 120, ouro 60, diamante 320,
netherite 600; no criativo não gasta). O disco que esgota a durabilidade faz a última viagem e, ao terminar (pego, largado, grudado no
mel), quebra com som e lascas em vez de voltar como item; lobos não pegam um disco nessa última viagem. Reparo na bigorna com
o material do disco (ferro/ouro/diamante/netherite). Itens com durabilidade não empilham.
