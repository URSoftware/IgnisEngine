package com.ignis.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistencia da coordenacao em {@code <projeto>/.ignis/coordination.json}: mural,
 * claims e tarefas devem sobreviver a recriacao do {@link McpCoordination} (que e o
 * que acontece quando o editor reinicia — nova JVM, singleton zerado). Como o
 * singleton e estatico, o teste simula o "reinicio" ligando OUTRO projeto e depois
 * voltando ao primeiro: {@code bindProject} recarrega o arquivo do disco.
 */
class McpCoordinationPersistenceTest {

    @TempDir
    File projectA;

    @TempDir
    File projectB;

    @Test
    void muralClaimsETarefasSobrevivemAoReinicio() {
        McpCoordination coord = McpCoordination.get();

        // Sessao 1: liga no projeto A e produz estado.
        coord.bindProject(projectA);
        coord.sendMessage("Ana", "", "cuidando do Hero");
        coord.claim("Ana", "objeto:Hero");
        coord.createTask("Pintar tilemap", "camada base");

        File store = new File(new File(projectA, ".ignis"), "coordination.json");
        assertTrue(store.isFile(), "deveria ter gravado coordination.json");

        // "Reinicio": liga em outro projeto (zera a memoria) e volta ao A (recarrega).
        coord.bindProject(projectB);
        assertFalse(coord.listClaims().contains("objeto:Hero"),
                "projeto B nao deve enxergar o claim do projeto A");

        coord.bindProject(projectA);

        assertTrue(coord.listClaims().contains("objeto:Hero"),
                "claim deve voltar do disco: " + coord.listClaims());
        assertTrue(coord.readMessages("Ana", 0).contains("cuidando do Hero"),
                "mural deve voltar do disco");
        assertTrue(coord.listTasks().contains("Pintar tilemap"),
                "tarefa deve voltar do disco: " + coord.listTasks());

        coord.release("Ana", "objeto:Hero"); // nao vaza para outros testes
    }

    @Test
    void arquivoCorrompidoCarregaComoCenaLimpaSemEstadoParcial() throws Exception {
        McpCoordination coord = McpCoordination.get();

        File ignis = new File(projectB, ".ignis");
        ignis.mkdirs();
        // JSON truncado no meio de um array de mensagens: parse falha depois de o
        // parser ja ter visto conteudo — o load nao pode comitar nada parcial.
        Files.write(new File(ignis, "coordination.json").toPath(),
                "{\"seqCounter\":9,\"messages\":[{\"seq\":1,\"from\":\"X\",\"text\":\"oi\"},{\"seq\":2,"
                        .getBytes(StandardCharsets.UTF_8));

        // Liga em A (limpo) e depois em B (corrompido): deve resultar em cena limpa.
        coord.bindProject(projectA);
        coord.bindProject(projectB);

        assertTrue(coord.readMessages("X", 0).startsWith("(sem novas mensagens)"),
                "arquivo corrompido nao pode carregar mensagens parciais: " + coord.readMessages("X", 0));
        assertTrue(coord.listClaims().contains("nenhum recurso"), coord.listClaims());
        assertTrue(coord.listTasks().contains("nenhuma tarefa"), coord.listTasks());
    }
}
