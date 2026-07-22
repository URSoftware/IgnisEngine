package com.ignis.dialog;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Domínio puro do diálogo data-driven (P1 fatia 2b): validação de grafo (start,
 * referências, alcançabilidade, ciclos) e round-trip JSON — sem editor nem JavaFX.
 */
class DialogTest {

    private static Dialog.Node node(String id, String text, String next) {
        return new Dialog.Node(id, "Rimuru", "", text, next);
    }

    @Test
    void dialogoLinearValidoPassa() {
        Dialog d = new Dialog("intro", "a");
        d.putNode(node("a", "Olá.", "b"));
        d.putNode(node("b", "Tchau.", ""));   // terminal
        assertTrue(d.validate(null).isEmpty(), d.validate(null).toString());
    }

    @Test
    void startAusenteEhReportado() {
        Dialog d = new Dialog("intro", "naoexiste");
        d.putNode(node("a", "Oi", ""));
        assertTrue(d.validate(null).stream().anyMatch(i -> i.contains("Nó inicial ausente")));
    }

    @Test
    void referenciaQuebradaEhReportada() {
        Dialog d = new Dialog("intro", "a");
        d.putNode(node("a", "Oi", "fantasma"));
        assertTrue(d.validate(null).stream().anyMatch(i -> i.contains("Referência quebrada")));
    }

    @Test
    void noInalcancavelEhReportado() {
        Dialog d = new Dialog("intro", "a");
        d.putNode(node("a", "Oi", ""));    // terminal, nao referencia b
        d.putNode(node("b", "Sozinho", ""));
        assertTrue(d.validate(null).stream().anyMatch(i -> i.contains("inalcançável")));
    }

    @Test
    void cicloSemSaidaViraAviso() {
        Dialog d = new Dialog("loop", "a");
        d.putNode(node("a", "1", "b"));
        d.putNode(node("b", "2", "a"));    // a<->b sem terminal
        assertTrue(d.validate(null).stream().anyMatch(i -> i.contains("terminal")));
    }

    @Test
    void escolhasComFlagCondicaoValidamEAlcancam() {
        Dialog d = new Dialog("q", "start");
        Dialog.Node start = new Dialog.Node("start", "NPC", "", "Aceita?", "");
        start.addChoice(new Dialog.Choice("Sim", "fim", "aceitou", ""));
        start.addChoice(new Dialog.Choice("Não", "fim", "", ""));
        d.putNode(start);
        d.putNode(node("fim", "Ok.", ""));
        assertTrue(d.validate(null).isEmpty(), d.validate(null).toString());
    }

    @Test
    void condicaoNuncaSetadaViraAviso() {
        Dialog d = new Dialog("q", "start");
        Dialog.Node start = new Dialog.Node("start", "NPC", "", "Entra?", "");
        start.addChoice(new Dialog.Choice("Entrar", "fim", "", "tem_chave")); // ninguem seta tem_chave
        d.putNode(start);
        d.putNode(node("fim", "Ok.", ""));
        assertTrue(d.validate(null).stream().anyMatch(i -> i.contains("tem_chave")));
    }

    @Test
    void retratoAusenteEhReportadoQuandoPredicadoFornece() {
        Dialog d = new Dialog("intro", "a");
        d.putNode(new Dialog.Node("a", "R", "assets/faces/x.png", "Oi", ""));
        assertTrue(d.validate(rel -> false).stream().anyMatch(i -> i.contains("Retrato ausente")));
        assertFalse(d.validate(rel -> true).stream().anyMatch(i -> i.contains("Retrato ausente")));
    }

    @Test
    void jsonRoundTripPreservaGrafo() {
        Dialog d = new Dialog("intro", "start");
        Dialog.Node start = new Dialog.Node("start", "NPC", "assets/faces/npc.png", "Escolha:", "");
        start.addChoice(new Dialog.Choice("A", "na", "flagA", ""));
        start.addChoice(new Dialog.Choice("B", "nb", "", "precisa"));
        d.putNode(start);
        d.putNode(node("na", "Foi A", ""));
        d.putNode(node("nb", "Foi B", ""));

        Dialog back = Dialog.fromJSON(new JSONObject(d.toJSON().toString()));
        assertEquals("start", back.getStart());
        assertEquals(3, back.getNodes().size());
        Dialog.Node s = back.getNode("start");
        assertEquals("assets/faces/npc.png", s.portrait);
        List<Dialog.Choice> choices = s.getChoices();
        assertEquals(2, choices.size());
        assertEquals("flagA", choices.get(0).setFlag);
        assertEquals("precisa", choices.get(1).condition);
        assertEquals("na", choices.get(0).next);
    }
}
