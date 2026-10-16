# CDecrypt Android (experimental)
Aplicativo Android com interface para selecionar diretórios Wii U NUS (arquivos .app, .h3, title.tik e title.tmd) e extrair code/content/meta.

## Gerar APK
Workflow de GitHub Actions `CDecrypt Android APK` compila a aplicação Android arm64 usando Gradle 8.10.2, Java 17 e NDK 26.3. O APK debug pode ser baixado em **Actions → CDecrypt Android APK → run → Artifacts**.

## Limites importantes
- Não está validado em dispositivos reais.
- Somente ARM64 e diretórios locais do ExternalStorageProvider. Usa permissão especial MANAGE_EXTERNAL_STORAGE: autorize apenas se confiar no projeto.
- A extração usa código nativo derivado de CDecrypt de VitaSmith: https://github.com/VitaSmith/cdecrypt
- Código e ponte JNI distribuídos sob GPL-3.0-or-later (consulte projeto original para licenças de componentes).
- O app não contém jogos, não baixa jogos, não transforma em ISO.
- Os arquivos extraídos são criados em CDecrypt_Extraido, sem apagar o original.
- Extrações longas exigem tela ligada; no momento não há progresso por arquivo e nem serviço foreground.
