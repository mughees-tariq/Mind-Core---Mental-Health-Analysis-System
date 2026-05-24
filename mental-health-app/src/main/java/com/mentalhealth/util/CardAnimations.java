package com.mentalhealth.util;

import java.util.LinkedHashSet;
import java.util.Set;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

//card animations used across all screens.
 // Provides consistent hover-lift and staggered entrance effects.
public final class CardAnimations {

    private CardAnimations() {}

    //  HOVER LIFT 
    // Subtle translateY shift + shadow deepening.

    private static final double HOVER_Y = -4;
    private static final Duration HOVER_DURATION = Duration.millis(200);

    private static final DropShadow BASE_SHADOW =
            new DropShadow(10, 0, 4, Color.rgb(0, 0, 0, 0.10));
    private static final DropShadow HOVER_SHADOW =
            new DropShadow(20, 0, 8, Color.rgb(0, 0, 0, 0.18));

    // Applies hover-lift: translateY -4 + deeper shadow on enter, restores on exit
    public static void applyHoverLift(Node node) {
        node.setEffect(new DropShadow(10, 0, 4, Color.rgb(0, 0, 0, 0.10)));

        node.setOnMouseEntered(e -> {
            Timeline in = new Timeline(new KeyFrame(HOVER_DURATION,
                    new KeyValue(node.translateYProperty(), HOVER_Y, Interpolator.EASE_OUT),
                    new KeyValue(node.effectProperty(),
                            new DropShadow(20, 0, 8, Color.rgb(0, 0, 0, 0.18)),
                            Interpolator.EASE_OUT)));
            in.play();
        });

        node.setOnMouseExited(e -> {
            Timeline out = new Timeline(new KeyFrame(HOVER_DURATION,
                    new KeyValue(node.translateYProperty(), 0, Interpolator.EASE_OUT),
                    new KeyValue(node.effectProperty(),
                            new DropShadow(10, 0, 4, Color.rgb(0, 0, 0, 0.10)),
                            Interpolator.EASE_OUT)));
            out.play();
        });
    }

    //  ENTRANCE ANIMATION 
    // Fade-in + slide-up with staggered delay per card index.

    private static final double ENTRANCE_SLIDE = 20;
    private static final Duration ENTRANCE_DURATION = Duration.millis(400);
    private static final Duration ENTRANCE_STAGGER = Duration.millis(80);

    // Plays fade + slide-up entrance animation; index sets stagger delay (0-based)
    public static void playEntrance(Node node, int index) {
        node.setOpacity(0);
        node.setTranslateY(ENTRANCE_SLIDE);

        FadeTransition fade = new FadeTransition(ENTRANCE_DURATION, node);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition slide = new TranslateTransition(ENTRANCE_DURATION, node);
        slide.setFromY(ENTRANCE_SLIDE);
        slide.setToY(0);
        slide.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition anim = new ParallelTransition(fade, slide);
        anim.setDelay(ENTRANCE_STAGGER.multiply(index));
        anim.play();
    }

    //  BATCH HELPER 

    // Animates all nodes matching CSS selectors inside root (entrance + hover-lift); call after scene graph built
    public static void animateAll(Pane root, String... selectors) {
        Platform.runLater(() -> {
            // Deduplicate: a node with multiple classes (e.g. "card card-success card-shadow")
            // would match several selectors — only animate it once.
            Set<Node> seen = new LinkedHashSet<>();
            for (String selector : selectors) {
                seen.addAll(root.lookupAll(selector));
            }
            int i = 0;
            for (Node node : seen) {
                playEntrance(node, i);
                applyHoverLift(node);
                i++;
            }
        });
    }
}
