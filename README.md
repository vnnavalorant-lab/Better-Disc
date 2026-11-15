# Throwing Discs (Fabric 1.20.1)

Itens: copper_/iron_/gold_/diamond_/netherite_throwing_disc (botão direito para arremessar).

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
