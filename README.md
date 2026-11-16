# Throwing Discs

Mod Fabric para Minecraft 1.20.1. Discos de arremesso (ferro, ouro, diamante e netherite) que ricocheteiam nos blocos e voltam para quem jogou.

## Como funciona

- Segure o botão direito para carregar e solte para arremessar. O tempo de carga é o mesmo do arco.
- O disco ricocheteia em cada bloco. Depois de 30 segundos (ou 22 blocos de distância) ele refaz o caminho e volta para o jogador.
- Quebra vidro, vasos, plantações, bambu e cana-de-açúcar pelo caminho.
- Gruda em blocos de mel até alguém chegar perto.
- Dois discos que se cruzam no ar se chocam e mudam de rota.
- Ejetores e dispensadores lançam o disco em linha reta.
- Lobos domados correm atrás do disco do dono e trazem de volta.
- Cada lançamento gasta 1 de durabilidade (ferro 120, ouro 60, diamante 320, netherite 600). Conserta na bigorna com o material do disco.

Receita: 4 lingotes em losango rendem 2 discos.

## Compilar

Requer JDK 17 e Fabric API.

```
gradle build
```

O jar sai em `build/libs/`.

## Onde mexer

- Dano, velocidade e durabilidade: `DiscTier`
- Tempo de ida: `ThrowingDiscEntity.OUTBOUND_TICKS`
- Animação de arremesso: `ThrowAnimClient` e `ThrowAnimNetworking.RELEASE_DELAY`
- Comportamento dos lobos: `WolfDiscGoal`
- Pose do disco na mão em 3ª pessoa: `models/item/*_throwing_disc.json`
