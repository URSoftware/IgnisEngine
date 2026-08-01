import com.ignis.core.AssetResolver;
import com.ignis.core.CanvasComponent;
import com.ignis.core.GameObject;
import com.ignis.core.IgnisScript;
import com.ignis.core.ui.UIButton;
import com.ignis.core.ui.UICanvas;
import com.ignis.core.ui.UIComponent;
import com.ignis.core.ui.UILabel;
import com.rimurusurvivors.domain.CampaignState;
import com.rimurusurvivors.domain.ProjectDefinition;
import com.rimurusurvivors.domain.TownCommand;
import com.rimurusurvivors.domain.TownProjectState;
import com.rimurusurvivors.domain.TownProjects;
import com.rimurusurvivors.domain.TownResourceBundle;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adaptador de runtime do Conselho de Tempest. Traduz os sinais semanticos dos widgets
 * persistentes em TownCommand e projeta CampaignState nos rotulos. Nao cria objeto de
 * mundo, nao escreve no filesystem e nao guarda regra de dominio.
 */
public final class TownDirector extends IgnisScript {

    private static final String COPY_PATH = "data/town-council-copy.json";
    private static final String MANIFEST_PATH = "data/goblin-village-scene-visuals.json";
    private static final String SIGNAL_PREFIX = "signal:";

    private static final String SIGNAL_STATE_LOADED = "TENSURA_CAMPAIGN_STATE_LOADED";
    private static final String SIGNAL_STATE_CHANGED = "TENSURA_CAMPAIGN_STATE_CHANGED";
    private static final String SIGNAL_TOWN_COMMAND = "TENSURA_TOWN_COMMAND";
    private static final String SIGNAL_TOWN_COMMAND_REJECTED = "TENSURA_TOWN_COMMAND_REJECTED";
    private static final String SIGNAL_REPORT_OPEN = "TENSURA_TOWN_REPORT_OPEN";
    private static final String SIGNAL_REPORT_NEXT = "TENSURA_TOWN_REPORT_NEXT";
    private static final String SIGNAL_REPORT_CLOSE = "TENSURA_TOWN_REPORT_CLOSE";
    private static final String SIGNAL_COUNCIL_OPEN = "TENSURA_COUNCIL_OPEN";
    private static final String SIGNAL_COUNCIL_PICK_SHELTER = "TENSURA_COUNCIL_PICK_SHELTER";
    private static final String SIGNAL_COUNCIL_PICK_WORKSHOP = "TENSURA_COUNCIL_PICK_WORKSHOP";
    private static final String SIGNAL_COUNCIL_PICK_PALISADE = "TENSURA_COUNCIL_PICK_PALISADE";
    private static final String SIGNAL_COUNCIL_CONFIRM = "TENSURA_COUNCIL_CONFIRM";
    private static final String SIGNAL_COUNCIL_CLOSE = "TENSURA_COUNCIL_CLOSE";

    private enum Mode { HIDDEN, REPORT, COUNCIL }

    private JSONObject copy;
    private JSONObject widgetNames;
    private CanvasComponent canvasComponent;
    private final Map<String, UIComponent> widgets = new LinkedHashMap<>();
    private final List<String> reportPages = new ArrayList<>();
    private final List<String> priorityOrder = List.of(
            TownProjects.SHELTER, TownProjects.WORKSHOP, TownProjects.PALISADE);

    private CampaignState campaignState;
    private Mode mode = Mode.HIDDEN;
    private int reportPage;
    private String lastRejection;
    private boolean disabled;

    @Override
    public void start() {
        // start() e reentrante: cada execucao reconstroi o mapa de widgets e substitui os
        // onClick por setOnClick, que troca o handler em vez de acumular. onSceneSignal e
        // desfeito pelo onDetach do IgnisScript, entao recarregar o script nao duplica
        // ouvinte nem cria um segundo estado de painel.
        disabled = false;
        widgets.clear();
        reportPages.clear();
        mode = Mode.HIDDEN;
        reportPage = 0;
        lastRejection = null;

        if (!loadContracts() || !bindCanvas() || !bindWidgets()) {
            return;
        }

        onSceneSignal(SIGNAL_STATE_LOADED, this::onCampaignState);
        onSceneSignal(SIGNAL_STATE_CHANGED, this::onCampaignState);
        onSceneSignal(SIGNAL_TOWN_COMMAND_REJECTED, this::onCommandRejected);
        onSceneSignal(SIGNAL_REPORT_OPEN, payload -> openReport());
        onSceneSignal(SIGNAL_REPORT_NEXT, payload -> advanceReport());
        onSceneSignal(SIGNAL_REPORT_CLOSE, payload -> closePanel());
        onSceneSignal(SIGNAL_COUNCIL_OPEN, payload -> openCouncil());
        onSceneSignal(SIGNAL_COUNCIL_PICK_SHELTER, payload -> pick(TownProjects.SHELTER));
        onSceneSignal(SIGNAL_COUNCIL_PICK_WORKSHOP, payload -> pick(TownProjects.WORKSHOP));
        onSceneSignal(SIGNAL_COUNCIL_PICK_PALISADE, payload -> pick(TownProjects.PALISADE));
        onSceneSignal(SIGNAL_COUNCIL_CONFIRM, payload -> confirmPriority());
        onSceneSignal(SIGNAL_COUNCIL_CLOSE, payload -> closePanel());

        applyMode();
        log("TownDirector pronto com " + widgets.size() + " widgets do TownCouncilCanvas.");
    }

