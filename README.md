# Open Player

Player de vídeo simples: sem anúncios, sem compras, sem rastreadores.
Tema sempre escuro, com preto AMOLED opcional, cores de destaque em gradiente e fundo personalizável (menu ⋮ > Temas).
Motor: AndroidX Media3 (ExoPlayer). Abre vídeos locais, links http/https (MP4, HLS, DASH) e arquivos enviados por outros apps.

## Compilar (opção A: Android Studio)
1. Abra a pasta no Android Studio (Koala ou mais novo) e aguarde o Gradle sincronizar.
2. Build > Build APK(s). O arquivo sai em `app/build/outputs/apk/debug/app-debug.apk`.

## Compilar (opção B: GitHub, sem instalar nada)
1. Crie um repositório e envie estes arquivos.
2. Aba Actions > "Build APK" > Run workflow.
3. Baixe o APK em Artifacts.

## Identificador do app
- Pacote e `applicationId`: `com.josercha.openplayer` (antes era `com.example.openplayer`).
- Como o identificador mudou, o Android trata como **outro app**: ele instala ao lado da versão antiga e não herda temas, posições salvas nem autorizações. Pode desinstalar a antiga depois.
- Ao mover o código do pacote antigo, apague a pasta `app/src/main/java/com/example` antes de extrair a nova versão por cima.

## Compatibilidade
- `minSdk` 24 (Android 7.0), `compileSdk`/`targetSdk` 36 (Android 16). Testado para rodar do Android 10 em diante.
- Build com Android Gradle Plugin 8.9.1, Gradle 8.11.1 e Kotlin 2.1.0 (JDK 17).
- Android 15+: a tela ocupa a área toda (edge-to-edge). Android 16: em telas grandes (tablets, dobráveis) a orientação escolhida no player pode ser ignorada pelo sistema.

## Sabores (distribuição)
| Sabor | Para quê | Exclusão de vídeos |
|---|---|---|
| `github` | APK distribuído direto (GitHub Actions) | Autoriza uma vez ("Acesso a todos os arquivos" no Android 11+, raiz do cartão SD no Android 10 ou anterior) e depois exclui sem janelas |
| `play` | Pacote (AAB) para a Google Play | Sem `MANAGE_EXTERNAL_STORAGE`: usa a janela de confirmação do sistema (uma para todos os vídeos selecionados) |

Comandos: `gradle assembleGithubRelease` (APK) e `gradle bundlePlayRelease` (AAB).

## Permissões
- `READ_MEDIA_VIDEO` (Android 13+), `READ_EXTERNAL_STORAGE` (até o Android 12) e `READ_MEDIA_VISUAL_USER_SELECTED` (Android 14+, acesso parcial: o app lista só os vídeos que o usuário escolher).
- `WRITE_EXTERNAL_STORAGE` só até o Android 9/10 (exclusão).
- `INTERNET`: abrir links de vídeo (MP4, HLS, DASH).
- `MANAGE_EXTERNAL_STORAGE` **só no sabor `github`**, para excluir sem janelas. A Google Play restringe essa permissão a casos específicos, por isso o sabor `play` não a declara.
- Legendas externas (`.srt` ao lado do vídeo) dependem de acesso aos arquivos; no sabor `play` podem não ser encontradas no Android 11+.

## Reprodução em segundo plano e notificação
- O ExoPlayer roda no `PlaybackService` (Media3 `MediaLibraryService`). A tela do player se conecta a ele por um `MediaController`.
- Há notificação de mídia (play, pause e avançar/voltar) e controles na tela de bloqueio. O Android 13+ pede a permissão de notificações uma vez; sem ela o som continua, só não aparece a notificação.
- Ao ir para o início ou outro app com o vídeo tocando, o som continua (o vídeo segue sendo decodificado sem tela, para voltar sem travar). Dá para desligar em Configurações > "Continuar tocando em segundo plano"; desligado, o vídeo pausa ao sair.
- Pelo botão voltar, a reprodução para e o serviço encerra.
- Permissões novas: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS` e `WAKE_LOCK` (mantém o som tocando com a tela apagada).

## Android Auto (MVP)
O app é um app de mídia para o Android Auto: o carro navega pelas **pastas > vídeos** do celular e toca pelo mesmo serviço de reprodução (`PlaybackService`, um `MediaLibraryService`). O Android Auto mostra a interface do carro sozinho, por isso não usa a biblioteca de templates `androidx.car.app`.
- Arquivos: `res/xml/automotive_app_desc.xml` (declara `<uses name="media"/>`), `meta-data` `com.google.android.gms.car.application` no manifest e a navegação em `PlaybackService.kt` (`onGetLibraryRoot`, `onGetChildren`, `onGetItem`).
- No carro toca só o **áudio** dos vídeos; o Android Auto não exibe vídeo.
- Precisa da permissão de vídeos já liberada no celular (sem ela as listas aparecem vazias).
- Fora do MVP: busca por voz, capas nas listas, listas de reprodução e favoritos, Android Automotive OS (carros com Android embutido).
- Testar: instale o app, no celular abra Android Auto > Configurações > toque 10 vezes em "Versão" para liberar o modo desenvolvedor > ative "Fontes desconhecidas" (APK fora da Play) e conecte ao carro ou use o Desktop Head Unit (DHU).

## Release com R8
O release usa R8 (`isMinifyEnabled` e `isShrinkResources` ligados): código e recursos não usados são removidos e os nomes são ofuscados. As regras ficam em `app/proguard-rules.pro` (Media3 e FFmpeg, Glide, DataBinding e views próprias).
- O build gera `mapping.txt` (artefato `OpenPlayer-mapping` no Actions). Guarde-o junto de cada versão publicada: sem ele, os crashes saem ilegíveis.
- Se aparecer "Missing classes detected while running R8", o log traz as regras sugeridas (`missing_rules.txt`); acrescente-as ao `proguard-rules.pro`.
- Depois de qualquer mudança de dependência, teste o release (reprodução, legendas, temas, exclusão e "Sobre o app").

## Tráfego HTTP (cleartext)
`usesCleartextTraffic="true"` está ligado para permitir links `http://` de vídeos. Risco: esse tráfego não é criptografado e pode ser lido ou alterado na rede. Prefira links `https://`.

## Pendência para a Google Play
O decodificador FFmpeg (`libffmpegJNI.so`) precisa ser compatível com páginas de memória de 16 KB (exigência da Play para apps que miram o Android 15 ou mais novo). Verifique antes de publicar.

## Instalar
Copie o APK para o celular e permita "instalar apps desconhecidos".
