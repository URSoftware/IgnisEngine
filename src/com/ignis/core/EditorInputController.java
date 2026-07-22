package com.ignis.core;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Point2D;

/**
 * Controlador de input do editor (Fase F -- decomposicao do {@link Game}): traduz
 * eventos de mouse do Canvas em selecao, arrasto de gizmo, pintura de tiles/barreiras,
 * panning e edicao de collider. Detem o estado transiente de interacao (posicoes de
 * inicio de arraste, flags de pincel, warp de mouse), que nao pertence ao Game.
 *
 * <p>O Game continua sendo o Canvas AWT onde os listeners sao registrados
 * ({@link #install()}) e a fonte do estado compartilhado (objeto selecionado,
 * ferramenta, modo de arrasto do gizmo lido pelo EditorGizmoRenderer). Mesmo pacote,
 * entao le/escreve esse estado package-private sem API publica nova.</p>
 */
final class EditorInputController {

    private final Game game;

    // Robot para arraste infinito (warp do mouse ao encostar na borda).
    private Robot robot;
    private static final int WARP_MARGIN = 10;

    // Estado de arraste do gizmo de transform.
    private int dragStartX, dragStartY;
    private double objectStartX, objectStartY;
    private double objectStartRotation;
    private int objectStartWidth, objectStartHeight;
    private int accumulatedDragX, accumulatedDragY;
    private int lastMouseX, lastMouseY;
    private boolean isWarping = false;

    // Panning (botao do meio).
    private boolean isPanning = false;
    private int panStartX, panStartY;
    private double camStartX, camStartY;
    private Runnable onPanUpdate;

    // Pintura de barreiras/tiles em andamento.
    private boolean paintingWorld = false;
    private boolean paintingTiles = false;

    // Arraste de alca do gizmo de collider.
    private int colliderHandle = -1;
    private int hoveredColliderHandle = -1;
    private boolean draggingCollider = false;
    private double colStartMinX, colStartMinY, colStartW, colStartH;

    EditorInputController(Game game) {
        this.game = game;
        try {
            robot = new Robot();
        } catch (Exception e) {
            robot = null;
        }
    }

    // Roteamento de mouse para a UI vive no Game (fonte unica, compartilhada com a
    // injecao por coordenada do MCP); aqui apenas delegamos o evento real.
    private boolean routeMouseClickToUi(MouseEvent e, boolean pressed) {
        return game.routeUiMouseClick(e, pressed);
    }

    private void routeMouseMoveToUi(MouseEvent e) {
        game.routeUiMouseMove(e);
    }

    void install() {
        game.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // UI (CanvasComponents + canvas global) tem prioridade no Play
                if (routeMouseClickToUi(e, true)) {
                    e.consume();
                    return;
                }

                // Middle mouse button for panning - handle first to avoid selection
                if (e.getButton() == MouseEvent.BUTTON2) {
                    startPanning(e.getX(), e.getY());
                    e.consume();
                    return;
                }
                // Left click for selection/manipulation
                if (e.getButton() == MouseEvent.BUTTON1) {
                    handleMousePress(e.getX(), e.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                // UI (CanvasComponents + canvas global) tem prioridade no Play
                if (routeMouseClickToUi(e, false)) {
                    e.consume();
                    return;
                }

                if (e.getButton() == MouseEvent.BUTTON2) {
                    stopPanning();
                    e.consume();
                    return;
                }
                if (e.getButton() == MouseEvent.BUTTON1) {
                    handleMouseRelease();
                }
            }
        });

        game.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                // UI (CanvasComponents + canvas global) recebe hover no Play
                routeMouseMoveToUi(e);

                // Handle panning first
                if (isPanning) {
                    handlePanning(e.getX(), e.getY());
                    return;
                }
                handleMouseDrag(e.getX(), e.getY());
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                // UI (CanvasComponents + canvas global) recebe hover no Play
                routeMouseMoveToUi(e);