    private boolean loadContracts() {
        try {
            copy = readJson(COPY_PATH);
            widgetNames = readJson(MANIFEST_PATH)
                    .getJSONObject("persistentUi")
                    .getJSONObject("widgets");
        } catch (RuntimeException exception) {
            return fail("Contrato de apresentacao ausente ou invalido: " + exception.getMessage());
        }
        JSONArray pages = copy.getJSONObject("report").getJSONArray("pages");
        if (pages.isEmpty()) {
            return fail("Relatorio de retorno sem paginas em " + COPY_PATH + ".");
        }
        for (int index = 0; index < pages.length(); index++) {
            reportPages.add(pages.getString(index));
        }
        for (String projectId : priorityOrder) {
            requireProjectCopy(projectId);
        }
        return !disabled;
    }

    private void requireProjectCopy(String projectId) {
        JSONObject projects = copy.optJSONObject("projects");
        JSONObject entry = projects == null ? null : projects.optJSONObject(projectId);
        if (entry == null
                || entry.optString("name", "").isBlank()
                || entry.optString("summary", "").isBlank()) {
            fail("Texto obrigatorio ausente para " + projectId + " em " + COPY_PATH + ".");
        }
    }

    private boolean bindCanvas() {
        GameObject canvasObject = findObject(copy.getString("canvasObjectName"));
        if (canvasObject == null) {
            return fail("Objeto " + copy.getString("canvasObjectName")
                    + " nao existe na cena; o Codex precisa persistir o canvas.");
        }
        canvasComponent = canvasObject.getComponent(CanvasComponent.class);
        if (canvasComponent == null || canvasComponent.getCanvas() == null) {
            return fail("CanvasComponent ausente em " + canvasObject.getName() + ".");
        }
        return true;
    }

    private boolean bindWidgets() {
        UICanvas canvas = canvasComponent.getCanvas();
        for (String role : widgetNames.keySet()) {
            String widgetName = widgetNames.getString(role);
            UIComponent widget = canvas.findByName(widgetName);
            if (widget == null) {
                return fail("Widget " + widgetName + " (" + role
                        + ") nao existe no TownCouncilCanvas; nao vou criar UI volatil.");
            }
            widgets.put(role, widget);
            if (widget instanceof UIButton button) {
                String signal = signalOf(button, widgetName);
                if (signal == null) {
                    return false;
                }
                // O motor guarda actionData mas nao o interpreta; quem converte em sinal
                // e este runtime. setOnClick substitui o handler anterior.
                button.setOnClick(() -> sceneDispatcher.enqueue(signal, null));
            }
        }
        return true;
    }

    private String signalOf(UIButton button, String widgetName) {
        String actionData = button.getActionData();
        if (actionData == null || !actionData.startsWith(SIGNAL_PREFIX)
                || actionData.length() == SIGNAL_PREFIX.length()) {
            fail("Botao " + widgetName + " sem actionData 'signal:<NOME>' no canvas persistente.");
            return null;
        }
        return actionData.substring(SIGNAL_PREFIX.length());
    }

    private void onCampaignState(Object payload) {
        if (disabled || !(payload instanceof CampaignState state)) {
            return;
        }
        campaignState = state;
        lastRejection = null;
        if (mode != Mode.HIDDEN) {
            applyMode();
        }
    }

    private void onCommandRejected(Object payload) {
        if (disabled || !(payload instanceof String reason)) {
            return;
        }
        lastRejection = reason;
        applyMode();
    }

