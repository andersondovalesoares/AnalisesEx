# Configuração de compilação na nuvem — Análises Ex

O repositório GitHub está acessível para leitura, mas a integração conectada ao ChatGPT não tem permissão de escrita. Por isso, este pacote contém os dois arquivos necessários para você enviar ao repositório.

## Enviar pelo navegador do celular
1. Abra https://github.com/andersondovalesoares/AnalisesEx
2. Toque em **Add file** > **Upload files**.
3. Extraia este ZIP no celular e envie `project.zip.b64` e a pasta `.github/workflows/android.yml` (mantenha exatamente esse caminho).
4. Confirme em **Commit changes**.

## Baixar o APK
1. No repositório, abra a aba **Actions**.
2. Abra a execução **Build Android APK** e aguarde ficar verde.
3. Em **Artifacts**, baixe `AnalisesEx-debug-apk`.
4. Extraia o ZIP do artifact e instale `app-debug.apk` no Android.

## Limitação funcional importante
Este projeto é um MVP: captura a tela via MediaProjection e faz uma contagem simples de pixels verdes/vermelhos para diagnóstico. Ainda NÃO implementa de forma validada os indicadores EMA, RSI, MACD, Bollinger, Estocástico nem sinais CALL/PUT. Não use esta versão para operar dinheiro real.
