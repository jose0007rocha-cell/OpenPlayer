# Como atualizar o OpenPlayer no GitHub (PowerShell, Windows)

Rode um bloco de cada vez, dentro da pasta do projeto.

## 1. Entrar na pasta
    cd F:\BACKUP\Notas\OpenPlayer

## 2. Trazer o que já está no GitHub (evita conflito)
    git pull origin main

## 3. Trocar o código antigo pelo novo
Apague a pasta app\src e extraia o zip novo por cima da pasta do projeto:

    Remove-Item -Recurse -Force app\src
    Expand-Archive -Force "$HOME\Downloads\OpenPlayer-v1.3.zip" -DestinationPath .

(Apagar app\src antes evita sobrar arquivos antigos e recursos duplicados.)

## 4. Conferir o que mudou
    git status

## 5. Enviar
    git add -A
    git commit -m "v1.3: icone, fonte, temas com desfoque, player e ordenar pastas"
    git push origin main

## 6. Pegar o APK
GitHub > aba Actions > último "Build APK" com o visto verde > Artifacts > OpenPlayer-debug.

## Se der erro
- `origin does not appear to be a git repository`:
      git remote add origin https://github.com/jose0007rocha-cell/OpenPlayer.git
- `failed to push some refs`: rode `git pull origin main` e depois o push de novo.
- Build vermelho no Actions: abra o log, procure a linha que começa com `ERROR:` ou `e:` e me envie.
