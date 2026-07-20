# Roadmap bagual — TPoll Guard / TPoll Scanner

## Direção do produto

O app deve evoluir de um scanner técnico para um produto de usuário comum:

> Antivírus, limpeza inteligente e privacidade para Android.

O foco não é bancada técnica. O foco é cliente final: pessoa comum que quer saber se o celular está seguro, pesado, cheio de arquivos inúteis ou com apps abusando de permissões.

## Estado atual identificado

O app já possui uma base boa e não deve ser refeito do zero.

### Já existe

- Login Google com Firebase/FirebaseUI.
- Dashboard principal.
- Scan de apps instalados.
- Classificação de risco por score.
- `AppAnalyzer` com regras em `rules.json` e base `virus_db.json`.
- Serviço de scan em background.
- Proteção/Shield com `ShieldService`.
- Histórico de scans.
- Tela de permissões.
- Tela de saúde do dispositivo.
- Tela de quarentena.
- Verificação de atualização via `update.json`.
- Landing page no GitHub Pages.

### Ponto crítico atual

O login obrigatório antes do usuário ver valor pode derrubar conversão. Para app de usuário comum, o ideal é:

- permitir entrar sem conta;
- pedir login só para histórico, premium, backup ou sincronização;
- manter Google como recurso, não barreira inicial.

## Posicionamento comercial

Nome possível:

- TPoll Guard
- TPoll Scanner
- TPoll Safe Cleaner

Slogan recomendado:

> Antivírus, limpeza e privacidade em um só app.

Promessa segura:

> Analise apps suspeitos, veja permissões perigosas, encontre arquivos duplicados e libere espaço no celular.

Evitar prometer:

- remove todos os vírus;
- proteção 100% garantida;
- limpa tudo automaticamente;
- desbloqueia celular;
- mexe em arquivos do sistema.

## Pilares do app

### 1. Antivírus / Segurança

Usar o que já existe e melhorar a comunicação.

Funcionalidades:

- apps suspeitos;
- apps fora da Play Store;
- apps com permissões perigosas;
- apps com acessibilidade;
- apps com sobreposição de tela;
- APKs baixados e antigos;
- apps recém-instalados;
- explicação simples para cada risco.

Linguagem para usuário:

- Seguro
- Atenção
- Suspeito
- Alto risco

### 2. Limpeza inteligente

Novo módulo.

Funcionalidades:

- arquivos grandes;
- fotos duplicadas prováveis;
- vídeos grandes;
- vídeos duplicados prováveis;
- documentos duplicados;
- APKs antigos;
- downloads antigos;
- prints antigos;
- arquivos do WhatsApp;
- estimativa de espaço recuperável.

### 3. Privacidade

Aproveitar a tela de permissões e transformar em linguagem comercial.

Funcionalidades:

- quem acessa a câmera;
- quem acessa o microfone;
- quem sabe sua localização;
- quem acessa contatos;
- quem usa acessibilidade;
- quem pode aparecer sobre outros apps.

### 4. Saúde / Desempenho

Já existe tela de saúde. Evoluir para usuário comum:

- bateria;
- armazenamento;
- memória;
- rede;
- sensores;
- recomendações simples.

### 5. Anti-golpes

Fase futura, muito vendável.

Funcionalidades:

- analisar mensagem suspeita;
- analisar link suspeito;
- detectar urgência, prêmio, bloqueio, cobrança falsa, link estranho;
- dicas de segurança.

## Roadmap por versões

### 1.6.0 — Base comercial

- Login opcional: permitir continuar sem conta.
- Nova aba Limpeza.
- Scanner de arquivos grandes.
- Duplicados prováveis por nome/tamanho.
- APKs baixados e antigos.
- Mídias do WhatsApp.
- Estimativa de espaço recuperável.
- Home com comunicação mais comercial.

### 1.7.0 — Duplicados avançados

- Hash real de arquivos, em segundo plano.
- Agrupamento de fotos/vídeos/documentos duplicados.
- Seleção segura.
- Lixeira temporária.
- Revisão antes de apagar.

### 1.8.0 — Fotos parecidas

- Hash visual/perceptual de imagens.
- Fotos parecidas.
- Prints parecidos.
- Fotos borradas/escuras.
- Sugestão de melhor foto.

### 1.9.0 — WhatsApp Cleaner

- Fotos do WhatsApp.
- Vídeos do WhatsApp.
- Áudios antigos.
- Documentos antigos.
- Stickers.
- Backups antigos quando acessíveis.

### 2.0.0 — Produto premium

- Plano Premium.
- Histórico de limpezas.
- Histórico de scans.
- Detector de golpes.
- Alertas de privacidade.
- Relatório completo.
- Sem anúncios.

## Monetização sugerida

### Grátis

- scan antivírus básico;
- permissões perigosas;
- arquivos grandes;
- resultado simples;
- limpeza manual limitada.

### Premium

- fotos duplicadas;
- fotos parecidas;
- WhatsApp Cleaner;
- detector de golpes;
- histórico;
- alertas automáticos;
- relatório completo;
- sem anúncios.

Preço inicial sugerido:

- R$ 19,90 vitalício promocional;
- depois R$ 39,90/ano ou R$ 9,90/mês.

## Regra de ouro de UX

Sempre mostrar antes de apagar.

Fluxo correto:

1. Analisar.
2. Mostrar achados.
3. Explicar em linguagem simples.
4. Usuário seleciona.
5. Confirmar.
6. Só então apagar/mover.

## Implementação inicial desta branch

Esta branch inicia a fase 1.6.0 com:

- aba Limpeza;
- scanner de mídias/arquivos via MediaStore;
- detecção de arquivos grandes;
- candidatos a duplicados por metadados;
- mídia do WhatsApp;
- prints;
- downloads/APKs;
- login opcional para reduzir abandono.
