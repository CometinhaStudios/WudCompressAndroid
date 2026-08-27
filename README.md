# WudCompress Android — Java puro

Reimplementação Android do algoritmo WUD ⇄ WUX do **WudCompress v1.0 (Exzap)**.

## O que mudou

- O núcleo foi reescrito em **Java puro**: não usa C++, JNI, CMake nem Android NDK.
- Mantém o formato **WUX0** original:
  - cabeçalho de 32 bytes em little-endian;
  - setor padrão de `0x8000` bytes;
  - tabela de índices `uint32`;
  - setor de dados alinhado ao tamanho do setor.
- Compacta WUD → WUX.
- Descompacta WUX → WUD.
- Faz verificação byte a byte opcional ao final.
- Usa `ACTION_OPEN_DOCUMENT`/`ACTION_CREATE_DOCUMENT`, então não pede acesso total ao armazenamento.
- Usa `android.system.Os.pread/pwrite` para acesso aleatório a arquivos grandes.

## Correção importante em relação ao original

O WudCompress v1.0 reutiliza um setor quando o hash simples de 32 bytes coincide, sem comparar o conteúdo real. Esse hash pode colidir. Nesta versão, quando o hash coincide, os bytes do setor armazenado também são comparados antes de reutilizar o índice. O arquivo continua sendo WUX0 compatível, mas evita uma classe de corrupção que o algoritmo original pode produzir em colisões artificiais.

## Testes feitos nesta versão

1. WUD → WUX → WUD com setores repetidos.
2. Comparação byte por byte entre o WUD inicial e o WUD reconstruído.
3. SHA-256 inicial/final idêntico.
4. Leitura/descompactação de um WUX gerado pela lógica original do WudCompress.
5. Teste do núcleo com `javac`/JVM sem Android.

Resultado do teste principal:

- WUD inicial SHA-256: `910d6184085291beab1fc9fd6902cc6b28b20773858e99c7f408bb3c4c131864`
- WUD reconstruído SHA-256: `910d6184085291beab1fc9fd6902cc6b28b20773858e99c7f408bb3c4c131864`
- Resultado: **PASS**

Teste de compatibilidade com WUX original:

- WUD esperado SHA-256: `cc47fb32f503d1b52665a288dc3f35f463139ace9d535c9a886eddf67ccfaebd`
- WUD produzido SHA-256: `cc47fb32f503d1b52665a288dc3f35f463139ace9d535c9a886eddf67ccfaebd`
- Resultado: **COMPAT_PASS**

## Build

O projeto usa Android Gradle Plugin 8.7.3, `compileSdk 35`, `minSdk 26` e não possui dependências AndroidX.

### Android Studio

Abra a pasta do projeto, sincronize o Gradle e execute `assembleDebug`.

### GitHub Actions

O arquivo `.github/workflows/build-apk.yml` instala o SDK e gera automaticamente:

`app/build/outputs/apk/debug/app-debug.apk`

## Observação

Alguns provedores de arquivos do Android não oferecem um descritor realmente seekable. Para WUD/WUX, prefira selecionar o arquivo no armazenamento local do aparelho/SD quando possível.
