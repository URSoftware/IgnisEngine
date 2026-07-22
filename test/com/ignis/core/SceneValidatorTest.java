package com.ignis.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Linter de cena compartilhado entre o MCP (validate_scene) e o menu "Validar Cena…"
 * do editor. Trava a regra de detecção para os dois consumidores não divergirem.
 */
class SceneValidatorTest {

    @TempDir
    File projectFolder;

    private GameObject obj(String name, double x, double y) {
        GameObject go = new Square();
        go.setName(name);
        go.setX(x);
        go.setY(y);
        return go;
    }

    @Test
    void cenaVaziaNaoTemProblemas() {
        assertTrue(SceneValidator.validate(List.of(), null, projectFolder, null).isEmpty());
    }

    @Test
    void cenaLimpaPassa() {
        List<String> issues = SceneValidator.validate(
                List.of(obj("Hero", 0, 0), obj("Slime", 10, 10)), null, projectFolder, null);
        assertTrue(issues.isEmpty(), issues.toString());
    }

    @Test
    void nomeDuplicadoEhReportado() {
        List<String> issues = SceneValidator.validate(
                List.of(obj("Hero", 0, 0), obj("Hero", 5, 5)), null, projectFolder, null);
        assertTrue(issues.stream().anyMatch(i -> i.contains("Nome duplicado")), issues.toString());
    }

    @Test
    void spriteAusenteEComCaminhoInvalidoSaoReportados() {
        GameObject ausente = obj("A", 0, 0);
        ausente.setSpritePath("assets/sprites/naoexiste.png");
        GameObject traversal = obj("B", 0, 0);
        traversal.setSpritePath("../fora.png");

        List<String> issues = SceneValidator.validate(List.of(ausente, traversal), null, projectFolder, null);
        assertTrue(issues.stream().anyMatch(i -> i.contains("Sprite ausente")), issues.toString());
        assertTrue(issues.stream().anyMatch(i -> i.contains("Caminho de sprite invalido")), issues.toString());
    }

    @Test
    void scriptAusenteEhReportadoQuandoListaFornecida() {
        GameObject go = obj("Hero", 0, 0);
        go.setScriptNames(List.of("PlayerController"));
        List<String> issues = SceneValidator.validate(List.of(go), null, projectFolder, List.of("OutroScript"));
        assertTrue(issues.stream().anyMatch(i -> i.contains("Script ausente")), issues.toString());
    }

    @Test
    void objetoForaDosLimitesDoMundoEhReportado() {
        World world = new World();
        world.setBounds(0, 0, 100, 100);
        GameObject fora = obj("Longe", 500, 500);
        List<String> issues = SceneValidator.validate(List.of(fora), world, projectFolder, null);
        assertTrue(issues.stream().anyMatch(i -> i.contains("Fora do mundo")), issues.toString());
    }
}
