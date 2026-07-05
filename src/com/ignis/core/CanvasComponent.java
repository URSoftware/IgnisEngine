package com.ignis.core;

import com.ignis.core.ui.UIButton;
import com.ignis.core.ui.UICanvas;
import com.ignis.core.ui.UIImage;
import com.ignis.core.ui.UILabel;
import com.ignis.core.ui.UIPanel;
import com.ignis.core.ui.UIProgressBar;
import com.ignis.core.ui.UITextField;
import org.json.JSONObject;

import java.awt.Graphics2D;
import java.awt.event.MouseEvent;

/**
 * CanvasComponent — interface de usuário POR CIMA da camada do jogo, anexável
 * a qualquer GameObject (arquitetura EC).
 *
 * <p>Cada instância possui sua própria árvore {@link UICanvas} (overlay em
 * espaço de tela, imune à câmera), com todo o conjunto de widgets do motor
 * (botão, label, painel, imagem, barra de progresso, campo de texto, slider,
 * checkbox), âncoras e modos de escala do {@link UICanvas.ScaleMode}. O
 * {@link Game} renderiza os canvases de todas as entidades após o mundo —
 * inclusive no viewport do editor (preview de design) — e roteia mouse para
 * eles com prioridade durante o Play.</p>
 *
 * <p><b>Persistência:</b> a árvore de UI inteira é serializada dentro da cena
 * ({@code properties.canvas}, via {@code UICanvas.toJSON}/{@code UIFactory}),
 * então a interface montada no projeto abre pronta — diferente do canvas
 * global de runtime ({@link Game#getUICanvas()}), que é volátil e limpo a cada
 * Stop.</p>
 *
 * <p><b>Uso em scripts:</b></p>
 * <pre>
 * CanvasComponent hud = gameObject.getComponent(CanvasComponent.class);
 * hud.addLabel("HP", 20, 20);
 * hud.addButton("Pausar", 20, 60, () -> game.stopWorld());
 * </pre>
 */
public class CanvasComponent extends Component {

    /** Ordem de desenho entre CanvasComponents (maior = na frente). */
    @Serialize
    private int sortingOrder = 0;

    /** Se falso, o canvas não é desenhado nem recebe input (ex.: menus ocultos). */
    @Serialize
    private boolean canvasVisible = true;

    private UICanvas canvas = new UICanvas("Canvas");

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    @Override
    public void update(float deltaTime) {
        if (canvasVisible) {
            canvas.update();
        }
    }

    // ------------------------------------------------------------------
    // Render e input (chamados pelo Game)
    // ------------------------------------------------------------------

    /** Renderiza a árvore de UI em espaço de tela (chamado pelo Game após o mundo). */
    public void render(Graphics2D g, int screenWidth, int screenHeight) {
        if (!canvasVisible || !canvas.isVisible()) return;
        canvas.updateScreenSize(screenWidth, screenHeight);
        canvas.renderAll(g);
    }

    /** Roteia clique do mouse para a UI. @return true se a UI consumiu o evento. */
    public boolean processMouseClick(MouseEvent e, boolean pressed) {
        return canvasVisible && canvas.processMouseClick(e, pressed);
    }

    /** Roteia movimento do mouse para a UI (hover). @return true se consumiu. */
    public boolean processMouseMove(MouseEvent e) {
        return canvasVisible && canvas.processMouseMove(e);
    }

    // ------------------------------------------------------------------
    // Serialização: escalares via @Serialize + árvore de UI embutida
    // ------------------------------------------------------------------

    @Override
    public JSONObject saveProperties() {
        JSONObject props = super.saveProperties();
        props.put("canvas", canvas.toJSON());
        return props;
    }

    @Override
    public void loadProperties(JSONObject props, ScriptSerializationHelper.GameObjectResolver resolver) {
        super.loadProperties(props, resolver);
        JSONObject canvasJson = props.optJSONObject("canvas");
        if (canvasJson != null) {
            UICanvas loaded = UICanvas.fromJSON(canvasJson);
            if (loaded != null) {
                this.canvas = loaded;
            }
        }
    }

    // ------------------------------------------------------------------
    // API de conveniência (espelha o UICanvas)
    // ------------------------------------------------------------------

    /** Acesso direto à árvore de UI (widgets avançados, âncoras, escala). */
    public UICanvas getCanvas() { return canvas; }

    public UIButton addButton(String text, double x, double y, Runnable onClick) {
        return canvas.addButton(text, x, y, onClick);
    }

    public UILabel addLabel(String text, double x, double y) {
        return canvas.addLabel(text, x, y);
    }

    public UIPanel addPanel(double x, double y, double width, double height) {
        return canvas.addPanel(x, y, width, height);
    }

    public UIImage addImage(String imagePath, double x, double y) {
        return canvas.addImage(imagePath, x, y);
    }

    public UIProgressBar addProgressBar(double x, double y, double width) {
        return canvas.addProgressBar(x, y, width);
    }

    public UITextField addTextField(double x, double y, double width) {
        return canvas.addTextField(x, y, width);
    }

    /** Remove todos os widgets do canvas. */
    public void clear() { canvas.clearChildren(); }

    public int getSortingOrder() { return sortingOrder; }
    public void setSortingOrder(int sortingOrder) { this.sortingOrder = sortingOrder; }

    public boolean isCanvasVisible() { return canvasVisible; }
    public void setCanvasVisible(boolean visible) { this.canvasVisible = visible; }
}
