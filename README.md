# mSDrop

Pede confirmação antes de dropar um item. O jogador aperta Q, recebe o aviso, e só dropa de verdade se apertar Q de novo dentro do tempo configurado. Evita perder item por tecla errada.

## Compatibilidade

Spigot, Paper, Purpur e Folia, da 1.20 até a 26.x, com o mesmo jar.

Convive com outros plugins:

- O aviso roda em prioridade `HIGHEST` e ignora drops já cancelados. Plugins de proteção, login (AuthMe e similares), regiões (WorldGuard) e kits decidem primeiro, e o mSDrop só pede confirmação do que sobrou.
- Não usa o agendador do Bukkit, então funciona no Folia, e não empacota nenhuma biblioteca, então não conflita com versões de outros plugins.
- As permissões (`sd.use`, `sd.admin`) funcionam com LuckPerms, PermissionsEx e qualquer outro gerenciador. Se algum alias (`/sd`, `/drop`, `/safedrop`) já pertencer a outro plugin, o outro plugin vence e `/msdrop` ou `/msdrop:sd` continuam funcionando.
- Jogadores mortos, com inventário cheio ou sem `sd.use` nunca são afetados.

O plugin é compilado em Java 17 e roda em qualquer versão acima disso. Quem exige Java 21 (1.20.5+) ou Java 25 (26.1+) é o próprio servidor.

## Instalação

1. Baixe o jar em [Releases](../../releases), ou compile (veja abaixo), e coloque em `plugins`.
2. Reinicie o servidor.
3. Dê a permissão `sd.use` para quem for usar. Por padrão só OP tem.

## Comandos

| Comando | Permissão | O que faz |
|---|---|---|
| `/sd on` | `sd.use` | Liga a confirmação para você |
| `/sd off` | `sd.use` | Desliga a confirmação para você |
| `/drop` | `sd.use` | Alterna entre ligado e desligado |
| `/sd reload` | `sd.admin` | Recarrega o config.yml |
| `/sd update` | `sd.admin` | Baixa a versão mais nova |
| `/sd reportbug` | `sd.admin` | Mostra a mensagem de reportar bug |

`/sd` também responde como `/msdrop` e `/safedrop`.

## Configuração

O arquivo fica em `plugins/mSDrop/config.yml`.

- `safe-drop.enabled`: estado inicial de quem entra pela primeira vez.
- `safe-drop.seconds`: por quanto tempo o segundo drop é aceito depois do aviso.
- `safe-drop.text-message.enabled` e `safe-drop.actionbar-message.enabled`: aviso no chat e na action bar.
- `messages.*`: todas as mensagens. Aceita `&` e cor hex no formato `<#RRGGBB>`.

O estado de cada jogador fica em `saves.yml`. Para usar MySQL, coloque `MySQL: true` e preencha `database-settings`; a tabela `msdrop_users` é criada sozinha. Se a conexão falhar, o plugin volta para o `saves.yml` e avisa no console.

Quem está com o inventário cheio não recebe confirmação, o drop passa direto.

Qualquer alteração (`/sd on`, `/sd off`, `/drop`) é gravada na hora, então reiniciar ou dar reload no servidor não perde a escolha de ninguém. O MySQL tem timeout de 5 segundos para conectar, e se o banco falhar ao carregar um jogador o plugin usa o padrão sem sobrescrever o que estava salvo.

## Atualização

Ao ligar e a cada 6 horas o plugin olha a última release do repositório configurado no GitHub. Se houver versão nova, avisa no console e para quem tem `sd.admin` ao entrar. O download só acontece quando você pede com `/sd update`, ou se ligar `auto-download`. O jar baixado vai para `plugins/update` e o servidor troca o arquivo no próximo restart. O jar que está rodando nunca é substituído com o servidor ligado. Antes de baixar, o jar atual é copiado para `plugins/mSDrop/backups`.

```yaml
update:
  check: true
  auto-download: false
  notify-admins: true
  repository: ''
  interval-hours: 6
```

O campo `repository` (`dono/repositório`) vem vazio numa compilação própria, e o plugin não consulta nada até você preencher.

Como o atualizador instala código no servidor, ele é rígido:

- só fala com `api.github.com` e baixa de `github.com/<repositório>/releases/download/...`; os redirecionamentos são conferidos um a um e só podem ir para `github.com` ou `*.githubusercontent.com`, sempre em https;
- só aceita o arquivo `mSDrop-<versão>.jar` da última release, que não seja rascunho nem pré-release;
- **exige o SHA-256** que o GitHub calcula para cada arquivo da release. Sem ele, o download é recusado;
- o jar precisa ter `plugin.yml` com `name: mSDrop` e `main: dev.msdrop.mSDrop`;
- limite de 32 MB por download e backup do jar atual antes de trocar.

Se não quiser nada instalado pelo plugin, use `check: false`.

## Segurança

- Nenhuma biblioteca é empacotada no jar, então não há dependência embutida para desatualizar.
- `database-settings` é validado antes de montar a conexão (host, porta e nome do banco com caracteres inválidos são recusados), e o driver roda com `allowLoadLocalInfile`, `allowUrlInLocalInfile` e `allowMultiQueries` desligados.
- Use no MySQL um usuário só para este plugin, com permissão `CREATE`, `SELECT`, `INSERT` e `UPDATE` apenas no banco do mSDrop. A senha fica em texto puro no `config.yml`, então deixe esse arquivo legível só para o usuário que roda o servidor.
- Todas as consultas SQL usam `PreparedStatement`.
- O atualizador não baixa nada sozinho por padrão (veja acima).

## Compilar

Precisa de JDK 17 ou mais novo e de [Maven](https://maven.apache.org/).

```
mvn package
```

O jar sai em `target/mSDrop-<versão>.jar`.

Para que o jar compilado já venha com o atualizador apontando para um repositório:

```
mvn package -Dmsdrop.repository=dono/repositório
```