    private void openReport() {
        if (disabled || campaignState == null) {
            return;
        }
        // Um clique, um comando: o relatorio e idempotente pelo id no dominio.
        reportPage = 0;
        mode = Mode.REPORT;
        applyMode();
    }

    private void advanceReport() {
        if (disabled || mode != Mode.REPORT) {
            return;
        }
        reportPage = Math.min(reportPage + 1, reportPages.size() - 1);
        applyMode();
    }

    private void openCouncil() {
        if (disabled || campaignState == null) {
            return;
        }
        mode = Mode.COUNCIL;
        applyMode();
    }

    private void closePanel() {
        if (disabled) {
            return;
        }
        // Fechar nao guarda escolha nenhuma: o estado vive so no dominio, entao reabrir
        // projeta de novo a partir do CampaignState e nunca um estado paralelo local.
        mode = Mode.HIDDEN;
        reportPage = 0;
        lastRejection = null;
        applyMode();
    }

    private void pick(String projectId) {
        if (disabled || mode != Mode.COUNCIL || campaignState == null) {
            return;
        }
        sceneDispatcher.enqueue(
                SIGNAL_TOWN_COMMAND, new TownCommand.PrioritizeProject(projectId));
    }

    private void confirmPriority() {
        if (disabled || mode != Mode.COUNCIL || campaignState == null) {
            return;
        }
        if (campaignState.townState().prioritizedProjectId() == null) {
            lastRejection = council("noPriorityConfirm");
            applyMode();
            return;
        }
        sceneDispatcher.enqueue(SIGNAL_TOWN_COMMAND, new TownCommand.StartPrioritizedProject());
    }

    private void applyMode() {
        canvasComponent.setCanvasVisible(mode != Mode.HIDDEN);
        setVisible("overlay", mode != Mode.HIDDEN);
        setVisible("reportNext", mode == Mode.REPORT);
        setVisible("reportClose", mode == Mode.REPORT);
        setVisible("openCouncil", mode == Mode.REPORT && isLastReportPage());
        setVisible("shelterTab", mode == Mode.COUNCIL);
        setVisible("workshopTab", mode == Mode.COUNCIL);
        setVisible("palisadeTab", mode == Mode.COUNCIL);
        setVisible("confirm", mode == Mode.COUNCIL);
        setVisible("councilClose", mode == Mode.COUNCIL);
        setVisible("preview", mode == Mode.COUNCIL);
        setVisible("resources", mode != Mode.HIDDEN);
        setVisible("requirements", mode == Mode.COUNCIL);
        setVisible("benefit", mode == Mode.COUNCIL);
        setVisible("responsible", mode == Mode.COUNCIL);

        if (mode == Mode.REPORT) {
            renderReport();
        } else if (mode == Mode.COUNCIL) {
            renderCouncil();
        }
    }

    private void renderReport() {
        JSONObject report = copy.getJSONObject("report");
        setText("title", report.getString("title"));
        setText("page", String.format(
                council("pageFormat"), reportPage + 1, reportPages.size()));
        setText("body", reportPages.get(reportPage));
        setText("resources", resourcesLine());
        setButtonText("reportNext", report.getString("nextLabel"));
        setButtonText("openCouncil", report.getString("openCouncilLabel"));
        setButtonText("reportClose", report.getString("closeLabel"));
    }

    private void renderCouncil() {
        setText("title", council("title"));
        setButtonText("confirm", council("confirmLabel"));
        setButtonText("councilClose", council("closeLabel"));
        for (String projectId : priorityOrder) {
            setButtonText(tabRoleOf(projectId), projectName(projectId));
        }
        setText("resources", resourcesLine());

        String priority = campaignState.townState().prioritizedProjectId();
        if (priority == null) {
            setText("page", council("intro"));
            setText("body", lastRejection == null
                    ? council("noPriorityBody")
                    : String.format(message("rejectedFormat"), lastRejection));
            setText("requirements", "");
            setText("benefit", "");
            setText("responsible", "");
            return;
        }

        ProjectDefinition definition = TownProjects.requireDefinition(priority);
        TownProjectState state = campaignState.townState().projects().get(priority);
        setText("page", String.format(
                council("pageFormat"),
                priorityOrder.indexOf(priority) + 1,
                priorityOrder.size()));
        setText("body", projectSummary(priority) + "  [" + statusLabel(state) + "]");
        setText("requirements", requirementsLine(definition));
        setText("benefit", String.format(
                council("benefitFormat"), labelsOf("benefits", definition.benefitSignals())));
        setText("responsible", String.format(
                council("responsibleFormat"),
                labelOf("specialists", definition.responsibleSpecialistId())));
        if (lastRejection != null) {
            setText("body", String.format(message("rejectedFormat"), lastRejection));
        }
    }

