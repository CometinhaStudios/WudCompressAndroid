# WudCompressMobile

**Criador da versão mobile: Halleyplaybr**

WudCompressMobile é uma versão Android para conversão **WUD ⇄ WUX**, criada a partir do estudo e uso do código-fonte do **WudCompress v1.0 original, de Exzap**.

A versão mobile foi desenvolvida por **Halleyplaybr com assistência de IA**. O núcleo foi adaptado/reimplementado em Java para funcionar nativamente no Android, mantendo compatibilidade com o formato WUX0 do original.

## v2.2 — WudCompressMobile

- Nome alterado para **WudCompressMobile**.
- Ícone próprio do aplicativo.
- Tema acompanha automaticamente o tema do dispositivo: claro ou escuro.
- Correção de layout para respeitar barra de status, câmera frontal/cutout e barra de navegação.
- Removido o texto explicativo que ficava solto no rodapé da tela.
- Tela **Sobre** com autoria, origem do código e informação sobre assistência de IA.
- Conversão em **Foreground Service**, continuando ao minimizar ou remover o app dos Recentes.
- Notificação de progresso durante a conversão.
- `PARTIAL_WAKE_LOCK` enquanto processa arquivos grandes.
- Permissões persistentes de URI quando o provedor de arquivos Android oferece suporte.

> Forçar parada do aplicativo pelas Configurações do Android encerra o serviço, como acontece com qualquer aplicativo Android. Alguns fabricantes também podem aplicar limites extras de bateria a tarefas longas.

## Origem e créditos

- **WudCompressMobile / versão mobile:** Halleyplaybr.
- **Base técnica:** código-fonte do WudCompress v1.0 original, de Exzap.
- **Desenvolvimento mobile:** criado com assistência de IA.
- O formato WUX0 e a lógica de conversão foram estudados a partir do projeto original e adaptados para Android.

## Núcleo

- Java puro no Android, sem JNI/CMake/NDK.
- WUD → WUX.
- WUX → WUD.
- Formato WUX0 compatível.
- Cabeçalho de 32 bytes em little-endian.
- Setor padrão de `0x8000` bytes.
- Tabela de índices `uint32`.
- Verificação byte a byte opcional no final.
- `ACTION_OPEN_DOCUMENT` / `ACTION_CREATE_DOCUMENT`, sem pedir acesso total ao armazenamento.
- Acesso aleatório a arquivos grandes via `android.system.Os.pread/pwrite`.

## Correção adicional em relação ao algoritmo original

O WudCompress v1.0 usa um hash simples para detectar setores repetidos. Nesta implementação, quando um hash coincide, os bytes do setor também são comparados antes de reutilizar o índice. Isso mantém o formato WUX0 e evita uma possível colisão de hash gerar saída incorreta.

## Testes do núcleo

- WUD → WUX → WUD.
- Comparação byte por byte entre entrada e saída reconstruída.
- SHA-256 inicial e final idênticos no teste principal.
- Leitura de WUX produzido pela lógica original.

## Build

Android Gradle Plugin 8.7.3, `compileSdk 35`, `minSdk 26`, `targetSdk 35`.

O workflow `.github/workflows/build-apk.yml` gera o artifact:

`WudCompressMobile-debug`
