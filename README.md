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

## Instalar
Copie o APK para o celular e permita "instalar apps desconhecidos".