    private String requirementsLine(ProjectDefinition definition) {
        List<String> missing = new ArrayList<>();
        if (!campaignState.townState().resources().covers(definition.requiredResources())) {
            missing.add(String.format(
                    council("costFormat"), bundleLine(definition.requiredResources())));
        }
        if (!campaignState.townState().specialists()
                .contains(definition.responsibleSpecialistId())) {
            missing.add(labelOf("specialists", definition.responsibleSpecialistId()));
        }
        if (!campaignState.narrativeFlags().contains(definition.requiredMilestoneId())) {
            missing.add(labelOf("milestones", definition.requiredMilestoneId()));
        }
        return missing.isEmpty()
                ? council("requirementsMetLabel")
                : String.format(council("requirementsMissingFormat"), String.join(", ", missing));
    }

    private String resourcesLine() {
        return bundleLine(campaignState.townState().resources());
    }

    private String bundleLine(TownResourceBundle bundle) {
        JSONObject names = copy.getJSONObject("resources");
        List<String> parts = new ArrayList<>();
        appendResource(parts, names, "wood", bundle.wood());
        appendResource(parts, names, "stone", bundle.stone());
        appendResource(parts, names, "food", bundle.food());
        appendResource(parts, names, "cloth", bundle.cloth());
        appendResource(parts, names, "metal", bundle.metal());
        appendResource(parts, names, "magicules", bundle.magicules());
        return parts.isEmpty() ? "-" : String.join("   ", parts);
    }

    private void appendResource(
            List<String> parts, JSONObject names, String key, int amount) {
        if (amount <= 0) {
            return;
        }
        parts.add(String.format(council("resourceFormat"), requireText(names, key), amount));
    }

    private String statusLabel(TownProjectState state) {
        return requireText(copy.getJSONObject("projectStatus"), state.status().name());
    }

    private String projectName(String projectId) {
        return copy.getJSONObject("projects").getJSONObject(projectId).getString("name");
    }

    private String projectSummary(String projectId) {
        return copy.getJSONObject("projects").getJSONObject(projectId).getString("summary");
    }

    private String tabRoleOf(String projectId) {
        if (TownProjects.SHELTER.equals(projectId)) {
            return "shelterTab";
        }
        return TownProjects.WORKSHOP.equals(projectId) ? "workshopTab" : "palisadeTab";
    }

    private String labelsOf(String section, java.util.Set<String> ids) {
        List<String> labels = new ArrayList<>();
        ids.forEach(id -> labels.add(labelOf(section, id)));
        return String.join(", ", labels);
    }

    private String labelOf(String section, String id) {
        return requireText(copy.getJSONObject(section), id);
    }

    private String council(String key) {
        return requireText(copy.getJSONObject("council"), key);
    }

    private String message(String key) {
        return requireText(copy.getJSONObject("messages"), key);
    }

    /** Texto ausente e erro de contrato, nunca string vazia silenciosa. */
    private String requireText(JSONObject section, String key) {
        String value = section.optString(key, "");
        if (value.isBlank()) {
            fail("Texto obrigatorio '" + key + "' ausente em " + COPY_PATH + ".");
            return key;
        }
        return value;
    }

    private void setVisible(String role, boolean visible) {
        UIComponent widget = widgets.get(role);
        if (widget != null) {
            widget.setVisible(visible);
        }
    }

    private void setText(String role, String text) {
        UIComponent widget = widgets.get(role);
        if (widget instanceof UILabel label) {
            label.setText(text);
        }
    }

    private void setButtonText(String role, String text) {
        UIComponent widget = widgets.get(role);
        if (widget instanceof UIButton button) {
            button.setText(text);
        }
    }

    private boolean isLastReportPage() {
        return reportPage == reportPages.size() - 1;
    }

    /**
     * Desliga o diretor com mensagem unica. Lancar excecao aqui viraria spam no GameLoop,
     * que nao morre em erro de script.
     */
    private boolean fail(String reason) {
        if (!disabled) {
            disabled = true;
            log("TownDirector desativado: " + reason);
        }
        return false;
    }

    private JSONObject readJson(String relativePath) {
        File file = AssetResolver.resolve(relativePath);
        try {
            return new JSONObject(Files.readString(file.toPath()));
        } catch (IOException exception) {
            throw new IllegalStateException("Falha ao carregar " + relativePath, exception);
        }
    }
}
