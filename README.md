# Arena Agent para Android — app independente (não oficial)

Aplicativo Android criado para abrir diretamente:

https://arena.ai/agent/01a1cb5e-c8db-7f67-8fa1-5220b5404438

## O que faz

- Abre a sessão específica no WebView Android (não instala e não depende do PWA).
- Mantém cookies/login no armazenamento isolado do próprio aplicativo.
- Usa JavaScript, armazenamento local e seletor nativo para anexar arquivos.
- Permite voltar, atualizar, retornar ao link inicial e abrir a página no navegador externo.
- Suporta downloads HTTPS comuns via DownloadManager (na pasta Downloads).
- Oferece tela de erro para tentar novamente e limpeza de dados mediante confirmação.
- Bloqueia conteúdo não HTTPS dentro do WebView, sem coletar credenciais e sem servidor intermediário.

## Como gerar um APK instalável

### Opção A: GitHub Actions (sem Android Studio)

1. Crie um repositório no GitHub e envie o **conteúdo extraído** desta pasta, incluindo `.github`.
2. Faça push para a branch `main` (ou `master`).
3. Na guia **Actions**, abra **Compilar APK Android** e a execução concluída.
4. Em **Artifacts**, baixe `ArenaAgent-APK-Android`.
5. Extraia o ZIP baixado e instale `app-debug.apk` no Android.

O APK de depuração é assinado automaticamente pela infraestrutura de compilação e pode ser instalado manualmente; não é uma versão para publicação na Play Store.

### Opção B: Android Studio

1. Abra a pasta como projeto Gradle e instale Android SDK 35 se solicitado.
2. Escolha **Build > Build App Bundle(s) / APK(s) > Build APK(s)**.
3. Encontre `app/build/outputs/apk/debug/app-debug.apk`.

## Limitações importantes

- **Um wrapper WebView não corrige erros do próprio servidor Arena.** Ele evita o processo de instalação do PWA e usa seu próprio armazenamento.
- O Arena depende de internet, pode exigir autenticação e a sessão vinculada ao link pode exigir uma conta específica.
- Alguns provedores, especialmente o **Login com Google**, bloqueiam OAuth em WebViews por segurança. Abra pelo navegador usando o menu, ou utilize um método de login que o Arena ofereça e permita no app.
- Alguns downloads baseados em `blob:` e fluxos de autenticação por popup poderão exigir o navegador externo.
- O app não substitui o site, não contorna permissões de acesso e não contém nenhuma API privada do Arena.
- O projeto não foi compilado/testado em aparelho neste ambiente (sem SDK Android disponível). Use o workflow de compilação ou Android Studio para gerar e testar o APK.

## Segurança e privacidade

Não há integração de terceiros além do próprio site Arena carregado no WebView. Credenciais são enviadas diretamente ao domínio HTTPS do Arena. Links externos são abertos fora do app. Cookies de terceiros são habilitados para compatibilidade de login; o menu permite limpar todos os dados do app.

## Personalização

Altere a constante `START_URL` no começo de `MainActivity.java` para trocar de sessão.

**Nota:** este projeto é independente e não tem afiliação com Arena.ai. A marca e o conteúdo do website pertencem aos seus titulares.