# Arena para Android — versão 1.2

Aplicativo Android independente (não oficial) para acessar `https://arena.ai/`.

## Correção do erro 400 no Google (1.2)

- Intercepta o início de OAuth do Google dentro da WebView em vez de transferir uma URL de autenticação parcialmente carregada para o Chrome.
- Apresenta uma escolha explícita: reiniciar **todo o login no navegador**, a partir da página oficial `https://arena.ai/`, ou permanecer no app e usar o login por e-mail quando disponível.
- O botão “Login no navegador” segue o mesmo fluxo seguro: sempre abre a página inicial do Arena, não uma etapa intermediária de OAuth.
- Não promete compartilhar o login do Chrome com a WebView, pois esses armazenamentos são separados. O login via Google no Chrome permanece no Chrome. O histórico lateral e a logo anterior continuam.
- Não há acesso nativo à API de autenticação do Arena; não há como completar um OAuth Google dentro do WebView sem uma integração oficial entre o serviço e um cliente Android.

## Alterações anteriores

- Não inicia mais no link antigo com ID de sessão inválido. Abre a página oficial `https://arena.ai/` para o usuário fazer login e escolher o modo de conversa.
- Nenhuma barra superior do aplicativo: WebView ocupa toda a área útil da tela.
- Menu flutuante discreto ☰ e gesto da borda esquerda para abrir o painel; arraste para a esquerda ou toque fora para fechar.
- Painel mostra o histórico oficial do site Arena dentro de uma visualização secundária com cookies compartilhados, além de Novo chat e Modo Agente.
- Sessão preservada por CookieManager, DOM storage/WebStorage e flush quando a página carrega, ao alternar de app e ao encerrar.
- Não armazena nem recolhe senhas diretamente: login e dados de chat são tratados pelo site Arena.
- Logo de coluna branca, fundo preto, fornecida pelo usuário, aplicada aos ícones e ao painel.
- Suporte a popups de autenticação e a uploads de arquivos.

## Limitações conhecidas

- Este é um cliente **WebView**, sem integração oficial com Arena. Históricos e chats dependem do servidor, rotas e da interface da Arena; alterações do site podem quebrar o painel lateral.
- **Google Sign-In pode recusar navegadores WebView embarcados**. Isso é uma política do Google, não uma configuração de cookies. O login com e-mail no site pode funcionar; quando o provedor exigir um navegador compatível, abra o site no navegador pelo botão do painel. Cookies do Chrome não são compartilhados com este app, portanto login via navegador não implica login no WebView.
- O SDK do Android não transfere cookies do Chrome/Custom Tabs para WebView. Implementar OAuth Google totalmente nativo requer suporte do proprietário do site ao fluxo de autorização (deep link, callback e sessão).
- A barra de status do Android permanece visível por questões de usabilidade; não há barra superior criada pelo app.
- WebView pode não suportar certos downloads `blob:`; use o navegador quando necessário.
- A compilação CI prova que o APK é produzido, mas **não** equivale a teste de login/gestos num aparelho real.

## Compilação

O fluxo `.github/workflows/android-apk.yml` gera `app-debug.apk` com Gradle 8.9 e Android API 35. Os builds usam um cache com o `debug.keystore` para permitir atualizações após esta versão (uma compilação inicial feita com outro certificado pode exigir desinstalação antes da nova instalação).