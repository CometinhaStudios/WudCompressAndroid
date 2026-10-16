# CDecrypt Android v0.3 – interface baseada no WudCompressMobile
Este módulo é um port do CDecrypt de VitaSmith e crediar, com UI inspirada no WudCompressMobile (CometinhaStudios). O WudCompressAndroid original não é modificado; os arquivos ficam no subprojeto `cdecrypt-android` na branch `cdecrypt-android-build`.

## Recursos
- Escolha independente da pasta com `title.tik`, `title.tmd`, `.app` e `.h3`, e do destino.
- Cria automaticamente subpasta `CDecrypt_NomeDoJogo`, sem sobrescrever outro resultado.
- Progresso **real** calculado pelo número de bytes escritos, somando os tamanhos das entradas do FST do Wii U. A preparação inicial pode ficar em 0%; somente a conclusão bem-sucedida marca 100%.
- Serviço foreground `dataSync` com notificação de progresso; deve continuar após minimizar/tela apagada, sujeito às restrições do Android.
- Diagnóstico técnico detalhado, armazenado privadamente e copiado pelo botão.
- Código nativo baseado em https://github.com/VitaSmith/cdecrypt (GPL-3.0-or-later).

## Limitações
- Requer Android ARM64 e Android 8 ou superior; acesso especial a arquivos (MANAGE_EXTERNAL_STORAGE), porque o motor original opera por caminhos, não através de SAF.
- Destino deve estar em armazenamento local; seletor de arquivos em nuvem não é compatível.
- Aplicativo experimental, sem garantia de extração para todos os dumps, e sem sistema de pausar/reiniciar uma tarefa interrompida.
- A barra é baseada em **bytes gravados**, não quantidade de arquivos; não representa verificação ou trabalho de preparação.
- Diferentes builds de teste podem ter assinaturas incompatíveis; pode ser preciso desinstalar versão antiga antes de instalar.
