# Bibliotecas privadas de projeto (`project/libs/`)

## O problema

O `ScriptManager` compila os scripts de `project/scripts/` **um arquivo por vez**
(`compileScript(File)`), com um classpath fixo (`java.class.path` da engine +
`target/classes`, se existir). Isso é suficiente enquanto cada script só usa
classes do próprio `com.ignis.core` — mas se um projeto tiver lógica própria
espalhada em mais de um arquivo `.java` que se referenciam entre si (ex: um
script que usa uma classe de domínio definida em outro arquivo da mesma pasta),
a compilação falha com `cannot find symbol`, porque o compilador nunca vê os
outros `.java` da pasta nem qualquer `.class` pré-compilado deles.

## A solução: `project/libs/*.jar`

Qualquer projeto pode ter uma pasta `libs/` na raiz (irmã de `scripts/`,
`assets/`, `data/`). Todo `.jar` solto ali entra automaticamente:

1. No **classpath de compilação** de cada script (`ScriptManager.compileScript`).
2. No **`URLClassLoader` de runtime** usado para carregar as classes compiladas
   (`ScriptManager.reloadClassLoader`), tanto no editor quanto no jogo exportado
   pelo Builder (o `Builder` já copia a pasta `project/` inteira, `libs/`
   incluso — nenhuma mudança adicional foi necessária lá).

Ou seja: qualquer classe Java compilada num `.jar` e colocada em
`project/libs/` fica disponível para os scripts do projeto como se fosse uma
dependência normal — sem a engine precisar saber que ela existe, e sem nenhum
outro projeto feito na engine ser afetado (cada `ScriptManager` só olha para o
`libs/` do seu próprio `projectFolder`).

**Importante:** isso é diferente de adicionar uma dependência ao `pom.xml` raiz
do IgnisEngine-main. Uma dependência no `pom.xml` da engine seria carregada por
**todo** projeto feito nela, mesmo os que não precisam — `project/libs/` é
por-projeto e opcional.

## Como usar no seu projeto

1. Escreva sua lógica reutilizável (regras de jogo, estruturas de dados,
   parsers) como um módulo Maven comum, independente da engine — pode viver em
   `projects/<SeuJogo>/domain-lib/` (convenção, não obrigatório).
2. Empacote: `mvnw -f projects/<SeuJogo>/domain-lib/pom.xml package` gera o jar
   em `domain-lib/target/`.
3. Copie o jar gerado para `projects/<SeuJogo>/project/libs/`.
4. Nos seus scripts (`project/scripts/*.java`), importe normalmente as classes
   do jar — o `ScriptManager` resolve tanto na compilação quanto na execução.

## Exemplo real

O projeto `RimuruSurvivors` (`projects/RimuruSurvivors/`) usa exatamente esse
mecanismo: a lógica de progressão/evolução do personagem
(`com.rimurusurvivors.domain.*`, portada de um mod C# sem nenhuma dependência
de motor) vive em `domain-lib/`, é testada com JUnit isolada de qualquer
engine, e o jar resultante fica em `project/libs/rimuru-survivors-domain.jar`.
`ProgressionLoader.java` (um script comum do projeto) importa
`com.rimurusurvivors.domain.WeaponProgression` normalmente.

## Limite conhecido

`discoverLibJars()` lista arquivos `.jar` soltos direto em `libs/` — não
resolve dependências transitivas de dentro desses jars (se sua biblioteca
privada depender de uma terceira lib, ela também precisa estar em `libs/`,
ou empacote um jar "fat/uber" com `maven-shade-plugin`/`maven-assembly-plugin`
no seu `domain-lib`).
