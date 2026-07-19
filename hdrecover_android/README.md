# HdRecover Mobile

Aplicativo Android dedicado à recuperação de arquivos em HD, SSD e pendrive conectado por USB OTG.

## Funcionamento

1. O aplicativo identifica dispositivos USB Mass Storage.
2. Solicita autorização USB ao Android.
3. Abre o dispositivo em modo somente leitura usando Bulk-Only Transport/SCSI.
4. Faz uma primeira leitura sequencial procurando assinaturas.
5. Valida os candidatos e salva os arquivos numa pasta escolhida pelo usuário.

## Formatos iniciais

- JPG, PNG, GIF e BMP
- PDF
- ZIP, DOCX, XLSX e PPTX
- WAV e AVI
- SQLite
- MP4, MOV, HEIC e AVIF contíguos

## Limitações

- Não recupera a memória interna apagada do próprio celular sem acesso root.
- O celular precisa oferecer USB Host/OTG.
- HDs de 2,5 polegadas podem precisar de hub OTG alimentado.
- O suporte depende de o adaptador USB/SATA aceitar USB Mass Storage Bulk-Only Transport.
- Arquivos fragmentados podem ficar incompletos na recuperação por assinatura.
- Nunca selecione o mesmo dispositivo de origem como destino.

O projeto do Windows permanece separado e não é alterado por este aplicativo Android.
