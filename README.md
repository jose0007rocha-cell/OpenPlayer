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
- Build com Android Gradle Plugin 8.9.1 e Gradle 8.11.1 (JDK 17).
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

## Tráfego HTTP (cleartext)
`usesCleartextTraffic="true"` está ligado para permitir links `http://` de vídeos. Risco: esse tráfego não é criptografado e pode ser lido ou alterado na rede. Prefira links `https://`.

## Pendência para a Google Play
O decodificador FFmpeg (`libffmpegJNI.so`) precisa ser compatível com páginas de memória de 16 KB (exigência da Play para apps que miram o Android 15 ou mais novo). Verifique antes de publicar.

## Instalar
Copie o APK para o celular e permita "instalar apps desconhecidos".