                updateCursor(e.getX(), e.getY());
            }
        });
    }

    /**
     * Sets up editor panning with a callback for UI updates.
     */
    void setupEditorPanning(Runnable onUpdate) {
        this.onPanUpdate = onUpdate;
    }

    private void startPanning(int x, int y) {
        if (game.getGameState() != Game.GameState.EDITING) return;
        
        isPanning = true;
        panStartX = x;
        panStartY = y;
        Camera cam = game.getViewCamera();
        if (cam != null) {
            camStartX = cam.getX();
            camStartY = cam.getY();
        }
        game.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
    }

    private void handlePanning(int x, int y) {
        if (!isPanning || game.getGameState() != Game.GameState.EDITING) return;

        Camera cam = game.getViewCamera();
        if (cam != null) {
            double zoom = cam.getZoom();
            double dx = (x - panStartX) / zoom;
            // Invert dy because Y-axis is flipped (positive Y goes up)
            double dy = -(y - panStartY) / zoom;
            cam.setPosition(camStartX - dx, camStartY - dy);
            game.repaint();
        }
    }

    private void stopPanning() {
        isPanning = false;
        game.setCursor(Cursor.getDefaultCursor());
        if (onPanUpdate != null) {
            onPanUpdate.run();
        }
    }

    private void handleMousePress(int mouseX, int mouseY) {
        // Only allow selection and manipulation in EDITING mode
        if (game.getGameState() != Game.GameState.EDITING)
            return;

        // Ferramenta de pintura de barreiras: um clique/arraste bloqueia (ou apaga com
        // Ctrl) celulas da grade do World. Nao seleciona nem move objetos.
        if (game.getCurrentTool() == Game.ToolType.WORLD_PAINT) {
            if (game.getWorld() != null) {
                paintingWorld = true;
                if (game.worldPaintListener != null) game.worldPaintListener.onPaintStrokeStart();
                paintCellAt(mouseX, mouseY);
            }
            return;
        }

        // Ferramenta de pintura de tiles: clique/arraste pinta (ou apaga com Ctrl) a
        // celula do tilemap ativo. Nao seleciona nem move objetos.
        if (game.getCurrentTool() == Game.ToolType.TILE_PAINT) {
            if (game.getActiveTilemap() != null) {
                paintingTiles = true;
                paintTileAt(mouseX, mouseY);
            }
            return;
        }

        // Gizmo de collider (item 8b): alcas da hitbox tem precedencia sobre o gizmo
        // de transform quando o modo de edicao de collider esta ativo.
        int ch = getColliderHandleAt(mouseX, mouseY);
        if (ch != -1) {
            ColliderComponent cc = editableCollider();
            double[] b = cc != null ? cc.getWorldBounds() : null;
            if (b != null) {
                colliderHandle = ch;
                draggingCollider = true;
                colStartMinX = b[0];
                colStartMinY = b[1];
                colStartW = b[2];
                colStartH = b[3];
                dragStartX = mouseX;
                dragStartY = mouseY;
                if (game.colliderEditListener != null) {
                    game.colliderEditListener.onColliderEditStart(game.getSelectedObject(), cc);
                }
                return;
            }
        }

        // Check if clicked on gizmo first
        if (game.getSelectedObject() != null) {
            Game.GizmoDragMode mode = getGizmoHitArea(mouseX, mouseY);
            if (mode != Game.GizmoDragMode.NONE) {
                game.currentDragMode = mode;
                dragStartX = mouseX;
                dragStartY = mouseY;
                objectStartX = game.getSelectedObject().getX();
                objectStartY = game.getSelectedObject().getY();
                objectStartRotation = game.getSelectedObject().getRotation();
                objectStartWidth = game.getSelectedObject().getWidth();
                objectStartHeight = game.getSelectedObject().getHeight();
                
                // Initialize infinite drag tracking
                accumulatedDragX = 0;
                accumulatedDragY = 0;
                lastMouseX = mouseX;
                lastMouseY = mouseY;
                
                // Notificar início de transformação (para undo)
                if (game.transformListener != null) {
                    game.transformListener.onTransformStart(game.getSelectedObject(), 
                        objectStartX, objectStartY, objectStartRotation, 
                        objectStartWidth, objectStartHeight);
                }
                return;
            }
        }

        // Check if clicked on any object (cicla entre objetos sobrepostos a cada clique)
        GameObject clicked = game.getObjectAt(mouseX, mouseY, game.getSelectedObject());
        if (clicked != null) {
            game.setSelectedObject(clicked);
            // Start drag from center (move mode)
            if (game.getCurrentTool() == Game.ToolType.MOVE) {
                game.currentDragMode = Game.GizmoDragMode.CENTER;
                dragStartX = mouseX;
                dragStartY = mouseY;
                objectStartX = clicked.getX();
                objectStartY = clicked.getY();
                
                // Initialize infinite drag tracking
                accumulatedDragX = 0;
                accumulatedDragY = 0;
                lastMouseX = mouseX;
                lastMouseY = mouseY;
                
                // Notificar início de transformação (para undo)
                if (game.transformListener != null) {
                    game.transformListener.onTransformStart(clicked, 
                        objectStartX, objectStartY, clicked.getRotation(), 
                        clicked.getWidth(), clicked.getHeight());
                }
            }
        } else {
            // Clicked on empty area - deselect
            game.setSelectedObject(null);
        }
    }

    private void handleMouseRelease() {
        // Fim do traco de pintura de barreiras (WORLD_PAINT).
        if (paintingWorld) {
            paintingWorld = false;
            if (game.worldPaintListener != null) game.worldPaintListener.onPaintStrokeEnd();
            return;
        }
        // Fim do traco de pintura de tiles (TILE_PAINT).
        if (paintingTiles) {
            paintingTiles = false;
            if (game.tilePaintDirtyHook != null) game.tilePaintDirtyHook.run();
            return;
        }
        // Fim do arraste de collider (item 8b): marca o projeto sujo via o mesmo
        // listener (o transform do objeto nao mudou, entao nenhum comando de undo e
        // gerado; apenas dispara markProjectDirty no editor).
        if (draggingCollider) {
            draggingCollider = false;
            colliderHandle = -1;
            if (game.getSelectedObject() != null && game.colliderEditListener != null) {
                game.colliderEditListener.onColliderEditEnd(game.getSelectedObject(),
                        game.getSelectedObject().getComponent(ColliderComponent.class));
            } else if (game.getSelectedObject() != null && game.transformListener != null) {
                game.transformListener.onTransformEnd(game.getSelectedObject());
            }
            game.setCursor(Cursor.getDefaultCursor());
            return;
        }
        // Notificar fim de transformacao (para undo/auto-save)
        if (game.currentDragMode != Game.GizmoDragMode.NONE && game.getSelectedObject() != null && game.transformListener != null) {
            game.transformListener.onTransformEnd(game.getSelectedObject());
        }
        
        game.currentDragMode = Game.GizmoDragMode.NONE;
        game.setCursor(Cursor.getDefaultCursor());
        // Notificacao de selecao NAO e necessaria aqui: o Inspector do editor FX ja
        // sincroniza via AnimationTimer a 60fps (updateInspectorFields). Notificar
        // redundantemente enfileirava lambdas extras em Platform.runLater, contribuindo
        // para o loop de selecao infinita.
    }

    private void handleMouseDrag(int mouseX, int mouseY) {
        // Arraste do pincel de barreiras (WORLD_PAINT).
        if (paintingWorld) {
            paintCellAt(mouseX, mouseY);
            return;
        }
        // Arraste do pincel de tiles (TILE_PAINT).
        if (paintingTiles) {
            paintTileAt(mouseX, mouseY);
            return;
        }
        // Arraste de alca de collider (item 8b) — independente do gizmo de transform.
        if (draggingCollider) {
            handleColliderDrag(mouseX, mouseY);
            return;
        }
        if (game.currentDragMode == Game.GizmoDragMode.NONE || game.getSelectedObject() == null)
            return;
        if (game.getGameState() != Game.GameState.EDITING)
            return;
        
        // Skip if this is a warp event (mouse was just teleported)
        if (isWarping) {
            isWarping = false;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return;
        }
        
        // Calculate mouse delta since last position
        int mouseDeltaX = mouseX - lastMouseX;
        int mouseDeltaY = mouseY - lastMouseY;
        
        // Accumulate the drag
        accumulatedDragX += mouseDeltaX;
        accumulatedDragY += mouseDeltaY;
        
        // Update last position
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        
        // Check for edge wrapping (infinite drag)
        if (robot != null && game.isShowing()) {
            int w = game.getWidth();
            int h = game.getHeight();
            boolean needsWarp = false;
            int newX = mouseX;
            int newY = mouseY;
            
            if (mouseX <= WARP_MARGIN) {
                newX = w - WARP_MARGIN - 1;
                needsWarp = true;
            } else if (mouseX >= w - WARP_MARGIN) {
                newX = WARP_MARGIN + 1;
                needsWarp = true;
            }
            
            if (mouseY <= WARP_MARGIN) {
                newY = h - WARP_MARGIN - 1;
                needsWarp = true;
            } else if (mouseY >= h - WARP_MARGIN) {
                newY = WARP_MARGIN + 1;
                needsWarp = true;
            }
            
            if (needsWarp) {
                isWarping = true;
                lastMouseX = newX;
                lastMouseY = newY;
                // Convert component coordinates to screen coordinates
                Point screenLoc = game.getLocationOnScreen();
                robot.mouseMove(screenLoc.x + newX, screenLoc.y + newY);
            }
        }

        // Use accumulated drag for calculations
        Point2D.Double startWorld = game.screenToWorld(dragStartX, dragStartY);
        Point2D.Double accumulatedWorld = game.screenToWorld(dragStartX + accumulatedDragX, dragStartY + accumulatedDragY);
        
        double deltaX = accumulatedWorld.x - startWorld.x;
        double deltaY = accumulatedWorld.y - startWorld.y;

        switch (game.currentDragMode) {
                    case AXIS_X:
                        game.getSelectedObject().setX(objectStartX + deltaX);
                        if (game.snapToGrid) game.getSelectedObject().setX(game.snapToGrid(game.getSelectedObject().getX()));
                        break;
                    case AXIS_Y:
                        game.getSelectedObject().setY(objectStartY + deltaY);
                        if (game.snapToGrid) game.getSelectedObject().setY(game.snapToGrid(game.getSelectedObject().getY()));
                        break;
                    case CENTER:
                        game.getSelectedObject().setX(objectStartX + deltaX);
                        game.getSelectedObject().setY(objectStartY + deltaY);
                        if (game.snapToGrid) {
                            game.getSelectedObject().setX(game.snapToGrid(game.getSelectedObject().getX()));
                            game.getSelectedObject().setY(game.snapToGrid(game.getSelectedObject().getY()));
                        }
                        break;
                    case ROTATE:
                        // Calculate rotation based on accumulated angle change
                        double centerX = objectStartX + objectStartWidth / 2.0;
                        double centerY = objectStartY + objectStartHeight / 2.0;
                        double startAngle = Math.atan2(startWorld.y - centerY, startWorld.x - centerX);
                        double currentAngle = Math.atan2(accumulatedWorld.y - centerY, accumulatedWorld.x - centerX);
                        double deltaAngle = Math.toDegrees(currentAngle - startAngle);
                        game.getSelectedObject().setRotation(objectStartRotation + deltaAngle);
                        break;
                    case SCALE_X:
                        // Scale from center: adjust position to keep center fixed
                        int newWidth = Math.max(1, objectStartWidth + (int)(deltaX * 2));
                        double oldCenterX = objectStartX + objectStartWidth / 2.0;
                        game.getSelectedObject().setWidth(newWidth);
                        game.getSelectedObject().setX(oldCenterX - newWidth / 2.0);
                        if (game.snapToGrid) game.getSelectedObject().setX(game.snapToGrid(game.getSelectedObject().getX()));
                        break;
                    case SCALE_Y:
                        // Scale from center: adjust position to keep center fixed
                        // Dragging up (positive Y) increases height
                        int newHeight = Math.max(1, objectStartHeight + (int)(deltaY * 2));
                        double oldCenterY = objectStartY + objectStartHeight / 2.0;
                        game.getSelectedObject().setHeight(newHeight);
                        game.getSelectedObject().setY(oldCenterY - newHeight / 2.0);
                        if (game.snapToGrid) game.getSelectedObject().setY(game.snapToGrid(game.getSelectedObject().getY()));
                        break;
                    case SCALE_UNIFORM:
                        // Uniform scale from center (use game.getWorld() delta)
                        // Dragging right/up increases size
                        double scaleAmount = deltaX + deltaY;
                        int newUniformWidth = Math.max(1, objectStartWidth + (int)scaleAmount);
                        int newUniformHeight = Math.max(1, objectStartHeight + (int)scaleAmount);
                        double origCenterX = objectStartX + objectStartWidth / 2.0;
                        double origCenterY = objectStartY + objectStartHeight / 2.0;
                        game.getSelectedObject().setWidth(newUniformWidth);
                        game.getSelectedObject().setHeight(newUniformHeight);
                        game.getSelectedObject().setX(origCenterX - newUniformWidth / 2.0);
                        game.getSelectedObject().setY(origCenterY - newUniformHeight / 2.0);
                        if (game.snapToGrid) {
                            game.getSelectedObject().setX(game.snapToGrid(game.getSelectedObject().getX()));
                            game.getSelectedObject().setY(game.snapToGrid(game.getSelectedObject().getY()));
                        }
                        break;
                    default:
                        break;
                }

        // Hierarquia (Fase C): o objeto arrastado mantem a posicao (recaptura o
        // offset se tiver pai) e seus descendentes acompanham ao vivo no editor.
        game.syncHierarchyAfterEditorMove(game.getSelectedObject());

        // Don't notify listeners during drag - prevents Inspector from updating
        // constantly
        // Visual changes are already visible, listeners will be notified on mouse
        // release
    }

    private void updateCursor(int mouseX, int mouseY) {
        if (game.getGameState() != Game.GameState.EDITING) {
            game.setCursor(Cursor.getDefaultCursor());
            return;
        }

        // Hover nas alcas do gizmo de collider (item 8b): cursor de redimensionamento
        // direcional, com precedencia sobre o gizmo de transform.
        int colHandle = getColliderHandleAt(mouseX, mouseY);
        if (colHandle != hoveredColliderHandle) {
            hoveredColliderHandle = colHandle;
            game.repaint();
        }
        if (colHandle != -1) {
            game.setCursor(Cursor.getPredefinedCursor(colliderHandleCursor(colHandle)));
            return;
        }

        if (game.getSelectedObject() != null) {
            Game.GizmoDragMode mode = getGizmoHitArea(mouseX, mouseY);
            if (mode != game.hoveredGizmoMode) {
                game.hoveredGizmoMode = mode;
                game.repaint();
            }
            switch (mode) {
                case AXIS_X:
                case SCALE_X:
                    game.setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                    return;
                case AXIS_Y:
                case SCALE_Y:
                    game.setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
                    return;
                case CENTER:
                case SCALE_UNIFORM:
                    game.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    return;
                case ROTATE:
                    game.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                    return;
                default:
                    break;
            }
        } else {
            if (game.hoveredGizmoMode != Game.GizmoDragMode.NONE) {
                game.hoveredGizmoMode = Game.GizmoDragMode.NONE;
                game.repaint();
            }
        }

        // Verificar se está sobre algum objeto
        if (game.getObjectAt(mouseX, mouseY) != null) {
            game.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        } else {
            game.setCursor(Cursor.getDefaultCursor());
        }
    }

    private Game.GizmoDragMode getGizmoHitArea(int screenX, int screenY) {
        if (game.getSelectedObject() == null)
            return Game.GizmoDragMode.NONE;

        // Convert screen mouse position to game.getWorld() coordinates
        Point2D.Double worldPos = game.screenToWorld(screenX, screenY);
        double mouseX = worldPos.x;
        double mouseY = worldPos.y;

        int centerX = (int) game.getSelectedObject().getX() + game.getSelectedObject().getWidth() / 2;
        int centerY = (int) game.getSelectedObject().getY() + game.getSelectedObject().getHeight() / 2;
        
        // Get scaled gizmo dimensions
        int gizmoSize = game.getScaledGizmoSize();
        int hitArea = game.getScaledGizmoHitArea();
        int rotateRadius = game.getScaledRotateGizmoRadius();
        int scaledHitTolerance = (int)(25 / (game.getViewCamera() != null ? game.getViewCamera().getZoom() : 1.0));

        switch (game.getCurrentTool()) {
            case MOVE:
                // Check center first (precedence)
                if (mouseX >= centerX - hitArea && mouseX <= centerX + hitArea &&
                        mouseY >= centerY - hitArea && mouseY <= centerY + hitArea) {
                    return Game.GizmoDragMode.CENTER;
                }
                // Check X axis (arrow to right)
                if (mouseX >= centerX && mouseX <= centerX + gizmoSize &&
                        mouseY >= centerY - hitArea && mouseY <= centerY + hitArea) {
                    return Game.GizmoDragMode.AXIS_X;
                }
                // Check Y axis (arrow up = positive Y direction)
                if (mouseX >= centerX - hitArea && mouseX <= centerX + hitArea &&
                        mouseY >= centerY && mouseY <= centerY + gizmoSize) {
                    return Game.GizmoDragMode.AXIS_Y;
                }
                break;

            case ROTATE:
                // Check if on rotation circle
                double dist = Math.sqrt(Math.pow(mouseX - centerX, 2) + Math.pow(mouseY - centerY, 2));
                if (dist >= rotateRadius - scaledHitTolerance && dist <= rotateRadius + scaledHitTolerance) {
                    return Game.GizmoDragMode.ROTATE;
                }
                break;

            case SCALE:
                int squareSize = (int)(20 / (game.getViewCamera() != null ? game.getViewCamera().getZoom() : 1.0));
                // Check center square first (uniform scale precedence)
                if (mouseX >= centerX - hitArea && mouseX <= centerX + hitArea &&
                        mouseY >= centerY - hitArea && mouseY <= centerY + hitArea) {
                    return Game.GizmoDragMode.SCALE_UNIFORM;
                }
                // Check X axis square end (scale X)
                if (mouseX >= centerX + gizmoSize - squareSize && mouseX <= centerX + gizmoSize + squareSize &&
                        mouseY >= centerY - squareSize && mouseY <= centerY + squareSize) {
                    return Game.GizmoDragMode.SCALE_X;
                }
                // Check Y axis square end (scale Y - positive Y direction)
                if (mouseX >= centerX - squareSize && mouseX <= centerX + squareSize &&
                        mouseY >= centerY + gizmoSize - squareSize && mouseY <= centerY + gizmoSize + squareSize) {
                    return Game.GizmoDragMode.SCALE_Y;
                }
                break;
        }

        return Game.GizmoDragMode.NONE;
    }

    /**
     * ColliderComponent do objeto selecionado elegivel para edicao por gizmo, ou
     * {@code null}. Requer modo de edicao, {@code game.isShowColliders()} ligado e um
     * ColliderComponent anexado.
     */
    private ColliderComponent editableCollider() {
        if (game.getGameState() != Game.GameState.EDITING || !game.isShowColliders() || game.getSelectedObject() == null
                || game.getSelectedObject() instanceof Camera) {
            return null;
        }
        return game.getSelectedObject().getComponent(ColliderComponent.class);
    }

    /** Bloqueia (ou apaga, se game.worldPaintErase) a celula do World sob o ponto de tela. */
    private void paintCellAt(int screenX, int screenY) {
        if (game.getWorld() == null) return;
        Point2D.Double wp = game.screenToWorld(screenX, screenY);
        int col = game.getWorld().cellCol(wp.x);
        int row = game.getWorld().cellRow(wp.y);
        if (game.worldPaintErase) {
            game.getWorld().unblockCell(col, row);
        } else {
            game.getWorld().blockCell(col, row);
        }
        game.repaint();
    }

    /** Pinta (ou apaga, se game.tilePaintErase) o tile do tilemap ativo sob o ponto de tela. */
    private void paintTileAt(int screenX, int screenY) {
        if (game.getActiveTilemap() == null) return;
        Point2D.Double wp = game.screenToWorld(screenX, screenY);
        int col = game.getActiveTilemap().cellColAtWorld(wp.x);
        int row = game.getActiveTilemap().cellRowAtWorld(wp.y);
        int value = game.tilePaintErase ? TilemapObject.EMPTY : game.activeTileIndex;
        game.getActiveTilemap().setTile(game.activeTileLayer, col, row, value);
        game.repaint();
    }

    /** Cursor de redimensionamento AWT correspondente a alca de collider (0..7). */
    private int colliderHandleCursor(int handle) {
        switch (handle) {
            case 0: return Cursor.NW_RESIZE_CURSOR;
            case 1: return Cursor.N_RESIZE_CURSOR;
            case 2: return Cursor.NE_RESIZE_CURSOR;
            case 3: return Cursor.E_RESIZE_CURSOR;
            case 4: return Cursor.SE_RESIZE_CURSOR;
            case 5: return Cursor.S_RESIZE_CURSOR;
            case 6: return Cursor.SW_RESIZE_CURSOR;
            case 7: return Cursor.W_RESIZE_CURSOR;
            default: return Cursor.DEFAULT_CURSOR;
        }
    }

    /**
     * Indice (0..7) da alca de collider sob o ponto de tela, ou -1. So retorna algo
     * quando {@link #editableCollider()} nao e nulo.
     */
    private int getColliderHandleAt(int screenX, int screenY) {
        ColliderComponent cc = editableCollider();
        if (cc == null) return -1;
        double[] b = cc.getWorldBounds();
        if (b == null) return -1;
        Point2D.Double wp = game.screenToWorld(screenX, screenY);
        double tol = 7.0 * game.editorWorldPerPixel();
        for (int i = 0; i < 8; i++) {
            double[] p = game.colliderHandlePoint(b, i);
            if (Math.abs(wp.x - p[0]) <= tol && Math.abs(wp.y - p[1]) <= tol) {
                return i;
            }
        }
        return -1;
    }

    /** Aplica o arraste de uma alca de collider, redimensionando a hitbox em mundo. */
    private void handleColliderDrag(int mouseX, int mouseY) {
        ColliderComponent cc = editableCollider();
        if (cc == null) {
            draggingCollider = false;
            colliderHandle = -1;
            return;
        }
        Point2D.Double startW = game.screenToWorld(dragStartX, dragStartY);
        Point2D.Double curW = game.screenToWorld(mouseX, mouseY);
        double dx = curW.x - startW.x;
        double dy = curW.y - startW.y;

        double minX = colStartMinX, minY = colStartMinY;
        double maxX = colStartMinX + colStartW, maxY = colStartMinY + colStartH;

        boolean top = (colliderHandle == 0 || colliderHandle == 1 || colliderHandle == 2);
        boolean bottom = (colliderHandle == 4 || colliderHandle == 5 || colliderHandle == 6);
        boolean left = (colliderHandle == 0 || colliderHandle == 6 || colliderHandle == 7);
        boolean right = (colliderHandle == 2 || colliderHandle == 3 || colliderHandle == 4);

        if (top) minY += dy;
        if (bottom) maxY += dy;
        if (left) minX += dx;
        if (right) maxX += dx;

        // Normaliza (permite arrastar uma borda para alem da oposta sem inverter).
        double newMinX = Math.min(minX, maxX);
        double newMinY = Math.min(minY, maxY);
        double newW = Math.abs(maxX - minX);
        double newH = Math.abs(maxY - minY);
        cc.resizeToWorldBounds(newMinX, newMinY, newW, newH);
    }

    void cancelDrag() {
        if (game.currentDragMode != Game.GizmoDragMode.NONE && game.getSelectedObject() != null && game.transformListener != null) {
            game.transformListener.onTransformEnd(game.getSelectedObject());
        }
        game.currentDragMode = Game.GizmoDragMode.NONE;
        draggingCollider = false;
        colliderHandle = -1;
        isPanning = false;
        game.setCursor(Cursor.getDefaultCursor());
    }
}
